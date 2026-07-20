package com.reconciliation.controller;

import com.reconciliation.model.ReconRecord;
import com.reconciliation.model.UploadResponse;
import com.reconciliation.service.AIService;
import com.reconciliation.service.DataStoreService;
import com.reconciliation.service.ExcelService;
import com.reconciliation.service.OcrService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final ExcelService excelService;
    private final OcrService ocrService;
    private final AIService aiService;
    private final DataStoreService dataStoreService;

    public UploadController(ExcelService excelService, OcrService ocrService,
                            AIService aiService, DataStoreService dataStoreService) {
        this.excelService = excelService;
        this.ocrService = ocrService;
        this.aiService = aiService;
        this.dataStoreService = dataStoreService;
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "ok", "timestamp", System.currentTimeMillis());
    }

    /**
     * 上传对账文件（Excel/CSV/图片）
     * 返回值改为 ResponseEntity 以便直接返回错误消息
     */
    @PostMapping("/file")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            String fileName = file.getOriginalFilename();
            log.info("收到上传请求: fileName={}, size={}, contentType={}",
                    fileName, file.getSize(), file.getContentType());

            // 参数校验
            if (file.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "文件内容为空，请重新选择文件");
            }
            if (fileName == null || fileName.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "文件名不能为空");
            }

            String ext = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
                    : "";

            ExcelService.ParseResult parseResult;
            Set<String> imageExts = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tiff", ".webp");

            if (imageExts.contains(ext)) {
                log.info("上传图片文件: {}, 使用 OCR 识别", fileName);
                parseResult = ocrService.parseImage(file);
            } else {
                log.info("上传表格文件: {}", fileName);
                parseResult = excelService.parse(file);
            }

            if (parseResult.records().isEmpty()) {
                return error(HttpStatus.BAD_REQUEST,
                        "文件解析成功但未找到有效数据行。请确保文件包含金额列（如：金额/amount/交易金额等）");
            }

            String dataId = UUID.randomUUID().toString().substring(0, 8);
            dataStoreService.putData(dataId, new ArrayList<>(parseResult.records()));
            dataStoreService.putMeta(dataId, Map.of(
                    "fileName", fileName,
                    "recordCount", parseResult.records().size(),
                    "sourceType", imageExts.contains(ext) ? "image" : "spreadsheet",
                    "headers", parseResult.headers()
            ));

            List<ReconRecord> preview = parseResult.records().stream()
                    .limit(20)
                    .toList();

            UploadResponse response = UploadResponse.builder()
                    .dataId(dataId)
                    .fileName(fileName)
                    .recordCount(parseResult.records().size())
                    .headers(parseResult.headers())
                    .previewData(preview)
                    .sourceType(imageExts.contains(ext) ? "image" : "spreadsheet")
                    .build();

            log.info("上传成功: dataId={}, records={}", dataId, parseResult.records().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("上传处理异常", e);
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "文件处理失败: " + detail);
        }
    }

    /**
     * AI 视觉识别图片上传 —— 直接用大模型"看懂"图片内容并提取表格
     */
    @PostMapping("/file-ai")
    public ResponseEntity<?> uploadFileWithAI(
            @RequestParam("file") MultipartFile file,
            @RequestParam("model") String model,
            @RequestParam("token") String token,
            @RequestParam(value = "baseUrl", defaultValue = "https://api.openai.com/v1") String baseUrl) {
        try {
            String fileName = file.getOriginalFilename();
            log.info("收到 AI 图片上传请求: fileName={}, size={}, model={}",
                    fileName, file.getSize(), model);

            if (file.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "文件内容为空，请重新选择文件");
            }
            if (fileName == null || fileName.isBlank()) {
                return error(HttpStatus.BAD_REQUEST, "文件名不能为空");
            }

            String ext = fileName.contains(".")
                    ? fileName.substring(fileName.lastIndexOf('.')).toLowerCase()
                    : "";
            Set<String> imageExts = Set.of(".png", ".jpg", ".jpeg", ".bmp", ".gif", ".tiff", ".webp");

            if (!imageExts.contains(ext)) {
                return error(HttpStatus.BAD_REQUEST, "AI图片识别仅支持图片格式: png/jpg/jpeg/bmp/gif");
            }

            log.info("使用 AI 视觉模型识别图片: {}", fileName);
            ExcelService.ParseResult parseResult = aiService.parseImageWithVision(
                    file, model, token, baseUrl);

            if (parseResult.records().isEmpty()) {
                return error(HttpStatus.BAD_REQUEST,
                        "AI图片识别完成但未找到有效表格数据，请确认图片中包含清晰的表格");
            }

            String dataId = UUID.randomUUID().toString().substring(0, 8);
            dataStoreService.putData(dataId, new ArrayList<>(parseResult.records()));
            dataStoreService.putMeta(dataId, Map.of(
                    "fileName", fileName,
                    "recordCount", parseResult.records().size(),
                    "sourceType", "image-ai",
                    "headers", parseResult.headers()
            ));

            List<ReconRecord> preview = parseResult.records().stream()
                    .limit(20)
                    .toList();

            UploadResponse response = UploadResponse.builder()
                    .dataId(dataId)
                    .fileName(fileName)
                    .recordCount(parseResult.records().size())
                    .headers(parseResult.headers())
                    .previewData(preview)
                    .sourceType("image-ai")
                    .build();

            log.info("AI图片识别上传成功: dataId={}, records={}", dataId, parseResult.records().size());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("AI图片上传处理异常", e);
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();

            // 识别常见错误，给出明确提示
            String friendlyMsg = "AI图片识别失败";
            String lower = detail.toLowerCase();
            if (lower.contains("401") || lower.contains("unauthorized") || lower.contains("invalid token")
                    || lower.contains("incorrect api key")) {
                friendlyMsg = "Token无效或已过期，请在右上角AI设置中更换Token";
            } else if (lower.contains("429") || lower.contains("rate limit") || lower.contains("quota")) {
                friendlyMsg = "AI服务调用次数已达上限或Token余额不足";
            } else if (lower.contains("timeout") || lower.contains("timed out")) {
                friendlyMsg = "AI服务请求超时，请检查网络或稍后重试";
            } else if (lower.contains("403") || lower.contains("forbidden")) {
                friendlyMsg = "AI服务访问受限，请检查Token权限";
            } else {
                friendlyMsg = "AI服务暂不可用或Token可能已用完，请检查后重试";
            }

            return error(HttpStatus.INTERNAL_SERVER_ERROR, friendlyMsg + " (" + detail + ")");
        }
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    @GetMapping("/data/{dataId}")
    public List<ReconRecord> getData(@PathVariable String dataId) {
        return dataStoreService.getData(dataId);
    }

    @GetMapping("/sources")
    public List<Map<String, Object>> getSources() {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : dataStoreService.getMetaStore().entrySet()) {
            Map<String, Object> info = new LinkedHashMap<>(entry.getValue());
            info.put("dataId", entry.getKey());
            sources.add(info);
        }
        return sources;
    }

    @DeleteMapping("/data/{dataId}")
    public Map<String, String> deleteData(@PathVariable String dataId) {
        dataStoreService.remove(dataId);
        return Map.of("status", "ok", "message", "数据源已删除");
    }
}
