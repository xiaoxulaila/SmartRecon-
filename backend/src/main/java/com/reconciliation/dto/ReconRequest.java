package com.reconciliation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 对账请求 - 支持多列灵活匹配
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconRequest {
    /** 数据源A的ID */
    private String sourceDataId;
    /** 数据源B的ID */
    private String targetDataId;

    /** 匹配模式: ALL（全部列匹配成功才算匹配） / ANY（任一列匹配就算匹配） */
    private String matchMode;

    /** 需要匹配的列配置列表 */
    private List<ColumnMatch> matchColumns;

    // ===== 兼容旧版固定金额/日期匹配 =====
    private String sourceAmountColumn;
    private String targetAmountColumn;
    private String sourceDateColumn;
    private String targetDateColumn;
    private BigDecimal amountTolerance;
    private Integer dateToleranceDays;

    /**
     * 单列匹配配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnMatch {
        /** 数据源A的列名 */
        private String sourceColumn;
        /** 数据源B的列名 */
        private String targetColumn;
        /** 匹配类型: EXACT / NUMERIC_TOLERANCE / DATE_TOLERANCE / CONTAINS */
        private String matchType;
        /** 数值容差（matchType=NUMERIC_TOLERANCE时有效） */
        private BigDecimal numericTolerance;
        /** 日期容差天数（matchType=DATE_TOLERANCE时有效） */
        private Integer dateToleranceDays;
    }
}
