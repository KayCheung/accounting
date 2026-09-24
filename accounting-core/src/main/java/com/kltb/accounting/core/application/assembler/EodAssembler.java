package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.domain.service.EodCheckDomainService.EodPreCheckResult;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class EodAssembler {

    public EodPreCheckResponse toPreCheckResponse(EodPreCheckResult result) {
        return EodPreCheckResponse.builder()
                .accountingDate(result.getAccountingDate())
                .allPassed(result.isAllPassed())
                .checks(List.of(
                        EodPreCheckResponse.CheckItem.builder()
                                .name("bufferPending").count(result.getBufferPending())
                                .passed(result.getBufferPending() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("processingTxn").count(result.getProcessingTxn())
                                .passed(result.getProcessingTxn() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("unpostedVoucher").count(result.getUnpostedVoucher())
                                .passed(result.getUnpostedVoucher() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("processingJournal").count(result.getProcessingJournal())
                                .passed(result.getProcessingJournal() == 0).build(),
                        EodPreCheckResponse.CheckItem.builder()
                                .name("expiredFreeze").count(result.getExpiredFreeze())
                                .passed(result.getExpiredFreeze() == 0).build()
                ))
                .build();
    }

    public TrialBalanceResponse toTrialBalanceResponse(TrialBalanceResult result) {
        return TrialBalanceResponse.builder()
                .accountingDate(result.getAccountingDate())
                .passed(result.isPassed())
                .totalDebit(result.getTotalDebit())
                .totalCredit(result.getTotalCredit())
                .diff(result.getDiff())
                .subjectDetails(result.getSubjectDetails().stream()
                        .map(d -> TrialBalanceResponse.SubjectDetail.builder()
                                .subjectCode(d.getSubjectCode())
                                .subjectName(d.getSubjectName())
                                .totalDebit(d.getTotalDebit())
                                .totalCredit(d.getTotalCredit())
                                .netDiff(d.getNetDiff())
                                .build())
                        .toList())
                .imbalancedSubjects(result.getImbalancedSubjects().stream()
                        .map(d -> TrialBalanceResponse.SubjectDetail.builder()
                                .subjectCode(d.getSubjectCode())
                                .subjectName(d.getSubjectName())
                                .totalDebit(d.getTotalDebit())
                                .totalCredit(d.getTotalCredit())
                                .netDiff(d.getNetDiff())
                                .build())
                        .toList())
                .build();
    }

    public List<EodExecuteResponse.TransferRecord> toTransferRecords(
            List<TransferRuleResult> results) {
        return results.stream()
                .map(r -> EodExecuteResponse.TransferRecord.builder()
                        .ruleCode(r.getRuleCode())
                        .ruleName(r.getRuleName())
                        .transferNo(r.getTransferNo())
                        .voucherNo(r.getVoucherNo())
                        .totalAmount(r.getTotalAmount())
                        .status(Optional.ofNullable(r.getStatus()).map(TransferRecordStatusEnum::getCode).orElse(null))
                        .build())
                .toList();
    }
}
