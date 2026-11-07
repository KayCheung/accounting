package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量过账结果响应 DTO
 */
@Data
public class BatchPostingResponse {

    private int totalCount;
    private int successCount;
    private int failedCount;
    private long totalDurationMs;
    private List<FailedVoucherInfo> failedList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedVoucherInfo {
        private String voucherNo;
        private String failReason;
    }
}
