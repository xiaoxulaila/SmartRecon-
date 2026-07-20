package com.reconciliation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 对账结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconResult {
    /** 成功匹配的记录对 */
    private List<MatchedPair> matchedPairs;
    /** 仅存在于数据源A（未匹配） */
    private List<ReconRecord> onlyInSource;
    /** 仅存在于数据源B（未匹配） */
    private List<ReconRecord> onlyInTarget;
    /** 数据源A总记录数 */
    private int sourceCount;
    /** 数据源B总记录数 */
    private int targetCount;
    /** 匹配成功数 */
    private int matchedCount;
    /** 匹配率 */
    private double matchRate;

    /**
     * 匹配记录对
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchedPair {
        private ReconRecord source;
        private ReconRecord target;
        private boolean hasDifference;
        private String differenceNote;
    }
}
