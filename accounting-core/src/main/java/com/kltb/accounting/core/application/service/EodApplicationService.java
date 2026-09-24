package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.application.assembler.EodAssembler;
import com.kltb.accounting.core.domain.service.EodArchiveDomainService;
import com.kltb.accounting.core.domain.service.EodCheckDomainService;
import com.kltb.accounting.core.domain.service.EodCheckDomainService.EodPreCheckResult;
import com.kltb.accounting.core.domain.service.EodDomainService;
import com.kltb.accounting.core.domain.service.EodStatusDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.BalanceReconciliationResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.GlReconciliationResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EodApplicationService {

    private final EodCheckDomainService eodCheckDomainService;
    private final EodDomainService eodDomainService;
    private final TrialBalanceDomainService trialBalanceDomainService;
    private final PeriodEndTransferDomainService periodEndTransferDomainService;
    private final EodStatusDomainService eodStatusDomainService;
    private final EodArchiveDomainService eodArchiveDomainService;
    private final EodAssembler eodAssembler;

    public EodExecuteResponse executeEod(EodExecuteRequest request) {
        long startTime = System.currentTimeMillis();
        LocalDate accountingDate = request.getAccountingDate();

        try {
            // Step 0: 创建日切状态记录
            eodStatusDomainService.createStatus(accountingDate);

            // Step 1: 存量清理
            eodStatusDomainService.updateStage(3, accountingDate);
            EodPreCheckResult checkResult = null;
            boolean preCheckPassed = false;
            if (!request.isSkipPreCheck()) {
                checkResult = eodCheckDomainService.checkEodPreconditions(accountingDate);
                preCheckPassed = checkResult.isAllPassed();
                if (!preCheckPassed) {
                    eodStatusDomainService.markFailed(accountingDate, "CLEANUP", "前置检查失败");
                    return buildPreCheckFailureResponse(accountingDate, checkResult, startTime);
                }
            } else {
                preCheckPassed = true;
            }

            // Step 2: 余额计算 + upsert
            eodStatusDomainService.updateStage(4, accountingDate);
            List<AccountBalancePO> balances = eodDomainService.calculateDailyBalances(accountingDate);
            eodDomainService.upsertDailyBalances(accountingDate, balances);

            // Step 3: 快照生成
            int snapshotCount = eodDomainService.generateDailySnapshot(accountingDate);

            // Step 4: 借贷平衡
            eodStatusDomainService.updateStage(5, accountingDate);
            TrialBalanceResult trialResult = trialBalanceDomainService.executeTrialBalance(accountingDate);
            boolean trialBalancePassed = trialResult.isPassed();
            if (!trialBalancePassed) {
                eodStatusDomainService.markFailed(accountingDate, "TRIAL_BALANCE", "借贷不平衡");
                return buildTrialBalanceFailureResponse(accountingDate, preCheckPassed ? checkResult : null,
                        balances.size(), trialResult, startTime);
            }

            // Step 5: 总分核对（NEW）
            GlReconciliationResult glResult = trialBalanceDomainService.executeGlReconciliation(accountingDate);
            if (!glResult.isPassed()) {
                eodStatusDomainService.markFailed(accountingDate, "GL_RECONCILIATION", "总分核对失败");
                long duration = System.currentTimeMillis() - startTime;
                return buildGlReconciliationFailureResponse(accountingDate, checkResult,
                        balances.size(), trialResult, glResult, snapshotCount, duration);
            }

            // Step 6: 余额核对（NEW）
            BalanceReconciliationResult balResult = trialBalanceDomainService.executeBalanceReconciliation(accountingDate);
            if (!balResult.isPassed()) {
                eodStatusDomainService.markFailed(accountingDate, "BALANCE_RECONCILIATION", "余额核对失败");
                long duration = System.currentTimeMillis() - startTime;
                return buildBalanceReconciliationFailureResponse(accountingDate, checkResult,
                        balances.size(), trialResult, glResult, balResult, snapshotCount, duration);
            }

            // Step 7: 期末结转（可选）
            List<TransferRuleResult> transferResults = List.of();
            if (request.isExecuteTransfer() && trialBalancePassed) {
                eodStatusDomainService.updateStage(6, accountingDate);
                transferResults = periodEndTransferDomainService.executeTransferRules(accountingDate);
            }

            // Step 8: 归档（NEW）
            eodStatusDomainService.updateStage(7, accountingDate);
            long duration = System.currentTimeMillis() - startTime;
            eodArchiveDomainService.archive(accountingDate, duration);
            eodStatusDomainService.markCompleted(accountingDate, duration);

            log.info("[EOD-APP] 执行完成: preCheck={}, balanceCount={}, trialBalance={}, "
                            + "glReconciled=true, balanceReconciled=true, transferRules={}, snapshotCount={}, duration={}ms",
                    preCheckPassed, balances.size(), trialBalancePassed,
                    transferResults.size(), snapshotCount, duration);

            return EodExecuteResponse.builder()
                    .accountingDate(accountingDate)
                    .preCheckPassed(preCheckPassed)
                    .preCheckDetails(preCheckPassed && checkResult != null
                            ? eodAssembler.toPreCheckResponse(checkResult) : null)
                    .balanceCalculated(true)
                    .balanceCount(balances.size())
                    .trialBalancePassed(trialBalancePassed)
                    .trialBalanceDetails(eodAssembler.toTrialBalanceResponse(trialResult))
                    .transferResults(eodAssembler.toTransferRecords(transferResults))
                    .snapshotGenerated(snapshotCount > 0)
                    .snapshotCount(snapshotCount)
                    .totalDurationMs(duration)
                    .build();

        } catch (Exception e) {
            eodStatusDomainService.markFailed(accountingDate, "UNKNOWN", e.getMessage());
            throw e;
        }
    }

    public EodPreCheckResponse getPreCheckResult(LocalDate accountingDate) {
        EodPreCheckResult result = eodCheckDomainService.checkEodPreconditions(accountingDate);
        return eodAssembler.toPreCheckResponse(result);
    }

    public TrialBalanceResponse getTrialBalance(LocalDate accountingDate) {
        TrialBalanceResult result = trialBalanceDomainService.executeTrialBalance(accountingDate);
        return eodAssembler.toTrialBalanceResponse(result);
    }

    private EodExecuteResponse buildPreCheckFailureResponse(
            LocalDate accountingDate, EodPreCheckResult checkResult, long startTime) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(false)
                .preCheckDetails(eodAssembler.toPreCheckResponse(checkResult))
                .balanceCalculated(false)
                .balanceCount(0)
                .trialBalancePassed(false)
                .transferResults(List.of())
                .snapshotGenerated(false)
                .snapshotCount(0)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private EodExecuteResponse buildTrialBalanceFailureResponse(
            LocalDate accountingDate, EodPreCheckResult checkResult,
            int balanceCount, TrialBalanceResult trialResult, long startTime) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(checkResult != null)
                .preCheckDetails(checkResult != null ? eodAssembler.toPreCheckResponse(checkResult) : null)
                .balanceCalculated(true)
                .balanceCount(balanceCount)
                .trialBalancePassed(false)
                .trialBalanceDetails(eodAssembler.toTrialBalanceResponse(trialResult))
                .transferResults(List.of())
                .snapshotGenerated(false)
                .snapshotCount(0)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private EodExecuteResponse buildGlReconciliationFailureResponse(
            LocalDate accountingDate, EodPreCheckResult checkResult,
            int balanceCount, TrialBalanceResult trialResult,
            GlReconciliationResult glResult, int snapshotCount, long startTime) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(checkResult != null)
                .preCheckDetails(checkResult != null ? eodAssembler.toPreCheckResponse(checkResult) : null)
                .balanceCalculated(true)
                .balanceCount(balanceCount)
                .trialBalancePassed(trialResult.isPassed())
                .trialBalanceDetails(eodAssembler.toTrialBalanceResponse(trialResult))
                .transferResults(List.of())
                .snapshotGenerated(snapshotCount > 0)
                .snapshotCount(snapshotCount)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private EodExecuteResponse buildBalanceReconciliationFailureResponse(
            LocalDate accountingDate, EodPreCheckResult checkResult,
            int balanceCount, TrialBalanceResult trialResult,
            GlReconciliationResult glResult, BalanceReconciliationResult balResult,
            int snapshotCount, long startTime) {
        return EodExecuteResponse.builder()
                .accountingDate(accountingDate)
                .preCheckPassed(checkResult != null)
                .preCheckDetails(checkResult != null ? eodAssembler.toPreCheckResponse(checkResult) : null)
                .balanceCalculated(true)
                .balanceCount(balanceCount)
                .trialBalancePassed(trialResult.isPassed())
                .trialBalanceDetails(eodAssembler.toTrialBalanceResponse(trialResult))
                .transferResults(List.of())
                .snapshotGenerated(snapshotCount > 0)
                .snapshotCount(snapshotCount)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .build();
    }
}
