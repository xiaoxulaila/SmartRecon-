package com.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 对账请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIReconRequest {
    /** 数据源A的ID */
    private String sourceDataId;
    /** 数据源B的ID */
    private String targetDataId;
    /** AI 模型名称 */
    private String model;
    /** API Token */
    private String token;
    /** API Base URL */
    private String baseUrl;
    /** AI 对账模式: rule(规则对账) / chat(对话对账) */
    private String aiMode;
    /** 自定义提示词（对话对账模式下使用） */
    private String customPrompt;
}
