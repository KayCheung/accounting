package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 过账统计报表响应 DTO
 */
@Data
public class PostingStatsResponse {

    private LocalDate startDate;
    private LocalDate endDate;
    private String dimension;
    private List<StatsGroupData> statsList;

    @Data
    public static class StatsGroupData {
        private String dimensionValue;
        private int totalCount;
        private int successCount;
        private int failedCount;
        private int processingCount;
        private int pendingCount;
        private BigDecimal successRate;
        private Long avgDurationMs;
        private Long maxDurationMs;
        private Long minDurationMs;
    }
}
