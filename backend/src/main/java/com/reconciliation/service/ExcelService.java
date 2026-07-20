package com.reconciliation.service;

import com.reconciliation.model.ReconRecord;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Excel/CSV 文件解析服务
 */
@Service
public class ExcelService {

    private static final Logger log = LoggerFactory.getLogger(ExcelService.class);

    /**
     * 解析上传的文件（Excel 或 CSV）
     */
    public ParseResult parse(MultipartFile file) throws Exception {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0) {
            throw new IllegalArgumentException("文件没有扩展名，请上传 .xlsx / .xls / .csv 文件");
        }

        String ext = fileName.substring(dotIndex).toLowerCase();
        log.info("开始解析文件: {} (大小: {} bytes, 类型: {})", fileName, file.getSize(), ext);

        byte[] bytes = file.getBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("文件内容为空，请重新上传");
        }

        try {
            return switch (ext) {
                case ".xlsx", ".xls" -> parseExcel(bytes, ext);
                case ".csv" -> parseCsv(bytes);
                default -> throw new IllegalArgumentException(
                    "不支持的文件格式: " + ext + "，请上传 .xlsx / .xls / .csv 文件");
            };
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件解析失败: {}", fileName, e);
            throw new IllegalArgumentException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 Excel 文件（用 WorkbookFactory 自动识别 .xls / .xlsx）
     */
    private ParseResult parseExcel(byte[] bytes, String ext) throws Exception {
        try (InputStream is = new ByteArrayInputStream(bytes)) {
            Workbook workbook;
            try {
                workbook = WorkbookFactory.create(is);
            } catch (Exception e) {
                // .xls 文件可能损坏或格式异常，尝试用 HSSFWorkbook 直接打开
                if (".xls".equals(ext)) {
                    log.warn("WorkbookFactory 创建失败，尝试 HSSFWorkbook: {}", e.getMessage());
                    try (InputStream is2 = new ByteArrayInputStream(bytes)) {
                        workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(is2);
                    }
                } else {
                    throw e;
                }
            }

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 0) {
                workbook.close();
                throw new IllegalArgumentException("Excel 文件为空，至少需要一行表头");
            }

            // 读取表头
            Row headerRow = sheet.getRow(0);
            if (headerRow == null || headerRow.getLastCellNum() < 0) {
                workbook.close();
                throw new IllegalArgumentException("Excel 文件第一行不能为空（应为表头）");
            }

            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                Cell cell = headerRow.getCell(i);
                headers.add(cell == null ? "列" + (i + 1) : getCellStringValue(cell));
            }
            log.info("表头: {}", headers);

            // 读取数据行
            List<ReconRecord> records = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                ReconRecord record = buildRecord(i - 1, headers, row);
                if (record.getAmount() != null) {
                    records.add(record);
                }
            }

            workbook.close();
            log.info("解析完成: {} 行表头, {} 条有效数据", headers.size(), records.size());
            return new ParseResult(headers, records);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 解析异常 (格式: {}): {}", ext, e.getMessage(), e);
            throw new IllegalArgumentException("Excel 文件解析失败: " + e.getMessage()
                    + "。请确认文件未被加密或损坏，建议另存为 .xlsx 格式后重试", e);
        }
    }

    /**
     * 解析 CSV 文件（兼容 BOM 头）
     */
    private ParseResult parseCsv(byte[] bytes) throws Exception {
        // 去除 BOM
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }

        List<String> headers = new ArrayList<>();
        List<ReconRecord> records = new ArrayList<>();

        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) {
            throw new IllegalArgumentException("CSV 文件为空");
        }

        // 解析表头
        String[] headerParts = parseCsvLine(lines[0]);
        headers.addAll(Arrays.asList(headerParts));
        log.info("CSV 表头: {}", headers);

        // 解析数据
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = parseCsvLine(line);
            Map<String, String> rawData = new LinkedHashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                rawData.put(headers.get(j), j < parts.length ? parts[j].trim() : "");
            }

            ReconRecord record = ReconRecord.builder()
                    .index(i - 1)
                    .date(extractDate(rawData))
                    .amount(extractAmount(rawData))
                    .description(extractDescription(rawData))
                    .counterparty(extractCounterparty(rawData))
                    .rawData(rawData)
                    .build();

            if (record.getAmount() != null) {
                records.add(record);
            }
        }

        log.info("CSV 解析完成: {} 条数据", records.size());
        return new ParseResult(headers, records);
    }

    /**
     * CSV 行分割（支持引号包裹的字段）
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private ReconRecord buildRecord(int index, List<String> headers, Row row) {
        Map<String, String> rawData = new LinkedHashMap<>();
        for (int j = 0; j < headers.size(); j++) {
            Cell cell = row.getCell(j);
            rawData.put(headers.get(j), cell == null ? "" : getCellStringValue(cell));
        }

        return ReconRecord.builder()
                .index(index)
                .date(extractDate(rawData))
                .amount(extractAmount(rawData))
                .description(extractDescription(rawData))
                .counterparty(extractCounterparty(rawData))
                .rawData(rawData)
                .build();
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !getCellStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String extractDate(Map<String, String> rawData) {
        String[] dateKeys = {"日期", "交易日期", "date", "时间", "交易时间", "记账日期"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), dateKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        for (String val : rawData.values()) {
            if (val != null && (val.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")
                    || val.matches("\\d{1,2}[-/]\\d{1,2}[-/]\\d{4}.*"))) {
                return val;
            }
        }
        return "";
    }

    private BigDecimal extractAmount(Map<String, String> rawData) {
        String[] amountKeys = {"金额", "amount", "交易金额", "收入金额", "支出金额", "发生额", "借方", "贷方"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), amountKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                String clean = entry.getValue().replace(",", "")
                        .replace("¥", "").replace("$", "").replace("元", "").trim();
                try { return new BigDecimal(clean); } catch (NumberFormatException ignored) {}
            }
        }
        for (String val : rawData.values()) {
            if (val != null && val.matches("^-?[\\d,\\.]+$")) {
                try {
                    return new BigDecimal(val.replace(",", "").trim());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String extractDescription(Map<String, String> rawData) {
        String[] descKeys = {"描述", "摘要", "备注", "description", "说明", "交易摘要", "用途", "交易类型", "类型"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), descKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return rawData.values().stream()
                .filter(v -> v != null && !v.isBlank() && !v.matches("^-?[\\d,\\.\\-\\s]+$"))
                .findFirst()
                .orElse("");
    }

    private String extractCounterparty(Map<String, String> rawData) {
        String[] partyKeys = {"对方", "交易对方", "商户", "收款方", "付款方", "对方账户", "counterparty", "merchant"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), partyKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return "";
    }

    private boolean containsAny(String text, String[] keywords) {
        String lower = text.toLowerCase();
        for (String kw : keywords) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    private String getCellStringValue(Cell cell) {
        CellType type = cell.getCellType();
        if (type == CellType.STRING) return cell.getStringCellValue().trim();
        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return new SimpleDateFormat("yyyy-MM-dd").format(cell.getDateCellValue());
            }
            double val = cell.getNumericCellValue();
            if (val == Math.floor(val) && !Double.isInfinite(val)) {
                return String.valueOf((long) val);
            }
            return BigDecimal.valueOf(val).stripTrailingZeros().toPlainString();
        }
        if (type == CellType.BOOLEAN) return String.valueOf(cell.getBooleanCellValue());
        if (type == CellType.FORMULA) {
            try {
                return cell.getStringCellValue().trim();
            } catch (Exception e) {
                try {
                    return BigDecimal.valueOf(cell.getNumericCellValue())
                            .stripTrailingZeros().toPlainString();
                } catch (Exception e2) {
                    return "";
                }
            }
        }
        return "";
    }

    public record ParseResult(List<String> headers, List<ReconRecord> records) {}
}
