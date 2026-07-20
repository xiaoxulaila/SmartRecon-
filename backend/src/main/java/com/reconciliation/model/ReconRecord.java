package com.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 对账记录 - 单条交易/账目记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconRecord {
    /** 记录序号 */
    private int index;
    /** 金额 */
    private BigDecimal amount;
    /** 日期 */
    private String date;
    /** 描述/摘要 */
    private String description;
    /** 对方名称/交易对方 */
    private String counterparty;
    /** 原始行数据（保留所有列） */
    private Map<String, String> rawData;
}
