package com.reconciliation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconciliation.dto.AIReconRequest;
import com.reconciliation.model.ReconRecord;
import com.reconciliation.model.ReconResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 对账服务 - 调用大模型 API 进行智能匹配
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);

    private final DataStoreService dataStoreService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public AIService(DataStoreService dataStoreService, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.dataStoreService = dataStoreService;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行 AI 对账
     */
    public ReconResult runAIReconciliation(AIReconRequest request) {
        List<ReconRecord> sourceData = dataStoreService.getData(request.getSourceDataId());
        List<ReconRecord> targetData = dataStoreService.getData(request.getTargetDataId());

        if (sourceData == null || sourceData.isEmpty()) {
            throw new IllegalArgumentException("数据源A未找到或为空");
        }
        if (targetData == null || targetData.isEmpty()) {
            throw new IllegalArgumentException("数据源B未找到或为空");
        }

        // 收集所有列名
        Set<String> allHeaders = collectHeaders(sourceData, targetData);

        // 构建 prompt
        String customPrompt = request.getCustomPrompt();
        String prompt = buildPrompt(sourceData, targetData, allHeaders, customPrompt);

        // 调用 AI API
        String aiResponse = callAI(request.getBaseUrl(), request.getToken(), request.getModel(), prompt);

        // 解析 AI 返回的匹配结果
        return parseAIResponse(aiResponse, sourceData, targetData, allHeaders);
    }

    /**
     * 收集所有表头
     */
    private Set<String> collectHeaders(List<ReconRecord> sourceData, List<ReconRecord> targetData) {
        Set<String> headers = new LinkedHashSet<>();
        for (ReconRecord r : sourceData) {
            if (r.getRawData() != null) headers.addAll(r.getRawData().keySet());
        }
        for (ReconRecord r : targetData) {
            if (r.getRawData() != null) headers.addAll(r.getRawData().keySet());
        }
        return headers;
    }

    /**
     * 构建 AI prompt
     */
    private String buildPrompt(List<ReconRecord> sourceData, List<ReconRecord> targetData,
                                Set<String> headers, String customPrompt) {
        String headerList = String.join("、", headers);

        StringBuilder sb = new StringBuilder();
        sb.append("你是一个专业的数据对账助手。\n\n");

        // 数据源
        sb.append("数据源A（").append(sourceData.size()).append("条记录）：\n");
        sb.append("列：").append(headerList).append("\n");
        for (int i = 0; i < sourceData.size(); i++) {
            sb.append("A").append(i).append(": ");
            sb.append(formatRecord(sourceData.get(i), headers)).append("\n");
        }

        sb.append("\n数据源B（").append(targetData.size()).append("条记录）：\n");
        sb.append("列：").append(headerList).append("\n");
        for (int i = 0; i < targetData.size(); i++) {
            sb.append("B").append(i).append(": ");
            sb.append(formatRecord(targetData.get(i), headers)).append("\n");
        }

        // 对账指令
        if (customPrompt != null && !customPrompt.isBlank()) {
            sb.append("\n请按照以下用户指令进行数据对账：\n");
            sb.append(customPrompt).append("\n");
        } else {
            sb.append("\n请找出A和B之间匹配的记录，使用智能模糊匹配（名称相似、金额相近、日期接近等都可以视为匹配）。\n");
        }

        sb.append("\n请按以下JSON格式返回结果（只返回JSON，不要其他内容）：\n");
        sb.append("{\n");
        sb.append("  \"matchedPairs\": [\n");
        sb.append("    { \"sourceIndex\": 0, \"targetIndex\": 1, \"matchReason\": \"姓名和学号一致\", \"hasDifference\": false, \"differenceNote\": \"\" }\n");
        sb.append("  ],\n");
        sb.append("  \"onlyInSource\": [2, 5],\n");
        sb.append("  \"onlyInTarget\": [3],\n");
        sb.append("  \"analysisNote\": \"整体匹配分析说明\"\n");
        sb.append("}\n\n");
        sb.append("注意：sourceIndex/targetIndex 对应上面 A/B 后面的数字。一条A记录最多匹配一条B记录。");

        return sb.toString();
    }

    /**
     * 格式化单条记录
     */
    private String formatRecord(ReconRecord record, Set<String> headers) {
        StringBuilder sb = new StringBuilder("{");
        List<String> parts = new ArrayList<>();
        for (String h : headers) {
            String val = getVal(record, h);
            if (val != null && !val.isBlank()) {
                parts.add("\"" + h + "\": \"" + escapeJson(val) + "\"");
            }
        }
        sb.append(String.join(", ", parts));
        sb.append("}");
        return sb.toString();
    }

    private String getVal(ReconRecord record, String colName) {
        if (record == null || colName == null) return null;
        if (record.getRawData() != null) {
            String val = record.getRawData().get(colName);
            if (val != null && !val.isBlank()) return val.trim();
        }
        if ("金额".equals(colName) && record.getAmount() != null)
            return record.getAmount().toPlainString();
        if ("日期".equals(colName) && record.getDate() != null)
            return record.getDate();
        if ("描述".equals(colName) && record.getDescription() != null)
            return record.getDescription();
        return null;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * 调用 AI API（自动识别 OpenAI 兼容 / Anthropic 兼容格式）
     */
    private String callAI(String baseUrl, String apiKey, String model, String prompt) {
        String cleanUrl = (baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1")
                .replaceAll("/+$", "");

        if (cleanUrl.contains("anthropic")) {
            return callAnthropicTextAPI(cleanUrl, apiKey, model, prompt);
        }

        String url = cleanUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model != null ? model : "gpt-4o-mini");
        body.put("temperature", 0.1);
        body.put("max_tokens", 4096);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", prompt));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            log.info("调用 OpenAI兼容API: url={}, model={}, prompt长度={}", url, model, prompt.length());
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode message = choices.get(0).get("message");
                    if (message != null) {
                        String content = message.get("content").asText();
                        log.info("AI 返回内容长度: {}", content.length());
                        return content;
                    }
                }
                throw new RuntimeException("AI 返回格式异常: " + response.getBody());
            }
            throw new RuntimeException("AI API 调用失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("AI API 调用失败", e);
            throw new RuntimeException("AI 接口调用失败: " + e.getMessage());
        }
    }

    /**
     * Anthropic 兼容 API（纯文本）
     */
    private String callAnthropicTextAPI(String baseUrl, String apiKey, String model, String prompt) {
        String url = baseUrl + "/v1/messages";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model != null ? model : "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 4096);
        body.put("temperature", 0.1);

        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", content));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            log.info("调用 Anthropic兼容API: url={}, model={}, prompt长度={}", url, model, prompt.length());
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode contentNode = root.get("content");
                if (contentNode != null && contentNode.isArray() && contentNode.size() > 0) {
                    String text = contentNode.get(0).get("text").asText();
                    log.info("Anthropic API 返回内容长度: {}", text.length());
                    return text;
                }
                throw new RuntimeException("Anthropic API 返回格式异常: " + response.getBody());
            }
            throw new RuntimeException("Anthropic API 调用失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Anthropic API 调用失败", e);
            throw new RuntimeException("Anthropic API 调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析 AI 返回的 JSON 结果
     */
    private ReconResult parseAIResponse(String aiResponse, List<ReconRecord> sourceData,
                                         List<ReconRecord> targetData, Set<String> headers) {
        try {
            // 提取 JSON 内容（AI 可能包裹在 ```json ... ``` 中）
            String json = extractJson(aiResponse);
            JsonNode root = objectMapper.readTree(json);

            Set<Integer> matchedSourceIdx = new HashSet<>();
            Set<Integer> matchedTargetIdx = new HashSet<>();

            List<ReconResult.MatchedPair> matchedPairs = new ArrayList<>();
            JsonNode pairsNode = root.get("matchedPairs");
            if (pairsNode != null && pairsNode.isArray()) {
                for (JsonNode pairNode : pairsNode) {
                    int si = pairNode.get("sourceIndex").asInt();
                    int ti = pairNode.get("targetIndex").asInt();

                    if (si < 0 || si >= sourceData.size() || ti < 0 || ti >= targetData.size()) continue;

                    ReconRecord src = sourceData.get(si);
                    ReconRecord tgt = targetData.get(ti);
                    boolean hasDiff = pairNode.has("hasDifference") && pairNode.get("hasDifference").asBoolean();
                    String diffNote = pairNode.has("differenceNote") ? pairNode.get("differenceNote").asText() : "";
                    String reason = pairNode.has("matchReason") ? pairNode.get("matchReason").asText() : "";
                    String note = reason;
                    if (hasDiff && !diffNote.isBlank()) {
                        note += (note.isEmpty() ? "" : "; ") + diffNote;
                    }

                    matchedPairs.add(ReconResult.MatchedPair.builder()
                            .source(src)
                            .target(tgt)
                            .hasDifference(hasDiff)
                            .differenceNote(note.isEmpty() ? null : note)
                            .build());
                    matchedSourceIdx.add(si);
                    matchedTargetIdx.add(ti);
                }
            }

            // 解析仅A未匹配
            List<ReconRecord> onlyInSource = new ArrayList<>();
            JsonNode onlySourceNode = root.get("onlyInSource");
            if (onlySourceNode != null && onlySourceNode.isArray()) {
                for (JsonNode idx : onlySourceNode) {
                    int i = idx.asInt();
                    if (i >= 0 && i < sourceData.size() && !matchedSourceIdx.contains(i)) {
                        onlyInSource.add(sourceData.get(i));
                    }
                }
            }
            // 补充AI未标记的未匹配记录
            for (int i = 0; i < sourceData.size(); i++) {
                if (!matchedSourceIdx.contains(i) && !onlyInSource.contains(sourceData.get(i))) {
                    onlyInSource.add(sourceData.get(i));
                }
            }

            // 解析仅B未匹配
            List<ReconRecord> onlyInTarget = new ArrayList<>();
            JsonNode onlyTargetNode = root.get("onlyInTarget");
            if (onlyTargetNode != null && onlyTargetNode.isArray()) {
                for (JsonNode idx : onlyTargetNode) {
                    int i = idx.asInt();
                    if (i >= 0 && i < targetData.size() && !matchedTargetIdx.contains(i)) {
                        onlyInTarget.add(targetData.get(i));
                    }
                }
            }
            for (int i = 0; i < targetData.size(); i++) {
                if (!matchedTargetIdx.contains(i) && !onlyInTarget.contains(targetData.get(i))) {
                    onlyInTarget.add(targetData.get(i));
                }
            }

            int total = sourceData.size() + targetData.size();
            int matched = matchedPairs.size() * 2;
            double matchRate = total > 0 ? (double) matched / total * 100 : 0;

            // AI 分析摘要
            String analysisNote = root.has("analysisNote") ? root.get("analysisNote").asText() : "";
            log.info("AI对账完成: 匹配{}对, 仅A {}条, 仅B {}条, 分析: {}",
                    matchedPairs.size(), onlyInSource.size(), onlyInTarget.size(), analysisNote);

            return ReconResult.builder()
                    .matchedPairs(matchedPairs)
                    .onlyInSource(onlyInSource)
                    .onlyInTarget(onlyInTarget)
                    .sourceCount(sourceData.size())
                    .targetCount(targetData.size())
                    .matchedCount(matchedPairs.size())
                    .matchRate(Math.round(matchRate * 100.0) / 100.0)
                    .build();
        } catch (Exception e) {
            log.error("解析 AI 返回结果失败", e);
            throw new RuntimeException("AI 返回结果解析失败: " + e.getMessage());
        }
    }

    /**
     * 从 AI 回复中提取 JSON
     */
    private String extractJson(String text) {
        String trimmed = text.trim();
        // 去除 ```json ... ``` 包裹
        if (trimmed.startsWith("```json")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                return trimmed.substring(start, end).trim();
            }
        }
        // 尝试截取 { 到 } 的内容
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return trimmed;
    }

    // ==================== AI 视觉识别图片表格 ====================

    /**
     * 使用 AI 视觉模型直接识别图片中的表格数据
     */
    public ExcelService.ParseResult parseImageWithVision(MultipartFile imageFile,
                                                          String model, String token, String baseUrl) {
        try {
            String mimeType = imageFile.getContentType();
            if (mimeType == null || !mimeType.startsWith("image/")) {
                mimeType = "image/png";
            }
            byte[] imageBytes = imageFile.getBytes();
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64Image;

            log.info("AI视觉识别图片: fileName={}, size={}KB, model={}",
                    imageFile.getOriginalFilename(), imageBytes.length / 1024, model);

            String prompt = buildImageExtractionPrompt();
            String aiResponse = callVisionAPI(baseUrl, token, model, prompt, dataUrl);

            return parseImageTableResult(aiResponse);
        } catch (Exception e) {
            log.error("AI视觉识别图片失败", e);
            throw new RuntimeException("AI图片识别失败: " + e.getMessage());
        }
    }

    /**
     * 构建图片表格提取的 prompt
     */
    private String buildImageExtractionPrompt() {
        return """
                请仔细查看这张图片，提取其中的表格数据。
                
                要求：
                1. 第一行通常是表头，识别所有列名
                2. 逐行提取所有数据，保持原有顺序
                3. 数字保留原样，不要添加千位分隔符
                4. 空单元格用空字符串表示
                
                请严格按以下JSON格式返回（只返回JSON，不要其他内容）：
                {
                  "headers": ["列名1", "列名2", "列名3"],
                  "rows": [
                    ["值1-1", "值1-2", "值1-3"],
                    ["值2-1", "值2-2", "值2-3"]
                  ]
                }
                
                注意：如果图片中没有表格，请尝试提取所有可见的文字内容，每行文字作为一行数据。""";
    }

    /**
     * 调用 AI 视觉 API（自动识别 OpenAI 兼容 / Anthropic 兼容格式）
     */
    private String callVisionAPI(String baseUrl, String apiKey, String model,
                                  String prompt, String imageDataUrl) {
        String cleanUrl = (baseUrl != null && !baseUrl.isBlank() ? baseUrl : "https://api.openai.com/v1")
                .replaceAll("/+$", "");

        if (cleanUrl.contains("anthropic")) {
            return callAnthropicVisionAPI(cleanUrl, apiKey, model, prompt, imageDataUrl);
        }

        String url = cleanUrl + "/chat/completions";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model != null ? model : "gpt-4o-mini");
        body.put("temperature", 0.1);
        body.put("max_tokens", 4096);

        // 构建多模态消息（文本 + 图片）
        List<Map<String, Object>> content = new ArrayList<>();

        Map<String, Object> textPart = new LinkedHashMap<>();
        textPart.put("type", "text");
        textPart.put("text", prompt);
        content.add(textPart);

        Map<String, Object> imagePart = new LinkedHashMap<>();
        imagePart.put("type", "image_url");
        Map<String, String> imageUrlObj = new LinkedHashMap<>();
        imageUrlObj.put("url", imageDataUrl);
        imageUrlObj.put("detail", "high");
        imagePart.put("image_url", imageUrlObj);
        content.add(imagePart);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);

        body.put("messages", List.of(message));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        try {
            log.info("调用 OpenAI兼容视觉API: url={}, model={}, 图片大小约{}字符",
                    url, model, imageDataUrl.length());
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode choices = root.get("choices");
                if (choices != null && choices.isArray() && choices.size() > 0) {
                    JsonNode messageNode = choices.get(0).get("message");
                    if (messageNode != null) {
                        String contentText = messageNode.get("content").asText();
                        log.info("AI 视觉识别返回内容长度: {}", contentText.length());
                        return contentText;
                    }
                }
                throw new RuntimeException("AI 视觉API返回格式异常: " + response.getBody());
            }
            throw new RuntimeException("AI 视觉API调用失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("AI 视觉API调用失败", e);
            throw new RuntimeException("AI 视觉识别接口调用失败: " + e.getMessage());
        }
    }

    /**
     * Anthropic 兼容视觉 API（图片）
     */
    private String callAnthropicVisionAPI(String baseUrl, String apiKey, String model,
                                           String prompt, String imageDataUrl) {
        String url = baseUrl + "/v1/messages";

        // 解析 data:image/png;base64,... 格式
        String mediaType = "image/png";
        String base64Data = imageDataUrl;
        if (imageDataUrl.startsWith("data:")) {
            int colonIdx = imageDataUrl.indexOf(':') + 1;
            int semiIdx = imageDataUrl.indexOf(';');
            if (colonIdx > 0 && semiIdx > colonIdx) {
                mediaType = imageDataUrl.substring(colonIdx, semiIdx);
            }
            int commaIdx = imageDataUrl.indexOf(',');
            if (commaIdx > 0) {
                base64Data = imageDataUrl.substring(commaIdx + 1);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model != null ? model : "claude-3-5-sonnet-20241022");
        body.put("max_tokens", 4096);
        body.put("temperature", 0.1);

        // Anthropic 多模态消息格式
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));

        Map<String, Object> imageSource = new LinkedHashMap<>();
        imageSource.put("type", "base64");
        imageSource.put("media_type", mediaType);
        imageSource.put("data", base64Data);

        content.add(Map.of("type", "image", "source", imageSource));

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "user", "content", content));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            log.info("调用 Anthropic兼容视觉API: url={}, model={}, 图片mediaType={}",
                    url, model, mediaType);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode contentNode = root.get("content");
                if (contentNode != null && contentNode.isArray() && contentNode.size() > 0) {
                    String text = contentNode.get(0).get("text").asText();
                    log.info("Anthropic视觉API返回内容长度: {}", text.length());
                    return text;
                }
                throw new RuntimeException("Anthropic视觉API返回格式异常: " + response.getBody());
            }
            throw new RuntimeException("Anthropic视觉API调用失败: HTTP " + response.getStatusCode());
        } catch (Exception e) {
            log.error("Anthropic视觉API调用失败", e);
            throw new RuntimeException("Anthropic视觉API调用失败: " + e.getMessage());
        }
    }

    /**
     * 解析 AI 返回的图片表格数据为 ParseResult
     */
    private ExcelService.ParseResult parseImageTableResult(String aiResponse) {
        try {
            String json = extractJson(aiResponse);
            JsonNode root = objectMapper.readTree(json);

            // 解析表头
            List<String> headers = new ArrayList<>();
            JsonNode headersNode = root.get("headers");
            if (headersNode != null && headersNode.isArray()) {
                for (JsonNode h : headersNode) {
                    headers.add(h.asText());
                }
            }

            // 解析数据行
            JsonNode rowsNode = root.get("rows");
            if (rowsNode == null || !rowsNode.isArray() || rowsNode.size() == 0) {
                throw new RuntimeException("AI未从图片中识别到表格数据");
            }

            List<ReconRecord> records = new ArrayList<>();
            for (int i = 0; i < rowsNode.size(); i++) {
                JsonNode row = rowsNode.get(i);
                if (!row.isArray()) continue;

                Map<String, String> rawData = new LinkedHashMap<>();
                for (int j = 0; j < headers.size() && j < row.size(); j++) {
                    String val = row.get(j).asText();
                    if (val == null || "null".equals(val)) val = "";
                    rawData.put(headers.get(j), val);
                }

                ReconRecord record = ReconRecord.builder()
                        .index(i)
                        .rawData(rawData)
                        .amount(extractAmountFromRaw(rawData))
                        .date(extractDateFromRaw(rawData))
                        .description(extractDescFromRaw(rawData))
                        .build();
                records.add(record);
            }

            log.info("AI视觉识别完成: {}列表头, {}条记录", headers.size(), records.size());
            return new ExcelService.ParseResult(headers, records);
        } catch (Exception e) {
            log.error("解析AI视觉识别结果失败", e);
            throw new RuntimeException("AI图片识别结果解析失败: " + e.getMessage());
        }
    }

    private BigDecimal extractAmountFromRaw(Map<String, String> rawData) {
        String[] amountKeys = {"金额", "amount", "交易金额", "金额(元)", "收入", "支出"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), amountKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                try {
                    String v = entry.getValue().replace(",", "").replace("，", "")
                            .replace("¥", "").replace("$", "").replace("元", "").trim();
                    return new BigDecimal(v);
                } catch (Exception ignored) {}
            }
        }
        return BigDecimal.ZERO;
    }

    private String extractDateFromRaw(Map<String, String> rawData) {
        String[] dateKeys = {"日期", "date", "交易日期", "时间", "time"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), dateKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue().trim();
            }
        }
        return "";
    }

    private String extractDescFromRaw(Map<String, String> rawData) {
        String[] descKeys = {"描述", "desc", "摘要", "备注", "remark", "交易说明", "用途"};
        for (Map.Entry<String, String> entry : rawData.entrySet()) {
            if (containsAny(entry.getKey().toLowerCase(), descKeys)
                    && entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue().trim();
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
}
