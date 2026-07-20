package com.reconciliation.service;

import com.reconciliation.model.ReconRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OCR 图片识别服务
 * 使用 Tesseract-OCR CLI + TSV 输出，按坐标还原表格
 */
@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    @Value("${ocr.enabled:false}")
    private boolean ocrEnabled;

    @Value("${ocr.tesseract-cmd:tesseract}")
    private String tesseractCmd;

    @Value("${ocr.tessdata-path:C:/Program Files/Tesseract-OCR/tessdata}")
    private String tessdataPath;

    @Value("${ocr.language:chi_sim+eng}")
    private String language;

    @Value("${ocr.preprocess:true}")
    private boolean preprocessEnabled;

    @Value("${ocr.scale:2}")
    private int scaleFactor;

    /** Y轴容差：两个词的行间距在此范围内视为同一行（像素） */
    private static final int ROW_TOLERANCE = 10;
    /** 列检测窗口半径（像素） */
    private static final int COL_SMOOTH_RADIUS = 25;
    /** 列峰值检测最小高度比例 */
    private static final double COL_PEAK_MIN_RATIO = 0.15;

    /**
     * 对图片进行 OCR 识别并解析为对账记录
     */
    public ExcelService.ParseResult parseImage(MultipartFile file) throws Exception {
        Path tempFile = Files.createTempFile("recon_ocr_", ".png");
        file.transferTo(tempFile.toFile());

        Path processedFile = null;
        try {
            if (!ocrEnabled) {
                throw new IllegalArgumentException("OCR 功能未启用，请在 application.yml 中设置 ocr.enabled=true");
            }

            BufferedImage original = ImageIO.read(tempFile.toFile());
            if (original == null) {
                throw new IOException("无法读取图片，请确认图片格式正确");
            }
            log.info("原始图片尺寸: {}x{}", original.getWidth(), original.getHeight());

            // 预处理：去线、放大、二值化
            if (preprocessEnabled) {
                processedFile = preprocessImage(tempFile, original);
            } else {
                processedFile = tempFile;
            }

            // 用 TSV 模式识别，获取每个词的坐标
            List<WordBox> words = doOcrWithCoordinates(processedFile);
            if (words.isEmpty()) {
                log.warn("OCR 未识别到任何文字");
                return new ExcelService.ParseResult(List.of("列1"), List.of());
            }

            // 按坐标重建表格
            return buildTableFromWords(words);

        } finally {
            try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
            if (processedFile != null && !processedFile.equals(tempFile)) {
                try { Files.deleteIfExists(processedFile); } catch (Exception ignored) {}
            }
        }
    }

    // ======================== 图像预处理 ========================

    /**
     * 预处理图像：灰度化 → 放大 → Otsu二值化 → 去除横竖线
     */
    private Path preprocessImage(Path inputPath, BufferedImage original) throws IOException {
        // 1. 灰度化
        BufferedImage gray = toGrayscale(original);

        // 2. 放大 (截图分辨率通常低，放大可显著提升 OCR 准确率)
        int targetW = original.getWidth() * scaleFactor;
        int targetH = original.getHeight() * scaleFactor;
        BufferedImage scaled = scaleImage(gray, targetW, targetH);

        // 3. Otsu 二值化
        BufferedImage binary = otsuBinarize(scaled);

        // 4. 去除表格线 (横竖长线)
        removeLines(binary);

        // 5. 保存临时文件
        Path output = Files.createTempFile("recon_ocr_processed_", ".png");
        ImageIO.write(binary, "png", output.toFile());
        log.info("预处理完成: 输出图片 {}x{}", binary.getWidth(), binary.getHeight());
        return output;
    }

    private BufferedImage toGrayscale(BufferedImage src) {
        BufferedImage gray = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage scaleImage(BufferedImage src, int w, int h) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return scaled;
    }

    /** Otsu 自动阈值二值化 */
    private BufferedImage otsuBinarize(BufferedImage src) {
        int width = src.getWidth();
        int height = src.getHeight();
        int[] hist = new int[256];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = src.getRGB(x, y) & 0xFF;
                hist[rgb]++;
            }
        }

        int total = width * height;
        long sum = 0;
        for (int i = 0; i < 256; i++) sum += i * hist[i];

        long sumB = 0;
        int wB = 0;
        double maxVar = 0;
        int threshold = 128;

        for (int t = 0; t < 256; t++) {
            wB += hist[t];
            if (wB == 0) continue;
            int wF = total - wB;
            if (wF == 0) break;
            sumB += (long) t * hist[t];
            double mB = (double) sumB / wB;
            double mF = (double) (sum - sumB) / wF;
            double varBetween = (double) wB * wF * (mB - mF) * (mB - mF);
            if (varBetween > maxVar) {
                maxVar = varBetween;
                threshold = t;
            }
        }

        // 判断前景色：通常文字更黑，数量更少；如果白色像素更多，则反色
        int blackCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = src.getRGB(x, y) & 0xFF;
                if (v < threshold) blackCount++;
            }
        }
        boolean invert = blackCount > total / 2;

        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int v = src.getRGB(x, y) & 0xFF;
                boolean isForeground = v < threshold;
                if (invert) isForeground = !isForeground;
                // 文字黑 (0), 背景白 (0xFFFFFF)
                out.setRGB(x, y, isForeground ? 0x000000 : 0xFFFFFF);
            }
        }
        return out;
    }

    /** 去除长横线和长竖线 (Excel 网格线) */
    private void removeLines(BufferedImage binary) {
        int w = binary.getWidth();
        int h = binary.getHeight();
        boolean[][] isLine = new boolean[w][h];

        // 水平线：某行黑色像素占比超过 80%
        int horizontalThreshold = (int) (w * 0.80);
        for (int y = 0; y < h; y++) {
            int blackCount = 0;
            for (int x = 0; x < w; x++) {
                if (isBlack(binary, x, y)) blackCount++;
            }
            if (blackCount >= horizontalThreshold) {
                for (int x = 0; x < w; x++) isLine[x][y] = true;
            }
        }

        // 竖直线：某列黑色像素占比超过 80%
        int verticalThreshold = (int) (h * 0.80);
        for (int x = 0; x < w; x++) {
            int blackCount = 0;
            for (int y = 0; y < h; y++) {
                if (isBlack(binary, x, y)) blackCount++;
            }
            if (blackCount >= verticalThreshold) {
                for (int y = 0; y < h; y++) isLine[x][y] = true;
            }
        }

        // 清除标记的线像素
        int removed = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isLine[x][y]) {
                    binary.setRGB(x, y, 0xFFFFFF);
                    removed++;
                }
            }
        }
        log.info("去除网格线: {} 像素", removed);
    }

    private boolean isBlack(BufferedImage img, int x, int y) {
        return (img.getRGB(x, y) & 0xFFFFFF) == 0x000000;
    }

    // ======================== TSV OCR 引擎 ========================

    /**
     * 调用 tesseract CLI 输出 TSV
     */
    private List<WordBox> doOcrWithCoordinates(Path imagePath) throws Exception {
        String imageFile = imagePath.toAbsolutePath().toString();
        String tessdataDir = tessdataPath.replace("\\", "/");

        List<String> cmd = new ArrayList<>();
        cmd.add(tesseractCmd);
        cmd.add(imageFile);
        cmd.add("stdout");
        cmd.add("--tessdata-dir");
        cmd.add(tessdataDir);
        cmd.add("-l");
        cmd.add(language);
        cmd.add("--psm");
        cmd.add("3");
        cmd.add("tsv");

        log.info("执行 Tesseract CLI: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();

        List<WordBox> words = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                if (!headerSkipped) {
                    headerSkipped = true;
                    continue;
                }
                WordBox w = parseTsvLine(line);
                if (w != null && w.confidence >= 30) {
                    words.add(w);
                }
            }
        }

        StringBuilder errOut = new StringBuilder();
        try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            String errLine;
            while ((errLine = errReader.readLine()) != null) {
                errOut.append(errLine).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errMsg = errOut.toString().trim();
            log.error("Tesseract 退出码={}, stderr: {}", exitCode, errMsg);
            throw new RuntimeException("Tesseract OCR 执行失败 (退出码=" + exitCode + "): " + errMsg
                    + "\n请确认 tesseract 已安装并加入 PATH，或配置 ocr.tesseract-cmd");
        }

        log.info("OCR TSV 识别完成: {} 个有效词", words.size());
        return words;
    }

    private WordBox parseTsvLine(String line) {
        String[] cols = line.split("\t");
        if (cols.length < 12) return null;
        try {
            int level = Integer.parseInt(cols[0]);
            if (level != 5) return null;
            int left   = Integer.parseInt(cols[6]);
            int top    = Integer.parseInt(cols[7]);
            int width  = Integer.parseInt(cols[8]);
            int height = Integer.parseInt(cols[9]);
            int conf   = Integer.parseInt(cols[10]);
            String text = cols.length > 11 ? cols[11].trim() : "";
            if (text.isEmpty()) return null;
            return new WordBox(text, left, top, left + width, top + height, conf);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ======================== 坐标 → 表格重建 ========================

    private ExcelService.ParseResult buildTableFromWords(List<WordBox> words) {
        List<RowGroup> rows = groupByRows(words);
        log.info("按坐标分出行: {} 行", rows.size());

        if (rows.isEmpty()) {
            return new ExcelService.ParseResult(List.of("列1"), List.of());
        }

        List<ColBoundary> colBoundaries = computeColumnBoundaries(rows);
        log.info("列边界 ({} 列): {}", colBoundaries.size(),
                colBoundaries.stream().map(c -> c.minX + "-" + c.maxX).collect(Collectors.joining(", ")));

        int colCount = colBoundaries.size();
        if (colCount == 0) {
            return new ExcelService.ParseResult(List.of("列1"), List.of());
        }

        List<String> headers;
        int dataStartIdx;
        if (rows.size() >= 2) {
            // 第一行作为表头
            headers = new ArrayList<>();
            RowGroup headerRow = rows.get(0);
            for (int c = 0; c < colCount; c++) {
                String cellText = cleanCellText(getCellText(headerRow, colBoundaries.get(c)));
                headers.add(cellText.isEmpty() ? "列" + (c + 1) : cellText);
            }
            dataStartIdx = 1;
        } else {
            headers = new ArrayList<>();
            for (int i = 0; i < colCount; i++) headers.add("列" + (i + 1));
            dataStartIdx = 0;
        }

        log.info("OCR 表头: {}", headers);

        List<ReconRecord> records = new ArrayList<>();
        for (int i = dataStartIdx; i < rows.size(); i++) {
            RowGroup row = rows.get(i);
            Map<String, String> rawData = new LinkedHashMap<>();
            for (int c = 0; c < colCount; c++) {
                String cellText = cleanCellText(getCellText(row, colBoundaries.get(c)));
                rawData.put(headers.get(c), cellText);
            }

            BigDecimal amount = extractAmountFromRaw(rawData);
            String date = extractDateFromRaw(rawData);
            String desc = extractDescriptionFromRaw(rawData);

            ReconRecord record = ReconRecord.builder()
                    .index(i - dataStartIdx)
                    .date(date)
                    .amount(amount)
                    .description(desc)
                    .rawData(rawData)
                    .build();

            records.add(record);
        }

        log.info("OCR 表格重建完成: {} 行 × {} 列, 数据 {} 条", rows.size(), colCount, records.size());
        return new ExcelService.ParseResult(headers, records);
    }

    /** 清理单元格内 OCR 常见噪声 */
    private String cleanCellText(String text) {
        if (text == null) return "";
        // 去掉下拉箭头、排序图标等常见干扰符号
        String cleaned = text.replaceAll("[▼▽▾▿vˇˆ^]", "").trim();
        return cleaned;
    }

    /** 按 Y 坐标分组为行 */
    private List<RowGroup> groupByRows(List<WordBox> words) {
        if (words.isEmpty()) return List.of();
        words.sort(Comparator.comparingInt(w -> (w.top + w.bottom) / 2));

        List<RowGroup> rows = new ArrayList<>();
        RowGroup current = new RowGroup();
        current.words.add(words.get(0));
        current.top = words.get(0).top;
        current.bottom = words.get(0).bottom;
        current.centerY = (words.get(0).top + words.get(0).bottom) / 2;

        for (int i = 1; i < words.size(); i++) {
            WordBox w = words.get(i);
            int curCenter = (w.top + w.bottom) / 2;
            if (Math.abs(curCenter - current.centerY) <= ROW_TOLERANCE
                    || (w.top <= current.bottom && w.bottom >= current.top)) {
                current.words.add(w);
                current.top = Math.min(current.top, w.top);
                current.bottom = Math.max(current.bottom, w.bottom);
                current.centerY = (current.top + current.bottom) / 2;
            } else {
                rows.add(current);
                current = new RowGroup();
                current.words.add(w);
                current.top = w.top;
                current.bottom = w.bottom;
                current.centerY = curCenter;
            }
        }
        rows.add(current);

        // 按每行 x 坐标对词排序
        for (RowGroup row : rows) {
            row.words.sort(Comparator.comparingInt(w -> w.left));
        }
        return rows;
    }

    /**
     * 使用直方图峰谷检测列边界
     * 比固定容差聚类更鲁棒，适合列间距不一的表格
     */
    private List<ColBoundary> computeColumnBoundaries(List<RowGroup> rows) {
        if (rows.isEmpty() || rows.stream().mapToInt(r -> r.words.size()).sum() == 0) {
            return List.of();
        }

        // 估计图像宽度
        int maxX = 0;
        for (RowGroup row : rows) {
            for (WordBox w : row.words) {
                if (w.right > maxX) maxX = w.right;
            }
        }
        if (maxX == 0) return List.of();

        // 构建直方图：每个 x 坐标上有多少词的中心落在此处
        int[] histogram = new int[maxX + 1];
        for (RowGroup row : rows) {
            for (WordBox w : row.words) {
                int center = (w.left + w.right) / 2;
                if (center >= 0 && center <= maxX) histogram[center]++;
            }
        }

        // 平滑直方图
        int[] smoothed = smoothHistogram(histogram, COL_SMOOTH_RADIUS);

        // 找到所有峰值
        List<Integer> peaks = findPeaks(smoothed, maxX);
        if (peaks.isEmpty()) return List.of();

        // 按峰值生成列边界
        List<ColBoundary> boundaries = new ArrayList<>();
        for (int i = 0; i < peaks.size(); i++) {
            int leftBound = (i == 0) ? 0 : (peaks.get(i - 1) + peaks.get(i)) / 2;
            int rightBound = (i == peaks.size() - 1) ? maxX : (peaks.get(i) + peaks.get(i + 1)) / 2;
            boundaries.add(new ColBoundary(leftBound, rightBound));
        }

        return boundaries;
    }

    private int[] smoothHistogram(int[] hist, int radius) {
        int n = hist.length;
        int[] smoothed = new int[n];
        for (int i = 0; i < n; i++) {
            int sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - radius); j <= Math.min(n - 1, i + radius); j++) {
                sum += hist[j];
                count++;
            }
            smoothed[i] = sum / count;
        }
        return smoothed;
    }

    private List<Integer> findPeaks(int[] smoothed, int maxX) {
        List<Integer> peaks = new ArrayList<>();
        int maxVal = 0;
        for (int v : smoothed) if (v > maxVal) maxVal = v;
        int minHeight = Math.max(2, (int) (maxVal * COL_PEAK_MIN_RATIO));

        for (int i = 1; i < maxX; i++) {
            if (smoothed[i] > smoothed[i - 1] && smoothed[i] >= smoothed[i + 1]
                    && smoothed[i] >= minHeight) {
                // 避免重复峰值，只保留比之前峰值距离较远的
                if (peaks.isEmpty() || i - peaks.get(peaks.size() - 1) > COL_SMOOTH_RADIUS) {
                    peaks.add(i);
                }
            }
        }
        return peaks;
    }

    private String getCellText(RowGroup row, ColBoundary col) {
        StringBuilder sb = new StringBuilder();
        for (WordBox w : row.words) {
            int centerX = (w.left + w.right) / 2;
            if (centerX >= col.minX && centerX <= col.maxX) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(w.text);
            }
        }
        return sb.toString().trim();
    }

    // ======================== 辅助提取方法 ========================

    private BigDecimal extractAmountFromRaw(Map<String, String> rawData) {
        for (String val : rawData.values()) {
            if (val == null || val.isBlank()) continue;
            String clean = val.replace(",", "").replace("¥", "").replace("$", "")
                    .replace("€", "").replace("￥", "").replace("元", "").trim();
            if (clean.matches("-?\\d+\\.?\\d*")) {
                try { return new BigDecimal(clean); } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private String extractDateFromRaw(Map<String, String> rawData) {
        for (String val : rawData.values()) {
            if (val != null && val.matches("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}")) return val;
            if (val != null && val.matches("\\d{1,2}[-/.]\\d{1,2}[-/.]\\d{2,4}")) return val;
        }
        return "";
    }

    private String extractDescriptionFromRaw(Map<String, String> rawData) {
        for (String key : rawData.keySet()) {
            String keyLower = key.toLowerCase();
            if (keyLower.contains("摘要") || keyLower.contains("描述")
                    || keyLower.contains("说明") || keyLower.contains("备注")
                    || keyLower.contains("商品") || keyLower.contains("产品")) {
                return rawData.get(key);
            }
        }
        String longest = "";
        for (String val : rawData.values()) {
            if (val != null && val.length() > longest.length()
                    && !val.matches("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}")
                    && !val.matches("-?[\\d,\\.]+")) {
                longest = val;
            }
        }
        return longest;
    }

    // ======================== 内部数据类 ========================

    private static class WordBox {
        final String text;
        final int left, top, right, bottom, confidence;
        WordBox(String text, int left, int top, int right, int bottom, int confidence) {
            this.text = text; this.left = left; this.top = top;
            this.right = right; this.bottom = bottom; this.confidence = confidence;
        }
    }

    private static class RowGroup {
        List<WordBox> words = new ArrayList<>();
        int top, bottom, centerY;
    }

    private static class ColBoundary {
        int minX, maxX;
        ColBoundary(int minX, int maxX) { this.minX = minX; this.maxX = maxX; }
    }
}
