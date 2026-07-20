package com.reconciliation.controller;

import com.reconciliation.dto.AIReconRequest;
import com.reconciliation.model.ReconResult;
import com.reconciliation.service.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 对账控制器
 */
@RestController
@RequestMapping("/api/ai")
public class AIController {

    private static final Logger log = LoggerFactory.getLogger(AIController.class);

    private final AIService aiService;
    private final RestTemplate restTemplate;

    public AIController(AIService aiService, RestTemplate restTemplate) {
        this.aiService = aiService;
        this.restTemplate = restTemplate;
    }

    /**
     * AI 智能对账
     */
    @PostMapping("/reconcile")
    public ReconResult aiReconcile(@RequestBody AIReconRequest request) {
        log.info("AI对账请求: source={}, target={}, model={}",
                request.getSourceDataId(), request.getTargetDataId(), request.getModel());

        if (request.getToken() == null || request.getToken().isBlank()) {
            throw new IllegalArgumentException("请先配置 AI Token");
        }
        if (request.getBaseUrl() == null || request.getBaseUrl().isBlank()) {
            throw new IllegalArgumentException("请先配置 AI API 地址");
        }
        if (request.getModel() == null || request.getModel().isBlank()) {
            throw new IllegalArgumentException("请先选择 AI 模型");
        }

        return aiService.runAIReconciliation(request);
    }

    /**
     * 测试 AI 连接（后端代理，避免浏览器 CORS 限制）
     */
    @PostMapping("/test")
    public Map<String, Object> testConnection(@RequestBody Map<String, String> body) {
        String baseUrl = body.getOrDefault("baseUrl", "").replaceAll("/+$", "");
        String token = body.getOrDefault("token", "");
        String model = body.getOrDefault("model", "");

        if (baseUrl.isBlank()) {
            return Map.of("success", false, "message", "API 地址不能为空");
        }
        if (token.isBlank()) {
            return Map.of("success", false, "message", "API Token 不能为空");
        }
        if (model.isBlank()) {
            return Map.of("success", false, "message", "AI 模型不能为空");
        }

        try {
            boolean isAnthropic = baseUrl.contains("anthropic");
            String url;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String requestBody;

            if (isAnthropic) {
                url = baseUrl + "/v1/messages";
                headers.set("x-api-key", token);
                headers.set("anthropic-version", "2023-06-01");
                requestBody = """
                    {"model":"%s","max_tokens":10,"messages":[{"role":"user","content":"回复ok"}]}"""
                    .formatted(model);
            } else {
                url = baseUrl + "/chat/completions";
                headers.setBearerAuth(token);
                requestBody = """
                    {"model":"%s","messages":[{"role":"user","content":"回复ok"}],"max_tokens":10}"""
                    .formatted(model);
            }

            log.info("测试AI连接: url={}, model={}, isAnthropic={}", url, model, isAnthropic);
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                return Map.of("success", true, "message", "连接成功！AI 服务可用 ✅");
            }
            return Map.of("success", false, "message",
                    "HTTP " + response.getStatusCodeValue() + " " + response.getBody());
        } catch (Exception e) {
            log.error("AI连接测试失败", e);
            String msg = e.getMessage();
            if (msg != null) {
                if (msg.contains("401")) msg = "Token 无效 (401)";
                else if (msg.contains("403")) msg = "API Key 无权限 (403)";
                else if (msg.contains("429")) msg = "请求过于频繁 (429)";
                else if (msg.length() > 80) msg = msg.substring(0, 80);
            }
            return Map.of("success", false, "message", msg != null ? msg : "连接失败");
        }
    }
}
