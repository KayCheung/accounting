package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EodExecuteResponse {

    private LocalDate accountingDate;
    private boolean preCheckPassed;
    private EodPreCheckResponse preCheckDetails;
    private boolean balanceCalculated;
    private int balanceCount;
    private boolean trialBalancePassed;
    private TrialBalanceResponse trialBalanceDetails;
    private List<TransferRecord> transferResults;
    private boolean snapshotGenerated;
    private int snapshotCount;
    private long totalDurationMs;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferRecord {
        private String ruleCode;
        private String ruleName;
        private String transferNo;
        private String voucherNo;
        private BigDecimal totalAmount;
        private int status;
    }
}
