package com.reconciliation.service;

import com.reconciliation.dto.ReconRequest;
import com.reconciliation.model.ReconRecord;
import com.reconciliation.model.ReconResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 对账核心服务 - 支持多列灵活匹配
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    @Value("${reconciliation.amount-tolerance:0.01}")
    private BigDecimal defaultAmountTolerance;

    @Value("${reconciliation.date-tolerance-days:3}")
    private int defaultDateToleranceDays;

    /**
     * 新版：多列灵活匹配
     */
    public ReconResult reconcileWithColumns(List<ReconRecord> sourceA, List<ReconRecord> sourceB,
                                             List<ReconRequest.ColumnMatch> matchColumns, String matchMode) {
        log.info("=== 多列对账开始: sourceA={}条, sourceB={}条, matchMode={}, columns={} ===",
                sourceA.size(), sourceB.size(), matchMode, matchColumns != null ? matchColumns.size() : 0);

        // 打印每条记录的 rawData keys（仅前3条作为诊断）
        logRawDataKeys("sourceA", sourceA, 3);
        logRawDataKeys("sourceB", sourceB, 3);

        // 打印匹配列配置 & 前几条记录对应列的值
        if (matchColumns != null) {
            for (ReconRequest.ColumnMatch cm : matchColumns) {
                log.info("匹配列配置: source=[{}] target=[{}] type=[{}]",
                        cm.getSourceColumn(), cm.getTargetColumn(), cm.getMatchType());
                // 打印前3条匹配列的值
                for (int i = 0; i < Math.min(3, sourceA.size()); i++) {
                    String val = getColumnValue(sourceA.get(i), cm.getSourceColumn());
                    log.info("  sourceA[{}] col=[{}] val=[{}]", i, cm.getSourceColumn(), val);
                }
                for (int i = 0; i < Math.min(3, sourceB.size()); i++) {
                    String val = getColumnValue(sourceB.get(i), cm.getTargetColumn());
                    log.info("  sourceB[{}] col=[{}] val=[{}]", i, cm.getTargetColumn(), val);
                }
            }
        }

        List<ReconRecord> unmatchedA = new ArrayList<>(sourceA);
        List<ReconRecord> unmatchedB = new ArrayList<>(sourceB);
        List<ReconResult.MatchedPair> matchedPairs = new ArrayList<>();

        boolean requireAll = !"ANY".equalsIgnoreCase(matchMode); // 默认ALL

        Iterator<ReconRecord> iterA = unmatchedA.iterator();
        while (iterA.hasNext()) {
            ReconRecord recordA = iterA.next();

            ReconRecord bestMatch = null;
            int bestMatchCount = 0;
            List<String> bestDiffNotes = new ArrayList<>();

            for (ReconRecord recordB : unmatchedB) {
                List<String> diffNotes = new ArrayList<>();
                int matchCount = 0;

                for (ReconRequest.ColumnMatch colMatch : matchColumns) {
                    String valA = getColumnValue(recordA, colMatch.getSourceColumn());
                    String valB = getColumnValue(recordB, colMatch.getTargetColumn());

                    boolean matched = checkMatch(valA, valB, colMatch, diffNotes);
                    if (matched) {
                        matchCount++;
                    }
                }

                int totalColumns = matchColumns.size();
                boolean isMatch;
                if (requireAll) {
                    isMatch = (matchCount == totalColumns) && totalColumns > 0;
                } else {
                    isMatch = (matchCount >= 1);
                }

                if (isMatch && matchCount > bestMatchCount) {
                    bestMatchCount = matchCount;
                    bestMatch = recordB;
                    bestDiffNotes = diffNotes;
                }
            }

            if (bestMatch != null) {
                ReconResult.MatchedPair pair = ReconResult.MatchedPair.builder()
                        .source(recordA)
                        .target(bestMatch)
                        .hasDifference(!bestDiffNotes.isEmpty())
                        .differenceNote(bestDiffNotes.isEmpty() ? null : String.join("; ", bestDiffNotes))
                        .build();
                matchedPairs.add(pair);
                unmatchedB.remove(bestMatch);
                iterA.remove();
            }
        }

        log.info("=== 对账完成: 匹配{}对, 仅A {}条, 仅B {}条 ===",
                matchedPairs.size(), unmatchedA.size(), unmatchedB.size());

        return buildResult(sourceA, sourceB, matchedPairs, unmatchedA, unmatchedB);
    }

    private void logRawDataKeys(String label, List<ReconRecord> records, int limit) {
        if (records == null || records.isEmpty()) {
            log.info("  {} 记录数为空", label);
            return;
        }
        for (int i = 0; i < Math.min(limit, records.size()); i++) {
            ReconRecord r = records.get(i);
            log.info("  {}[{}] rawData keys: {}", label, i,
                    r.getRawData() != null ? r.getRawData().keySet() : "null");
        }
    }

    /**
     * 旧版：固定金额+日期匹配（向后兼容）
     */
    public ReconResult reconcile(List<ReconRecord> sourceA, List<ReconRecord> sourceB,
                                  BigDecimal amountTolerance, Integer dateToleranceDays) {
        if (amountTolerance == null) amountTolerance = defaultAmountTolerance;
        if (dateToleranceDays == null) dateToleranceDays = defaultDateToleranceDays;

        List<ReconRecord> unmatchedA = new ArrayList<>(sourceA);
        List<ReconRecord> unmatchedB = new ArrayList<>(sourceB);
        List<ReconResult.MatchedPair> matchedPairs = new ArrayList<>();

        unmatchedA.sort(Comparator.comparing(r -> r.getAmount() != null ? r.getAmount().abs() : BigDecimal.ZERO));
        unmatchedB.sort(Comparator.comparing(r -> r.getAmount() != null ? r.getAmount().abs() : BigDecimal.ZERO));

        Iterator<ReconRecord> iterA = unmatchedA.iterator();
        while (iterA.hasNext()) {
            ReconRecord recordA = iterA.next();
            if (recordA.getAmount() == null) continue;

            ReconRecord bestMatch = null;
            BigDecimal bestDiff = null;

            for (ReconRecord recordB : unmatchedB) {
                if (recordB.getAmount() == null) continue;

                BigDecimal diff = recordA.getAmount().subtract(recordB.getAmount()).abs();
                if (diff.compareTo(amountTolerance) > 0) continue;

                if (!isDateWithinTolerance(recordA.getDate(), recordB.getDate(), dateToleranceDays)) {
                    continue;
                }

                if (bestDiff == null || diff.compareTo(bestDiff) < 0) {
                    bestDiff = diff;
                    bestMatch = recordB;
                }
            }

            if (bestMatch != null) {
                boolean hasDiff = bestDiff.compareTo(BigDecimal.ZERO) > 0;
                ReconResult.MatchedPair pair = ReconResult.MatchedPair.builder()
                        .source(recordA)
                        .target(bestMatch)
                        .hasDifference(hasDiff)
                        .differenceNote(hasDiff ? "金额差异: " + bestDiff.stripTrailingZeros().toPlainString() : null)
                        .build();
                matchedPairs.add(pair);
                unmatchedB.remove(bestMatch);
                iterA.remove();
            }
        }

        return buildResult(sourceA, sourceB, matchedPairs, unmatchedA, unmatchedB);
    }

    // ======================== 列匹配引擎 ========================

    /**
     * 从记录中获取指定列的值（支持模糊列名匹配）
     */
    private String getColumnValue(ReconRecord record, String columnName) {
        if (record == null || columnName == null) return null;
        if (record.getRawData() != null) {
            // 1. 精确匹配
            String val = record.getRawData().get(columnName);
            if (val != null && !val.isBlank()) return val.trim();

            // 2. 模糊匹配：去除前后空格后匹配
            String trimmedColName = columnName.trim();
            if (!trimmedColName.equals(columnName)) {
                val = record.getRawData().get(trimmedColName);
                if (val != null && !val.isBlank()) return val.trim();
            }

            // 3. 遍历 rawData 做标准化匹配（去除多余空格、Unicode 不可见字符）
            for (Map.Entry<String, String> entry : record.getRawData().entrySet()) {
                if (normalizeKey(entry.getKey()).equals(normalizeKey(columnName))
                        && entry.getValue() != null && !entry.getValue().isBlank()) {
                    return entry.getValue().trim();
                }
            }
        }
        // 兜底：尝试已知字段
        if ("金额".equals(columnName) && record.getAmount() != null)
            return record.getAmount().toPlainString();
        if ("日期".equals(columnName) && record.getDate() != null)
            return record.getDate();
        if ("描述".equals(columnName) && record.getDescription() != null)
            return record.getDescription();
        return null;
    }

    /**
     * 标准化列名：去空格、全角转半角、移除零宽字符，用于模糊匹配
     */
    private String normalizeKey(String key) {
        if (key == null) return "";
        return key.trim()
                .replace("\u00A0", " ")    // 不间断空格 → 普通空格
                .replace("\u3000", " ")     // 全角空格 → 半角空格
                .replaceAll("\\s+", "")     // 合并所有空白
                .replaceAll("[\\u200B\\u200C\\u200D\\uFEFF]", ""); // 零宽字符
    }

    /**
     * 根据匹配类型检查两列值是否匹配
     */
    private boolean checkMatch(String valA, String valB, ReconRequest.ColumnMatch colMatch,
                                List<String> diffNotes) {
        if (valA == null || valB == null || valA.isBlank() || valB.isBlank()) {
            return false; // 空值不算匹配
        }

        String type = colMatch.getMatchType() != null ? colMatch.getMatchType().toUpperCase() : "EXACT";

        switch (type) {
            case "NUMERIC_TOLERANCE": {
                BigDecimal tolerance = colMatch.getNumericTolerance() != null
                        ? colMatch.getNumericTolerance() : defaultAmountTolerance;
                return matchNumeric(valA, valB, tolerance, colMatch, diffNotes);
            }
            case "DATE_TOLERANCE": {
                int days = colMatch.getDateToleranceDays() != null
                        ? colMatch.getDateToleranceDays() : defaultDateToleranceDays;
                return matchDate(valA, valB, days, colMatch, diffNotes);
            }
            case "CONTAINS": {
                boolean result = valA.contains(valB) || valB.contains(valA);
                if (!result) {
                    diffNotes.add("[" + colMatch.getSourceColumn() + "↔" + colMatch.getTargetColumn()
                            + "] 内容不包含: \"" + truncate(valA, 20) + "\" vs \"" + truncate(valB, 20) + "\"");
                }
                return result;
            }
            case "EXACT":
            default: {
                boolean result = valA.equals(valB);
                if (!result) {
                    diffNotes.add("[" + colMatch.getSourceColumn() + "↔" + colMatch.getTargetColumn()
                            + "] 值不同: \"" + truncate(valA, 20) + "\" vs \"" + truncate(valB, 20) + "\"");
                }
                return result;
            }
        }
    }

    private boolean matchNumeric(String valA, String valB, BigDecimal tolerance,
                                  ReconRequest.ColumnMatch colMatch, List<String> diffNotes) {
        try {
            String cleanA = valA.replace(",", "").replace("¥", "").replace("$", "")
                    .replace("元", "").replace(" ", "").trim();
            String cleanB = valB.replace(",", "").replace("¥", "").replace("$", "")
                    .replace("元", "").replace(" ", "").trim();
            BigDecimal numA = new BigDecimal(cleanA);
            BigDecimal numB = new BigDecimal(cleanB);
            BigDecimal diff = numA.subtract(numB).abs();
            boolean result = diff.compareTo(tolerance) <= 0;
            if (!result) {
                diffNotes.add("[" + colMatch.getSourceColumn() + "↔" + colMatch.getTargetColumn()
                        + "] 数值差异: " + diff.stripTrailingZeros().toPlainString()
                        + " (容差: " + tolerance.toPlainString() + ")");
            }
            return result;
        } catch (NumberFormatException e) {
            return valA.equals(valB); // 解析失败降级为精确匹配
        }
    }

    private boolean matchDate(String valA, String valB, int days,
                               ReconRequest.ColumnMatch colMatch, List<String> diffNotes) {
        try {
            LocalDate d1 = parseDate(valA);
            LocalDate d2 = parseDate(valB);
            if (d1 == null || d2 == null) return valA.equals(valB);
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(d1, d2));
            boolean result = daysDiff <= days;
            if (!result) {
                diffNotes.add("[" + colMatch.getSourceColumn() + "↔" + colMatch.getTargetColumn()
                        + "] 日期差: " + daysDiff + "天 (容差: " + days + "天)");
            }
            return result;
        } catch (Exception e) {
            return valA.equals(valB);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    // ======================== 工具方法 ========================

    private ReconResult buildResult(List<ReconRecord> sourceA, List<ReconRecord> sourceB,
                                     List<ReconResult.MatchedPair> matchedPairs,
                                     List<ReconRecord> unmatchedA, List<ReconRecord> unmatchedB) {
        int total = sourceA.size() + sourceB.size();
        int matched = matchedPairs.size() * 2;
        double matchRate = total > 0 ? (double) matched / total * 100 : 0;

        return ReconResult.builder()
                .matchedPairs(matchedPairs)
                .onlyInSource(unmatchedA)
                .onlyInTarget(unmatchedB)
                .sourceCount(sourceA.size())
                .targetCount(sourceB.size())
                .matchedCount(matchedPairs.size())
                .matchRate(Math.round(matchRate * 100.0) / 100.0)
                .build();
    }

    private boolean isDateWithinTolerance(String dateStr1, String dateStr2, int toleranceDays) {
        if (dateStr1 == null || dateStr1.isEmpty() || dateStr2 == null || dateStr2.isEmpty()) {
            return true;
        }
        try {
            LocalDate date1 = parseDate(dateStr1);
            LocalDate date2 = parseDate(dateStr2);
            if (date1 == null || date2 == null) return true;
            long daysDiff = Math.abs(ChronoUnit.DAYS.between(date1, date2));
            return daysDiff <= toleranceDays;
        } catch (Exception e) {
            return true;
        }
    }

    private LocalDate parseDate(String dateStr) {
        String[] patterns = {
                "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
                "yyyy-M-d", "yyyy/M/d",
                "MM/dd/yyyy", "MM-dd-yyyy",
                "yyyyMMdd",
                "yyyy年MM月dd日", "yyyy年M月d日"
        };
        String cleanDate = dateStr.split("\\s")[0].split("T")[0].trim();
        for (String pattern : patterns) {
            try {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pattern);
                return LocalDate.parse(cleanDate, fmt);
            } catch (Exception ignored) {}
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            sdf.setLenient(false);
            Date date = sdf.parse(cleanDate);
            return LocalDate.ofInstant(date.toInstant(), java.time.ZoneId.systemDefault());
        } catch (ParseException ignored) {}
        return null;
    }
}
