package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceResult;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.infrastructure.redis.RedisKeyConstants;
import com.kltb.accounting.core.shared.exception.AsyncRetryException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 回滚领域服务
 * <p>
 * 职责：整笔流水级失败回滚 + 单边记账回滚 + 重试判断
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
    private final FreezeDetailRepository freezeDetailRepository;
    private final FreezeDomainService freezeDomainService;
    private final LocalMessageService localMessageService;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final SubjectRepository subjectRepository;

    /**
     * 实时过账失败回滚（事务外调用，使用独立事务更新状态）
     */
    public void markTransactionFailed(
        String txnNo,
        String voucherNo,
        String traceNo,
        String failReason) {

        String safeFailReason = truncateFailReason(failReason);
        log.error("[ROLLBACK] 标记事务失败: txnNo={}, voucherNo={}, traceNo={}, reason={}",
            txnNo, voucherNo, traceNo, safeFailReason);

        transactionTemplate.execute(status -> {
            // 1. 更新 t_transaction 状态为失败
            if (txnNo != null) {
                transactionRepository.updateStatusByTxnNo(
                    txnNo, TransactionStatusEnum.FAILED, safeFailReason, LocalDateTime.now());
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
     * 整笔业务流水级回滚（支持未过账/部分过账/异常中断等全场景回滚与清理）
     *
     * @param traceNo 流水跟踪号
     * @param reason  回滚原因
     */
    public void executeFullJournalRollback(String traceNo, String reason) {
        String safeReason = truncateFailReason(reason);
        log.warn("[ROLLBACK] 执行整笔流水回滚: traceNo={}, reason={}", traceNo, safeReason);

        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            log.warn("[ROLLBACK] 未找到流水记录，跳过回滚: traceNo={}", traceNo);
            return;
        }

        if (record.getStatus() == BusinessRecordStatusEnum.SUCCESS) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "记账成功的流水禁止执行技术回滚，请使用凭证红冲操作: " + traceNo);
        }

        // 防重校验：若流水已是 FAILED 且不存在需要回滚的半成品数据，拦截重复回滚
        if (record.getStatus() == BusinessRecordStatusEnum.FAILED && !hasPendingRollbackData(traceNo, record)) {
            log.warn("[ROLLBACK] 流水已完成全额回滚清理，拒绝重复回滚: traceNo={}", traceNo);
            throw new ServiceException(ResultCode.PARAM_ERROR,
                    "该业务流水已完成全额回滚清理，无需重复回滚: " + traceNo);
        }

        String lockKey = RedisKeyConstants.Lock.Idempotent.trace(
                traceNo, record.getTraceSeq() != null ? record.getTraceSeq() : 1);

        distributedLockTemplate.execute(lockKey, 3, -1, () -> {
            transactionTemplate.execute(status -> {
                // 1. 查询关联事务与凭证列表
                TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
                String txnNo = (txn != null) ? txn.getTxnNo() : null;
                List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
                if ((vouchers == null || vouchers.isEmpty()) && txnNo != null) {
                    vouchers = accountingVoucherRepository.selectByTxnNo(txnNo);
                }

                List<String> allEntryIds = new ArrayList<>();
                List<String> allVoucherNos = new ArrayList<>();

                // 2. 处理凭证及分录
                if (vouchers != null && !vouchers.isEmpty()) {
                    for (AccountingVoucherPO voucher : vouchers) {
                        allVoucherNos.add(voucher.getVoucherNo());
                        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository
                                .selectEntriesByVoucherNo(voucher.getVoucherNo());

                        List<AccountingVoucherEntryPO> postedEntries = new ArrayList<>();
                        if (entries != null) {
                            for (AccountingVoucherEntryPO entry : entries) {
                                allEntryIds.add(entry.getEntryId());
                                if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
                                    postedEntries.add(entry);
                                } else {
                                    entry.setStatus(VoucherEntryStatusEnum.FAILED);
                                    accountingVoucherRepository.updateEntryById(entry);
                                }
                            }
                        }

                        // 对已过账分录执行反向冲回（若有）
                        if (!postedEntries.isEmpty()) {
                            rollbackPostedEntries(voucher, postedEntries, safeReason);
                        }

                        // 更新凭证状态为 FAILED
                        voucher.setStatus(VoucherStatusEnum.FAILED);
                        voucher.setFailReason(safeReason);
                        accountingVoucherRepository.updateById(voucher);
                    }
                }

                // 3. 逻辑删除凭证关联的辅助核算明细（防止报表统计残留）
                if (!allVoucherNos.isEmpty()) {
                    accountingVoucherRepository.deleteAuxiliaryByVoucherNos(allVoucherNos);
                }

                // 4. 作废待发送的本地消息（防止 MQ 消费端继续入账）
                if (!allEntryIds.isEmpty()) {
                    localMessageService.markFailedByBusinessKeys(allEntryIds, "流水回滚作废: " + safeReason);
                }

                // 5. 作废缓冲记账明细（防止 Job 批处理再次入账）
                bufferPostingDetailRepository.markFailedByTraceNo(traceNo, "流水回滚作废: " + safeReason);

                // 6. 释放关联预冻结资金（若关联了 origFreezeNo 且未全额扣减/解冻）
                if (StrUtil.isNotBlank(record.getOrigFreezeNo())) {
                    tryReleaseFreeze(record.getOrigFreezeNo(), safeReason);
                }

                // 6. 更新事务状态为 FAILED
                if (txnNo != null) {
                    transactionRepository.updateStatusByTxnNo(
                            txnNo, TransactionStatusEnum.FAILED, safeReason, LocalDateTime.now());
                }

                // 7. 更新流水状态为 FAILED
                businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);

                log.info("[ROLLBACK] 整笔流水回滚完成: traceNo={}, txnNo={}", traceNo, txnNo);
                return null;
            });
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

        // 2. 独立事务执行回滚
        transactionTemplate.execute(status -> {
            if (!postedEntries.isEmpty()) {
                rollbackPostedEntries(origVoucher, new ArrayList<>(postedEntries), failReason);
            }

            // c. 更新原凭证状态
            origVoucher.setStatus(VoucherStatusEnum.FAILED);
            accountingVoucherRepository.updateById(origVoucher);

            // 清理辅助核算明细
            accountingVoucherRepository.deleteAuxiliaryByVoucherNos(List.of(voucherNo));

            // d. 更新事务状态
            if (txnNo != null) {
                transactionRepository.updateStatusByTxnNo(
                    txnNo, TransactionStatusEnum.FAILED, truncateFailReason(failReason), LocalDateTime.now());
            }

            // e. 联动更新业务流水状态为 FAILED
            if (origVoucher.getTraceNo() != null) {
                businessRecordRepository.updateStatusByTraceNo(
                    origVoucher.getTraceNo(), BusinessRecordStatusEnum.FAILED);
            }

            return null;
        });
    }

    /**
     * 执行已过账分录的余额冲回与反向凭证生成
     */
    private void rollbackPostedEntries(
            AccountingVoucherPO origVoucher,
            List<AccountingVoucherEntryPO> postedEntries,
            String failReason) {
        String voucherNo = origVoucher.getVoucherNo();
        String reverseVoucherNo = "REV" + voucherNo;

        // 写入反向凭证记录
        AccountingVoucherPO reverseVoucher = new AccountingVoucherPO();
        reverseVoucher.setVoucherNo(reverseVoucherNo)
            .setTxnNo(origVoucher.getTxnNo())
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
            .setSummary("回滚-原凭证:" + voucherNo)
            .setAttachmentCount(0)
            .setOrigVoucherNo(voucherNo);
        accountingVoucherRepository.insert(reverseVoucher);

        // 遵循并发控制规范：多账户加锁必须 account_no 升序
        postedEntries.sort(Comparator.comparing(AccountingVoucherEntryPO::getAccountNo));

        int seq = 0;
        for (AccountingVoucherEntryPO entry : postedEntries) {
            seq++;
            String reverseEntryId = "REV_ENTRY" + seq;

            // 查询账户并加行锁
            AccountPO account = accountRepository.selectForUpdate(entry.getAccountNo());
            if (account == null) {
                log.error("[ROLLBACK] 回滚账户不存在: accountNo={}", entry.getAccountNo());
                continue;
            }

            // 查询子账户（精准获取可用余额子账户）
            List<SubAccountPO> subs = subAccountRepository.selectForUpdate(entry.getAccountNo());
            SubAccountPO subAccount = (subs != null)
                ? subs.stream().filter(s -> BalanceTypeEnum.AVAILABLE.equals(s.getBalanceType())).findFirst().orElse(null)
                : null;

            // 确定反向分录借贷方向（借贷对调）与增减方向取反
            DebitCreditEnum origDc = entry.getDebitCredit();
            DebitCreditEnum reverseDc = (origDc == DebitCreditEnum.DEBIT) ? DebitCreditEnum.CREDIT : DebitCreditEnum.DEBIT;
            ChangeDirectionEnum reverseChangeDir = (entry.getChangeDirection() == ChangeDirectionEnum.INCREASE)
                    ? ChangeDirectionEnum.DECREASE
                    : ChangeDirectionEnum.INCREASE;

            // 获取科目配置（优先从科目代码查，为空从账户方向兜底）
            String effectiveSubjectCode = StrUtil.isNotBlank(entry.getSubjectCode())
                    ? entry.getSubjectCode()
                    : account.getSubjectCode();
            SubjectBalanceDirectionEnum subjectDir = null;
            if (StrUtil.isNotBlank(effectiveSubjectCode)) {
                AccountSubjectPO subj = subjectRepository.selectByCode(effectiveSubjectCode);
                if (subj != null) {
                    subjectDir = subj.getDebitCredit();
                }
            }
            if (subjectDir == null) {
                subjectDir = (account.getBalanceDirection() == BalanceDirectionEnum.CREDIT)
                        ? SubjectBalanceDirectionEnum.CREDIT
                        : (account.getBalanceDirection() == BalanceDirectionEnum.FLAT ? SubjectBalanceDirectionEnum.NONE : SubjectBalanceDirectionEnum.DEBIT);
            }

            BigDecimal reversePreBalance = account.getBalance();
            AccountBalanceResult mainResult = AccountBalanceCalculator.calculate(
                    reversePreBalance,
                    account.getBalanceDirection(),
                    subjectDir,
                    reverseDc,
                    reverseChangeDir,
                    entry.getAmount()
            );
            BigDecimal newBalance = mainResult.getNewBalance();

            // 更新主账户余额与方向
            account.setBalance(newBalance);
            account.setBalanceDirection(mainResult.getNewDirection());
            accountRepository.updateById(account);

            // 更新子账户余额与方向
            BigDecimal subNewBalance = null;
            BigDecimal subReversePreBalance = null;
            if (subAccount != null) {
                subReversePreBalance = subAccount.getBalance();
                BalanceDirectionEnum subDir = subAccount.getBalanceDirection() != null
                        ? subAccount.getBalanceDirection()
                        : account.getBalanceDirection();
                AccountBalanceResult subResult = AccountBalanceCalculator.calculate(
                        subReversePreBalance,
                        subDir,
                        subjectDir,
                        reverseDc,
                        reverseChangeDir,
                        entry.getAmount()
                );
                subNewBalance = subResult.getNewBalance();
                subAccount.setBalance(subNewBalance);
                subAccount.setBalanceDirection(subResult.getNewDirection());
                subAccountRepository.updateById(subAccount);
            }

            // 写入反向 t_account_detail
            AccountDetailPO reverseDetail = new AccountDetailPO();
            reverseDetail.setVoucherNo(reverseVoucherNo)
                .setEntryId(reverseEntryId)
                .setTxnNo(origVoucher.getTxnNo())
                .setTraceNo(origVoucher.getTraceNo())
                .setTraceSeq(origVoucher.getTraceSeq())
                .setSubjectCode(account.getSubjectCode())
                .setAccountNo(entry.getAccountNo())
                .setBusinessCode(origVoucher.getBusinessCode())
                .setTradingCode(origVoucher.getTradingCode())
                .setPayChannel(origVoucher.getPayChannel())
                .setTradeType(origVoucher.getTradeType())
                .setTradeTime(origVoucher.getTradeTime())
                .setDebitCredit(reverseDc)
                .setChangeDirection(mainResult.getChangeDirection())
                .setCurrency(entry.getCurrency())
                .setPreBalance(reversePreBalance)
                .setAmount(entry.getAmount())
                .setPostBalance(newBalance)
                .setAccountingDate(entry.getAccountingDate())
                .setSummary("回滚-原entryId:" + entry.getEntryId());
            accountDetailRepository.insert(reverseDetail);

            // 写入反向 t_sub_account_detail
            if (subAccount != null && subNewBalance != null) {
                SubAccountDetailPO reverseSubDetail = new SubAccountDetailPO();
                reverseSubDetail.setVoucherNo(reverseVoucherNo)
                    .setEntryId(reverseEntryId)
                    .setTxnNo(origVoucher.getTxnNo())
                    .setTraceNo(origVoucher.getTraceNo())
                    .setTraceSeq(origVoucher.getTraceSeq())
                    .setAccountNo(entry.getAccountNo())
                    .setBalanceType(subAccount.getBalanceType() != null ? subAccount.getBalanceType() : BalanceTypeEnum.AVAILABLE)
                    .setTradingCode(origVoucher.getTradingCode())
                    .setTradeType(origVoucher.getTradeType())
                    .setTradeTime(origVoucher.getTradeTime())
                    .setDebitCredit(reverseDc)
                    .setChangeDirection(mainResult.getChangeDirection())
                    .setCurrency(entry.getCurrency())
                    .setPreBalance(subReversePreBalance)
                    .setAmount(entry.getAmount())
                    .setPostBalance(subNewBalance)
                    .setAccountingDate(entry.getAccountingDate())
                    .setSummary("回滚-原entryId:" + entry.getEntryId());
                subAccountDetailRepository.insert(reverseSubDetail);
            }

            // 更新原分录状态为过账失败
            entry.setStatus(VoucherEntryStatusEnum.FAILED);
            accountingVoucherRepository.updateEntryById(entry);
        }
    }

    /**
     * 尝试安全释放关联预冻结资金
     */
    private void tryReleaseFreeze(String freezeNo, String reason) {
        try {
            AccountFreezeDetailPO freezeDetail = freezeDetailRepository.selectByVoucherNo(freezeNo);
            if (freezeDetail != null && freezeDetail.getStatus() == FreezeStatusEnum.FROZEN) {
                BigDecimal remaining = freezeDetail.getFreezeAmount();
                if (remaining != null && remaining.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("[ROLLBACK] 释放关联预冻结资金: freezeNo={}, amount={}", freezeNo, remaining);
                    freezeDomainService.unfreezeFund(freezeNo, remaining, "流水异常回滚释放资金: " + reason);
                }
            }
        } catch (Exception e) {
            log.error("[ROLLBACK] 释放预冻结资金异常（不阻断流水回滚）: freezeNo={}", freezeNo, e);
        }
    }

    /**
     * 判断异常是否可重试
     * <p>
     * 支持重试的异常类型包括：
     * 1. 显式声明的异步重试异常 (AsyncRetryException)
     * 2. 数据库行锁/悲观锁并发冲突 (PessimisticLockingFailureException)
     * 3. 各种超时异常 (TimeoutException)
     * 4. 数据库瞬态资源异常 (TransientDataAccessResourceException)
     * <p>
     * 支持递归穿透 cause 检查，避免框架层包装异常（如 TransactionSystemException、UndeclaredThrowableException）掩盖底层重试标志。
     */
    public boolean isRetryable(Throwable ex) {
        if (ex == null) {
            return false;
        }
        Throwable current = ex;
        while (current != null) {
            Class<? extends Throwable> type = current.getClass();
            if (AsyncRetryException.class.isAssignableFrom(type)
                || PessimisticLockingFailureException.class.isAssignableFrom(type)
                || TimeoutException.class.isAssignableFrom(type)
                || TransientDataAccessResourceException.class.isAssignableFrom(type)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 判断指定流水是否仍存在待回滚/待清理的半成品数据
     *
     * @param traceNo 流水跟踪号
     * @param record  流水记录
     * @return true-存在需要回滚清理的数据; false-无待清理数据（已完全回滚或为空）
     */
    public boolean hasPendingRollbackData(String traceNo, BusinessRecordPO record) {
        if (record == null) {
            record = businessRecordRepository.selectByTraceNo(traceNo);
        }
        if (record == null) {
            return false;
        }

        // 1. 若流水处于处理中，必定需要回滚清理
        if (record.getStatus() == BusinessRecordStatusEnum.PROCESSING) {
            return true;
        }

        // 2. 检查关联凭证与分录
        TransactionPO txn = transactionRepository.selectByTraceNo(traceNo);
        String txnNo = (txn != null) ? txn.getTxnNo() : null;
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
        if ((vouchers == null || vouchers.isEmpty()) && txnNo != null) {
            vouchers = accountingVoucherRepository.selectByTxnNo(txnNo);
        }

        if (vouchers != null && !vouchers.isEmpty()) {
            for (AccountingVoucherPO v : vouchers) {
                // 凭证未处于 FAILED 或 REVERSED，需要回滚清理
                if (v.getStatus() != VoucherStatusEnum.FAILED && v.getStatus() != VoucherStatusEnum.REVERSED) {
                    return true;
                }
                // 检查分录中是否有 POSTED 或 PENDING 分录
                List<AccountingVoucherEntryPO> entries = accountingVoucherRepository.selectEntriesByVoucherNo(v.getVoucherNo());
                if (entries != null) {
                    for (AccountingVoucherEntryPO entry : entries) {
                        if (entry.getStatus() == VoucherEntryStatusEnum.POSTED || entry.getStatus() == VoucherEntryStatusEnum.PENDING) {
                            return true;
                        }
                    }
                }
                // 检查是否存在未删除的辅助核算明细
                List<AccountingVoucherAuxiliaryPO> auxList = accountingVoucherRepository.selectAuxiliaryByVoucherNo(v.getVoucherNo());
                if (auxList != null && !auxList.isEmpty()) {
                    return true;
                }
            }
        }

        // 3. 检查关联预冻结资金是否仍处于冻结状态
        if (StrUtil.isNotBlank(record.getOrigFreezeNo())) {
            AccountFreezeDetailPO freezeDetail = freezeDetailRepository.selectByVoucherNo(record.getOrigFreezeNo());
            if (freezeDetail != null && freezeDetail.getStatus() == FreezeStatusEnum.FROZEN) {
                return true;
            }
        }

        // 4. 检查事务状态是否未处于 FAILED
        if (txn != null && txn.getStatus() != TransactionStatusEnum.FAILED) {
            return true;
        }

        return false;
    }

    /**
     * 安全截断失败原因，防止超长引起 MySQL DataTruncation 异常
     */
    private String truncateFailReason(String reason) {
        if (reason == null) {
            return "";
        }
        return reason.length() > 250 ? reason.substring(0, 247) + "..." : reason;
    }
}
