package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 缓冲记账执行响应 DTO
 */
@Data
public class BufferExecuteResponse {

    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Long durationMs;
    private List<BufferFailedItem> failedList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BufferFailedItem {
        private Long detailId;
        private String accountNo;
        private BigDecimal amount;
        private String failReason;
    }
}
