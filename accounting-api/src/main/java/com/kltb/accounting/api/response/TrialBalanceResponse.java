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
public class TrialBalanceResponse {

    private LocalDate accountingDate;
    private boolean passed;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal diff;
    private List<SubjectDetail> subjectDetails;
    private List<SubjectDetail> imbalancedSubjects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectDetail {
        private String subjectCode;
        private String subjectName;
        private BigDecimal totalDebit;
        private BigDecimal totalCredit;
        private BigDecimal netDiff;
    }
}
