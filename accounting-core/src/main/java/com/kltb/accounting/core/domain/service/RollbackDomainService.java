package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 回滚领域服务
 * <p>
 * 职责：实时路径失败回滚 + 单边记账回滚 + 重试判断
 * <p>
 * 是否记账：是（更新状态记录 / 生成反向凭证）
 * 异常处理：
 *   - [RuntimeException] → 独立事务包裹，不影响调用方
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollbackDomainService {

    private final TransactionTemplate transactionTemplate;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;

    /**
     * 实时过账失败回滚（事务外调用，使用独立事务更新状态）
     */
    public void markTransactionFailed(
        String txnNo,
        String voucherNo,
        String traceNo,
        String failReason) {

        log.error("[ROLLBACK] 标记事务失败: txnNo={}, voucherNo={}, traceNo={}, reason={}",
            txnNo, voucherNo, traceNo, failReason);

        transactionTemplate.execute(status -> {
            // 1. 更新 t_transaction 状态为失败
            if (txnNo != null) {
                transactionRepository.updateStatusByTxnNo(
                    txnNo, TransactionStatusEnum.FAILED, failReason, LocalDateTime.now());
            }

            // 2. 更新 t_business_record 状态为失败
            if (traceNo != null) {
                BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
                if (record != null) {
                    businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
                }
            }

            // 3. 更新 t_accounting_voucher 状态为过账失败
            if (voucherNo != null) {
                accountingVoucherRepository.updateStatusByVoucherNo(voucherNo, VoucherStatusEnum.FAILED.getCode());

                // 4. 更新所有分录状态为过账失败
                List<AccountingVoucherEntryPO> entries = accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo);
                for (AccountingVoucherEntryPO entry : entries) {
                    if (entry.getStatus() != VoucherEntryStatusEnum.POSTED) {
                        entry.setStatus(VoucherEntryStatusEnum.FAILED);
                        accountingVoucherRepository.updateEntryById(entry);
                    }
                }
            }

            return null;
        });
    }

    /**
     * 单边记账回滚（异步分录失败，实时分录已提交）
     * <p>
     * 生成反向凭证号（REV + 原 voucherNo），写入 t_accounting_voucher 一条真实记录。
     * 反向分录使用新 entryId（REV_ENTRY + 序号），避免唯一约束冲突。
     */
    public void executeRollbackForAsyncFailure(
        String voucherNo,
        String txnNo,
        String failReason) {

        log.warn("[ROLLBACK] 执行异步失败回滚: voucherNo={}, txnNo={}, reason={}",
            voucherNo, txnNo, failReason);

        // 1. 查询原凭证 + 所有已过账的实时分录
        AccountingVoucherPO origVoucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (origVoucher == null) {
            log.error("[ROLLBACK] 原凭证不存在: voucherNo={}", voucherNo);
            return;
        }

        List<AccountingVoucherEntryPO> postedEntries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo)
            .stream()
            .filter(e -> e.getStatus() == VoucherEntryStatusEnum.POSTED)
            .toList();

        if (postedEntries.isEmpty()) {
            log.info("[ROLLBACK] 无已过账分录，无需回滚: voucherNo={}", voucherNo);
            return;
        }

        // 2. 生成反向凭证号
        String reverseVoucherNo = "REV" + voucherNo;

        // 3. 独立事务执行回滚
        transactionTemplate.execute(status -> {
            // a. 写入反向凭证
            AccountingVoucherPO reverseVoucher = new AccountingVoucherPO();
            reverseVoucher.setVoucherNo(reverseVoucherNo)
                .setTxnNo(txnNo)
                .setTraceNo(origVoucher.getTraceNo())
                .setTraceSeq(origVoucher.getTraceSeq())
                .setVoucherType(origVoucher.getVoucherType())
                .setPostingType(origVoucher.getPostingType())
                .setBusinessCode(origVoucher.getBusinessCode())
                .setTradingCode(origVoucher.getTradingCode())
                .setPayChannel(origVoucher.getPayChannel())
                .setTradeType(origVoucher.getTradeType())
                .setTradeTime(origVoucher.getTradeTime())
                .setAmount(origVoucher.getAmount())
                .setStatus(VoucherStatusEnum.FAILED)
                .setPostTime(LocalDateTime.now())
                .setAccountingDate(origVoucher.getAccountingDate())
                .setSummary("异步回滚-原凭证:" + voucherNo)
                .setAttachmentCount(0)
                .setOrigVoucherNo(voucherNo);
            accountingVoucherRepository.insert(reverseVoucher);

            // b. 对每个已过账分录执行反向操作
            int seq = 0;
            for (AccountingVoucherEntryPO entry : postedEntries) {
                seq++;
                String reverseEntryId = "REV_ENTRY" + seq;

                // 查询账户并加锁
                AccountPO account = accountRepository.selectForUpdate(entry.getAccountNo());
                if (account == null) {
                    log.error("[ROLLBACK] 回滚账户不存在: accountNo={}", entry.getAccountNo());
                    continue;
                }

                // 查询子账户
                List<SubAccountPO> subs = subAccountRepository.selectForUpdate(entry.getAccountNo());
                SubAccountPO subAccount = subs != null && !subs.isEmpty() ? subs.get(0) : null;

                // 反向计算余额（changeDirection 取反）
                int reverseChangeDir = entry.getChangeDirection() == 1 ? 2 : 1;

                // 保存反向操作前的余额（即当前余额，作为 preBalance）
                BigDecimal reversePreBalance = account.getBalance();

                // 使用反向方向计算新余额
                BigDecimal newBalance = AccountBalanceCalculator.calculateNewBalance(
                    reversePreBalance, entry.getAmount(), reverseChangeDir);

                // 更新主账户余额
                account.setBalance(newBalance);
                account.setVersion(account.getVersion() != null ? account.getVersion() + 1 : 1);
                accountRepository.updateById(account);

                // 更新子账户余额
                BigDecimal subNewBalance = null;
                BigDecimal subReversePreBalance = null;
                if (subAccount != null) {
                    subReversePreBalance = subAccount.getBalance();
                    subNewBalance = AccountBalanceCalculator.calculateNewBalance(
                        subReversePreBalance, entry.getAmount(), reverseChangeDir);
                    subAccount.setBalance(subNewBalance);
                    subAccount.setVersion(subAccount.getVersion() != null ? subAccount.getVersion() + 1 : 1);
                    subAccountRepository.updateById(subAccount);
                }

                // 写入反向 t_account_detail
                AccountDetailPO reverseDetail = new AccountDetailPO();
                reverseDetail.setVoucherNo(reverseVoucherNo)
                    .setEntryId(reverseEntryId)
                    .setTxnNo(txnNo)
                    .setTraceNo(origVoucher.getTraceNo())
                    .setTraceSeq(origVoucher.getTraceSeq())
                    .setSubjectCode(entry.getSubjectCode())
                    .setAccountNo(entry.getAccountNo())
                    .setBusinessCode(origVoucher.getBusinessCode())
                    .setTradingCode(origVoucher.getTradingCode())
                    .setPayChannel(origVoucher.getPayChannel())
                    .setTradeType(origVoucher.getTradeType())
                    .setTradeTime(origVoucher.getTradeTime())
                    .setDebitCredit(entry.getDebitCredit())
                    .setChangeDirection(reverseChangeDir == 1
                        ? ChangeDirectionEnum.INCREASE
                        : ChangeDirectionEnum.DECREASE)
                    .setCurrency(entry.getCurrency())
                    .setPreBalance(reversePreBalance)
                    .setAmount(entry.getAmount())
                    .setPostBalance(newBalance)
                    .setAccountingDate(entry.getAccountingDate())
                    .setSummary("异步回滚-原entryId:" + entry.getEntryId());
                accountDetailRepository.insert(reverseDetail);

                // 写入反向 t_sub_account_detail
                if (subAccount != null && subNewBalance != null) {
                    SubAccountDetailPO reverseSubDetail = new SubAccountDetailPO();
                    reverseSubDetail.setVoucherNo(reverseVoucherNo)
                        .setEntryId(reverseEntryId)
                        .setTxnNo(txnNo)
                        .setTraceNo(origVoucher.getTraceNo())
                        .setTraceSeq(origVoucher.getTraceSeq())
                        .setAccountNo(entry.getAccountNo())
                        .setBalanceType(com.kltb.accounting.core.domain.enums.BalanceTypeEnum.AVAILABLE)
                        .setTradingCode(origVoucher.getTradingCode())
                        .setTradeType(origVoucher.getTradeType())
                        .setTradeTime(origVoucher.getTradeTime())
                        .setDebitCredit(entry.getDebitCredit())
                        .setChangeDirection(reverseChangeDir == 1
                            ? ChangeDirectionEnum.INCREASE
                            : ChangeDirectionEnum.DECREASE)
                        .setCurrency(entry.getCurrency())
                        .setPreBalance(subReversePreBalance)
                        .setAmount(entry.getAmount())
                        .setPostBalance(subNewBalance)
                        .setAccountingDate(entry.getAccountingDate())
                        .setSummary("异步回滚-原entryId:" + entry.getEntryId());
                    subAccountDetailRepository.insert(reverseSubDetail);
                }

                // 更新原分录状态为过账失败
                entry.setStatus(VoucherEntryStatusEnum.FAILED);
                accountingVoucherRepository.updateEntryById(entry);
            }

            // c. 更新原凭证状态
            origVoucher.setStatus(VoucherStatusEnum.FAILED);
            accountingVoucherRepository.updateById(origVoucher);

            // d. 更新事务状态
            if (txnNo != null) {
                transactionRepository.updateStatusByTxnNo(
                    txnNo, TransactionStatusEnum.FAILED, failReason, LocalDateTime.now());
            }

            return null;
        });
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable ex) {
        if (ex == null) return false;
        Class<? extends Throwable> type = ex.getClass();
        return org.springframework.dao.PessimisticLockingFailureException.class.isAssignableFrom(type)
            || java.util.concurrent.TimeoutException.class.isAssignableFrom(type)
            || org.springframework.dao.TransientDataAccessResourceException.class.isAssignableFrom(type);
    }
}
