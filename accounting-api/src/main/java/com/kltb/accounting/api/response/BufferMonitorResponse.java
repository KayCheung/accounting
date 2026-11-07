package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 缓冲监控响应 DTO
 */
@Data
public class BufferMonitorResponse {

    private Integer totalRecords;
    private List<BufferStatusCount> statusBreakdown;
    private List<FailedAccountInfo> failedTopAccounts;
    private List<BalanceAlertInfo> runningBalanceAlerts;

    @Data
    public static class BufferStatusCount {
        private Integer status;
        private String statusDesc;
        private Integer count;
        private BigDecimal amount;
    }

    @Data
    public static class FailedAccountInfo {
        private String accountNo;
        private Integer failedCount;
        private BigDecimal totalAmount;
    }

    @Data
    public static class BalanceAlertInfo {
        private String accountNo;
        private BigDecimal actualBalance;
        private BigDecimal calculatedBalance;
        private BigDecimal diff;
    }
}
