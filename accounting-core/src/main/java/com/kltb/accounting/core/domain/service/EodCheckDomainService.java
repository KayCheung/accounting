package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 日切前置检查领域服务
 * <p>
 * 在日终处理开始前，执行 5 项前置检查，确保所有中间态数据已处理完毕：
 * 1. 缓冲待入账明细：无 PENDING/PROCESSING 状态的缓冲明细
 * 2. 处理中的事务：无 PROCESSING 状态的事务
 * 3. 未过账凭证：无 PENDING/POSTING 状态的凭证
 * 4. 处理中的业务流水：无 PROCESSING 状态的业务流水
 * 5. 过期未解冻记录：无已过期的冻结记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodCheckDomainService {

    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final FreezeDetailRepository freezeDetailRepository;
    private final ExecutorService businessAsyncExecutor;

    /**
     * 执行日切前置检查（6 个 COUNT 查询并行执行）
     *
     * @param accountingDate 会计日期
     * @return 检查结果
     */
    public EodPreCheckResult checkEodPreconditions(LocalDate accountingDate) {
        CompletableFuture<Integer> fBufferPending = CompletableFuture.supplyAsync(
                () -> bufferPostingDetailRepository.countByAccountingDateAndStatus(
                        accountingDate, BufferStatusEnum.PENDING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fBufferProcessing = CompletableFuture.supplyAsync(
                () -> bufferPostingDetailRepository.countByAccountingDateAndStatus(
                        accountingDate, BufferStatusEnum.PROCESSING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fProcessingTxn = CompletableFuture.supplyAsync(
                () -> transactionRepository.countByAccountingDateAndStatus(
                        accountingDate, TransactionStatusEnum.PROCESSING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fUnpostedVoucher = CompletableFuture.supplyAsync(
                () -> voucherRepository.countByAccountingDateAndStatus(
                        accountingDate, VoucherStatusEnum.PENDING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fPostingVoucher = CompletableFuture.supplyAsync(
                () -> voucherRepository.countByAccountingDateAndStatus(
                        accountingDate, VoucherStatusEnum.POSTING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fProcessingJournal = CompletableFuture.supplyAsync(
                () -> businessRecordRepository.countByAccountingDateAndStatus(
                        accountingDate, BusinessRecordStatusEnum.PROCESSING.getCode()),
                businessAsyncExecutor);
        CompletableFuture<Integer> fExpiredFreeze = CompletableFuture.supplyAsync(
                freezeDetailRepository::countExpiredUnfrozen,
                businessAsyncExecutor);

        CompletableFuture.allOf(fBufferPending, fBufferProcessing, fProcessingTxn,
                fUnpostedVoucher, fPostingVoucher, fProcessingJournal, fExpiredFreeze).join();

        int bufferPending = fBufferPending.join();
        int bufferProcessing = fBufferProcessing.join();
        int totalBufferPending = bufferPending + bufferProcessing;
        int processingTxn = fProcessingTxn.join();
        int unpostedVoucher = fUnpostedVoucher.join();
        int postingVoucher = fPostingVoucher.join();
        int totalUnpostedVoucher = unpostedVoucher + postingVoucher;
        int processingJournal = fProcessingJournal.join();
        int expiredFreeze = fExpiredFreeze.join();

        boolean allPassed = totalBufferPending == 0 && processingTxn == 0
                && totalUnpostedVoucher == 0 && processingJournal == 0 && expiredFreeze == 0;

        if (!allPassed) {
            log.error("[EOD-PRECHECK-FAILED] date={}, bufferPending={}, processingTxn={}, "
                            + "unpostedVoucher={}, processingJournal={}, expiredFreeze={}",
                    accountingDate, totalBufferPending, processingTxn, totalUnpostedVoucher,
                    processingJournal, expiredFreeze);
        }

        return new EodPreCheckResult(
                accountingDate, allPassed, totalBufferPending, processingTxn,
                totalUnpostedVoucher, processingJournal, expiredFreeze);
    }

    /**
     * 执行日切前置检查，若不通过则抛出异常
     *
     * @param accountingDate 会计日期
     * @throws AccountException 当检查不通过时抛出
     */
    public void checkAndThrow(LocalDate accountingDate) {
        EodPreCheckResult result = checkEodPreconditions(accountingDate);
        if (!result.isAllPassed()) {
            throw new AccountException(ResultCode.EOD_PRECHECK_FAILED,
                    "日切前置检查失败: date=" + accountingDate
                            + ", bufferPending=" + result.getBufferPending()
                            + ", processingTxn=" + result.getProcessingTxn()
                            + ", unpostedVoucher=" + result.getUnpostedVoucher()
                            + ", processingJournal=" + result.getProcessingJournal()
                            + ", expiredFreeze=" + result.getExpiredFreeze());
        }
    }

    /**
     * 日切前置检查结果
     */
    @Data
    public static class EodPreCheckResult {
        private final LocalDate accountingDate;
        private final boolean allPassed;
        private final int bufferPending;
        private final int processingTxn;
        private final int unpostedVoucher;
        private final int processingJournal;
        private final int expiredFreeze;
    }
}
