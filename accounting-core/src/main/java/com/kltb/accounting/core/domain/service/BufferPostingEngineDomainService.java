package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.infrastructure.redis.RedisKeyConstants;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 缓冲记账引擎领域服务
 * <p>
 * 职责：将 Step 10 生成的缓冲明细（t_buffer_posting_detail，status=待入账）
 * 按不同缓冲模式执行真实过账：逐条（mode=1）、日间批量（mode=2）、日终批量（mode=3）。
 * <p>
 * 核心原则：缓冲记账是延迟过账机制，与 Step 12 实时过账共享同一套余额计算逻辑，
 * 但执行时机不同。缓冲记账不生成新凭证（凭证已在 Step 10 生成），仅执行余额更新 + 状态联动。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BufferPostingEngineDomainService {

    private static final int MAX_OPTIMISTIC_RETRIES = 3;

    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RunningBalanceValidator runningBalanceValidator;

    /**
     * 逐条缓冲记账（buffer_mode=1）
     */
    public BatchPostingResult executeSinglePosting(LocalDate accountingDate, int maxBatchSize) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, BufferModeEnum.ASYNC_SINGLE.getCode(),
                BufferStatusEnum.PENDING.getCode(), maxBatchSize);

        return processDetails(details);
    }

    /**
     * 日间批量缓冲记账（buffer_mode=2）
     */
    public BatchPostingResult executeBatchPosting(LocalDate accountingDate, int maxBatchSize) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, BufferModeEnum.DAILY_BATCH.getCode(),
                BufferStatusEnum.PENDING.getCode(), maxBatchSize);

        return executeBatchByAccount(details, accountingDate);
    }

    /**
     * 分片扫描缓冲记账（用于 Job 分片执行）
     * <p>
     * P1-1 修复：增加 bufferMode 参数过滤。
     */
    public BatchPostingResult executeShardedPosting(
            LocalDate accountingDate, Integer bufferMode, Long shardingStart, Long shardingEnd, int maxBatchSize) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectByShardingRange(
                shardingStart, shardingEnd, accountingDate,
                BufferStatusEnum.PENDING.getCode(), maxBatchSize);

        if (bufferMode != null) {
            details = details.stream()
                    .filter(d -> d.getBufferMode() != null && d.getBufferMode().equals(bufferMode))
                    .collect(Collectors.toList());
        }

        return processDetails(details);
    }

    /**
     * 日终批量缓冲记账（buffer_mode=3）
     * <p>
     * P0-1 修复：新增 mode=3 专属入口，按账户汇总过账后触发 Running Balance 校验。
     */
    public BatchPostingResult executeEodPosting(LocalDate accountingDate, int maxBatchSize) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, BufferModeEnum.EOD_BATCH.getCode(),
                BufferStatusEnum.PENDING.getCode(), maxBatchSize);

        BatchPostingResult result = executeBatchByAccount(details, accountingDate);

        // 日终批量后触发 Running Balance 校验
        Set<String> accountNos = details.stream()
                .map(BufferPostingDetailPO::getAccountNo)
                .collect(Collectors.toSet());
        for (String accountNo : accountNos) {
            RunningBalanceValidator.ValidationResult vr =
                    runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);
            if (vr.isAlert()) {
                log.error("[BUFFER-POSTING-EOD] Running Balance 告警: accountNo={}, actual={}, calculated={}, diff={}",
                        accountNo, vr.getActualBalance(), vr.getCalculatedBalance(), vr.getDiff());
            }
        }

        return result;
    }

    /**
     * 按账户分组执行批量过账（mode=2 和 mode=3 共用）
     */
    private BatchPostingResult executeBatchByAccount(List<BufferPostingDetailPO> details, LocalDate accountingDate) {
        Map<String, List<BufferPostingDetailPO>> groupedByAccount = details.stream()
                .collect(Collectors.groupingBy(BufferPostingDetailPO::getAccountNo,
                        TreeMap::new, Collectors.toList()));

        long startTime = System.currentTimeMillis();
        BatchPostingResult result = new BatchPostingResult();
        result.setTotalCount(details.size());
        List<FailedItemInfo> failedList = new ArrayList<>();

        for (Map.Entry<String, List<BufferPostingDetailPO>> entry : groupedByAccount.entrySet()) {
            String accountNo = entry.getKey();
            List<BufferPostingDetailPO> accountDetails = entry.getValue();
            try {
                processAccountSummary(accountNo, accountingDate, accountDetails);
                result.setSuccessCount(result.getSuccessCount() + accountDetails.size());
            } catch (Exception e) {
                result.setFailedCount(result.getFailedCount() + accountDetails.size());
                for (BufferPostingDetailPO detail : accountDetails) {
                    failedList.add(new FailedItemInfo(detail.getId(), accountNo,
                            detail.getAmount(), e.getMessage()));
                }
                log.error("[BUFFER-POSTING] 账户批量过账失败: accountNo={}, reason={}",
                        accountNo, e.getMessage(), e);
            }
        }

        result.setFailedList(failedList);
        result.setTotalDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 处理缓冲明细列表（逐条模式）
     * <p>
     * P0-3 修复：使用 elapsed time 而非 System.currentTimeMillis()。
     */
    private BatchPostingResult processDetails(List<BufferPostingDetailPO> details) {
        BatchPostingResult result = new BatchPostingResult();
        result.setTotalCount(details.size());
        List<FailedItemInfo> failedList = new ArrayList<>();

        long startTime = System.currentTimeMillis();
        for (BufferPostingDetailPO detail : details) {
            try {
                processSingleDetail(detail);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                result.setFailedCount(result.getFailedCount() + 1);
                failedList.add(new FailedItemInfo(detail.getId(), detail.getAccountNo(),
                        detail.getAmount(), e.getMessage()));
                log.warn("[BUFFER-POSTING] 单笔过账失败: detailId={}, accountNo={}, reason={}",
                        detail.getId(), detail.getAccountNo(), e.getMessage());
            }
        }

        result.setFailedList(failedList);
        result.setTotalDurationMs(System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 单笔缓冲明细过账（核心逻辑）
     */
    public void processSingleDetail(BufferPostingDetailPO detail) {
        // 幂等检查
        if (detail.getStatus() != BufferStatusEnum.PENDING) {
            log.info("[BUFFER-POSTING] 跳过非待入账明细: detailId={}, status={}",
                    detail.getId(), detail.getStatus());
            return;
        }

        String lockKey = buildLockKey(detail.getAccountNo());

        distributedLockTemplate.execute(lockKey, 3, 30, () -> {
            executePostingWithLockUpgrade(detail);
            return null;
        });
    }

    /**
     * 按账户汇总过账（buffer_mode=2/3 专用）
     */
    public void processAccountSummary(
            String accountNo, LocalDate accountingDate, List<BufferPostingDetailPO> details) {

        if (details == null || details.isEmpty()) {
            return;
        }

        String lockKey = buildLockKey(accountNo);

        distributedLockTemplate.execute(lockKey, 3, 30, () -> {
            executeBatchPostingForAccount(accountNo, accountingDate, details);
            return null;
        });
    }

    /**
     * 带锁升级的过账执行
     * <p>
     * P2-5 修复：retryCount 已达上限时直接标记 FAILED，不进入重试循环。
     */
    private void executePostingWithLockUpgrade(BufferPostingDetailPO detail) {
        int retryCount = detail.getRetryCount() != null ? detail.getRetryCount() : 0;

        // P2-5: 重试次数已耗尽，直接标记 FAILED
        if (retryCount >= MAX_OPTIMISTIC_RETRIES) {
            String failReason = "重试次数已达上限(" + MAX_OPTIMISTIC_RETRIES + ")";
            log.error("[BUFFER-POSTING-RETRY-EXHAUSTED] detailId={}, accountNo={}, reason={}",
                    detail.getId(), detail.getAccountNo(), failReason);
            bufferPostingDetailRepository.updateToFailed(detail.getId(), failReason);
            throw new AccountException(ResultCode.BUFFER_POSTING_RETRY_EXHAUSTED, failReason);
        }

        // 乐观锁重试阶段
        for (int i = retryCount; i < MAX_OPTIMISTIC_RETRIES; i++) {
            try {
                executePostingInTransaction(detail, false);
                return;
            } catch (AccountException e) {
                if (e.getResultCode() == ResultCode.OPTIMISTIC_LOCK_FAILED) {
                    log.warn("[BUFFER-POSTING] 乐观锁冲突，重试: detailId={}, retry={}",
                            detail.getId(), i + 1);
                    continue;
                }
                throw e;
            }
        }

        // 乐观锁重试耗尽 → 升级为悲观锁
        log.warn("[BUFFER-POSTING] 乐观锁重试耗尽，升级为悲观锁: detailId={}", detail.getId());

        try {
            executePostingInTransaction(detail, true);
        } catch (Exception e) {
            String failReason = "锁升级后仍失败: " + e.getMessage();
            log.error("[BUFFER-POSTING-LOCK-UPGRADE-FAILED] detailId={}, accountNo={}, reason={}",
                    detail.getId(), detail.getAccountNo(), failReason);
            bufferPostingDetailRepository.updateToFailed(detail.getId(), failReason);
            throw new AccountException(ResultCode.BUFFER_POSTING_LOCK_UPGRADE_FAILED, failReason);
        }
    }

    /**
     * 在事务中执行过账
     * <p>
     * P1-3 修复：去掉了 updateToProcessing 调用，直接执行业务逻辑。
     * P1-4 修复：findEntryByVoucherAndEntryId 改为直接查询。
     */
    private void executePostingInTransaction(BufferPostingDetailPO detail, boolean usePessimistic) {
        transactionTemplate.execute(status -> {
            try {
                // 1. 查询主账户与子账户
                AccountPO account = getAccount(detail.getAccountNo(), usePessimistic);
                if (account == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "主账户不存在: accountNo=" + detail.getAccountNo());
                }
                SubAccountPO subAccount = getSubAccount(detail.getAccountNo(), usePessimistic);
                if (subAccount == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "子账户不存在: accountNo=" + detail.getAccountNo());
                }

                // 2. 余额计算
                BigDecimal oldAccountBalance = account.getBalance();
                BigDecimal oldSubBalance = subAccount.getBalance();
                BalanceDirectionEnum balanceDirection = subAccount.getBalanceDirection();
                DebitCreditEnum debitCredit = detail.getDebitCredit();
                BigDecimal amount = detail.getAmount();

                int changeDirection = calculateChangeDirection(balanceDirection, debitCredit);
                ChangeDirectionEnum changeDirectionEnum = (changeDirection == 1
                        ? ChangeDirectionEnum.INCREASE
                        : ChangeDirectionEnum.DECREASE);

                // 3. 计算新余额（遵循绝对值法则）
                BigDecimal newAccountBalance = AccountBalanceCalculator.calculateNewBalance(
                        oldAccountBalance, amount, changeDirection);
                BigDecimal newSubBalance = AccountBalanceCalculator.calculateNewBalance(
                        oldSubBalance, amount, changeDirection);

                // 4. 更新主账户与子账户余额
                account.setBalance(newAccountBalance);
                accountRepository.updateById(account);

                subAccount.setBalance(newSubBalance);
                subAccountRepository.updateById(subAccount);

                // 5. 写入主账户明细流水（t_account_detail）
                AccountDetailPO accountDetail = new AccountDetailPO();
                accountDetail.setVoucherNo(detail.getVoucherNo())
                        .setEntryId(detail.getEntryId())
                        .setTxnNo(StrUtil.isNotBlank(detail.getTxnNo()) ? detail.getTxnNo() : "")
                        .setTraceNo(StrUtil.isNotBlank(detail.getTraceNo()) ? detail.getTraceNo() : detail.getVoucherNo())
                        .setTraceSeq(detail.getTraceSeq() != null ? detail.getTraceSeq() : 0)
                        .setSubjectCode(account.getSubjectCode())
                        .setAccountNo(detail.getAccountNo())
                        .setBusinessCode(StrUtil.isNotBlank(detail.getBusinessCode()) ? detail.getBusinessCode() : "")
                        .setTradingCode(StrUtil.isNotBlank(detail.getTradingCode()) ? detail.getTradingCode() : "")
                        .setPayChannel(StrUtil.isNotBlank(detail.getPayChannel()) ? detail.getPayChannel() : "")
                        .setTradeType(detail.getTradeType())
                        .setTradeTime(detail.getTradeTime() != null ? detail.getTradeTime() : LocalDateTime.now())
                        .setDebitCredit(debitCredit)
                        .setChangeDirection(changeDirectionEnum)
                        .setCurrency(detail.getCurrency() != null ? detail.getCurrency() : "CNY")
                        .setPreBalance(oldAccountBalance)
                        .setAmount(amount)
                        .setPostBalance(newAccountBalance)
                        .setAccountingDate(detail.getAccountingDate())
                        .setSummary(StrUtil.isNotBlank(detail.getSummary()) ? detail.getSummary() : "缓冲记账(异步逐条)");
                accountDetailRepository.insert(accountDetail);

                // 6. 写入子账户明细快照（t_sub_account_detail）
                SubAccountDetailPO subDetail = new SubAccountDetailPO();
                subDetail.setVoucherNo(detail.getVoucherNo())
                        .setEntryId(detail.getEntryId())
                        .setTxnNo(detail.getTxnNo())
                        .setTraceNo(detail.getTraceNo())
                        .setTraceSeq(detail.getTraceSeq())
                        .setAccountNo(detail.getAccountNo())
                        .setBalanceType(BalanceTypeEnum.AVAILABLE)
                        .setTradingCode(detail.getTradingCode())
                        .setTradeType(detail.getTradeType())
                        .setTradeTime(detail.getTradeTime() != null ? detail.getTradeTime() : LocalDateTime.now())
                        .setDebitCredit(debitCredit)
                        .setChangeDirection(changeDirectionEnum)
                        .setCurrency(detail.getCurrency())
                        .setPreBalance(oldSubBalance)
                        .setAmount(amount)
                        .setPostBalance(newSubBalance)
                        .setAccountingDate(detail.getAccountingDate())
                        .setSummary(StrUtil.isNotBlank(detail.getSummary()) ? detail.getSummary() : "缓冲记账(异步逐条)");
                subAccountDetailRepository.insert(subDetail);

                // 7. 更新分录状态为已过账
                AccountingVoucherEntryPO entry = accountingVoucherRepository
                        .selectEntryByVoucherNoAndEntryId(detail.getVoucherNo(), detail.getEntryId());
                if (entry != null) {
                    entry.setStatus(VoucherEntryStatusEnum.POSTED);
                    entry.setBalanceUpdateTime(LocalDateTime.now());
                    accountingVoucherRepository.updateEntryById(entry);
                }

                // 8. 更新凭证状态
                updateVoucherStatus(detail.getVoucherNo());

                // 9. 更新缓冲明细状态为成功
                LocalDateTime completeTime = LocalDateTime.now();
                bufferPostingDetailRepository.updateToSuccess(detail.getId(), completeTime);

                log.info("[BUFFER-POSTING] 逐条过账成功: detailId={}, accountNo={}, amount={}, " +
                                "account: {}->{}, subAccount: {}->{}",
                        detail.getId(), detail.getAccountNo(), amount,
                        oldAccountBalance, newAccountBalance, oldSubBalance, newSubBalance);

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    /**
     * 批量过账事务逻辑（按账户汇总，buffer_mode=2/3 专用）
     * <p>
     * 遵循设计规范：按时间序排序缓冲明细，运用 Running Balance 逐笔推导每笔的 Pre/Post 余额，
     * 一次性更新主账户和子账户余额，并逐笔落库 t_account_detail 与 t_sub_account_detail。
     */
    private void executeBatchPostingForAccount(
            String accountNo, LocalDate accountingDate, List<BufferPostingDetailPO> details) {

        transactionTemplate.execute(status -> {
            try {
                // 1. 查询主账户和子账户
                AccountPO account = accountRepository.selectByAccountNo(accountNo);
                if (account == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "主账户不存在: accountNo=" + accountNo);
                }
                SubAccountPO subAccount = subAccountRepository.selectByAccountNoAndType(
                        accountNo, BalanceTypeEnum.AVAILABLE.getCode());
                if (subAccount == null) {
                    throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND,
                            "子账户不存在: accountNo=" + accountNo);
                }

                // 2. 按时间升序排序明细，执行 Running Balance 逐笔推导
                List<BufferPostingDetailPO> sortedDetails = new ArrayList<>(details);
                sortedDetails.sort(Comparator.comparing(
                        (BufferPostingDetailPO d) -> d.getTradeTime() != null ? d.getTradeTime() : LocalDateTime.MIN)
                        .thenComparing(BufferPostingDetailPO::getId));

                BigDecimal runningAccountBalance = account.getBalance();
                BigDecimal runningSubBalance = subAccount.getBalance();
                BalanceDirectionEnum balanceDirection = subAccount.getBalanceDirection();

                List<AccountDetailPO> accountDetailsToInsert = new ArrayList<>(sortedDetails.size());
                List<SubAccountDetailPO> subDetailsToInsert = new ArrayList<>(sortedDetails.size());

                for (BufferPostingDetailPO detail : sortedDetails) {
                    DebitCreditEnum debitCredit = detail.getDebitCredit();
                    BigDecimal amount = detail.getAmount();
                    int changeDirection = calculateChangeDirection(balanceDirection, debitCredit);
                    ChangeDirectionEnum changeDirectionEnum = (changeDirection == 1
                            ? ChangeDirectionEnum.INCREASE
                            : ChangeDirectionEnum.DECREASE);

                    BigDecimal preAccount = runningAccountBalance;
                    BigDecimal postAccount = AccountBalanceCalculator.calculateNewBalance(
                            preAccount, amount, changeDirection);
                    runningAccountBalance = postAccount;

                    BigDecimal preSub = runningSubBalance;
                    BigDecimal postSub = AccountBalanceCalculator.calculateNewBalance(
                            preSub, amount, changeDirection);
                    runningSubBalance = postSub;

                    // 组装 t_account_detail
                    AccountDetailPO ad = new AccountDetailPO();
                    ad.setVoucherNo(detail.getVoucherNo())
                            .setEntryId(detail.getEntryId())
                            .setTxnNo(StrUtil.isNotBlank(detail.getTxnNo()) ? detail.getTxnNo() : "")
                            .setTraceNo(StrUtil.isNotBlank(detail.getTraceNo()) ? detail.getTraceNo() : detail.getVoucherNo())
                            .setTraceSeq(detail.getTraceSeq() != null ? detail.getTraceSeq() : 0)
                            .setSubjectCode(account.getSubjectCode())
                            .setAccountNo(accountNo)
                            .setBusinessCode(StrUtil.isNotBlank(detail.getBusinessCode()) ? detail.getBusinessCode() : "")
                            .setTradingCode(StrUtil.isNotBlank(detail.getTradingCode()) ? detail.getTradingCode() : "")
                            .setPayChannel(StrUtil.isNotBlank(detail.getPayChannel()) ? detail.getPayChannel() : "")
                            .setTradeType(detail.getTradeType())
                            .setTradeTime(detail.getTradeTime() != null ? detail.getTradeTime() : LocalDateTime.now())
                            .setDebitCredit(debitCredit)
                            .setChangeDirection(changeDirectionEnum)
                            .setCurrency(detail.getCurrency() != null ? detail.getCurrency() : "CNY")
                            .setPreBalance(preAccount)
                            .setAmount(amount)
                            .setPostBalance(postAccount)
                            .setAccountingDate(accountingDate)
                            .setSummary(StrUtil.isNotBlank(detail.getSummary()) ? detail.getSummary() : "缓冲记账(批量入账)");
                    accountDetailsToInsert.add(ad);

                    // 组装 t_sub_account_detail
                    SubAccountDetailPO sad = new SubAccountDetailPO();
                    sad.setVoucherNo(detail.getVoucherNo())
                            .setEntryId(detail.getEntryId())
                            .setTxnNo(detail.getTxnNo())
                            .setTraceNo(detail.getTraceNo())
                            .setTraceSeq(detail.getTraceSeq())
                            .setAccountNo(accountNo)
                            .setBalanceType(BalanceTypeEnum.AVAILABLE)
                            .setTradingCode(detail.getTradingCode())
                            .setTradeType(detail.getTradeType())
                            .setTradeTime(detail.getTradeTime() != null ? detail.getTradeTime() : LocalDateTime.now())
                            .setDebitCredit(debitCredit)
                            .setChangeDirection(changeDirectionEnum)
                            .setCurrency(detail.getCurrency())
                            .setPreBalance(preSub)
                            .setAmount(amount)
                            .setPostBalance(postSub)
                            .setAccountingDate(accountingDate)
                            .setSummary(StrUtil.isNotBlank(detail.getSummary()) ? detail.getSummary() : "缓冲记账(批量入账)");
                    subDetailsToInsert.add(sad);
                }

                // 3. 一次性更新主账户和子账户余额
                account.setBalance(runningAccountBalance);
                accountRepository.updateById(account);

                subAccount.setBalance(runningSubBalance);
                subAccountRepository.updateById(subAccount);

                // 4. 批量落库明细流水
                accountDetailRepository.batchInsert(accountDetailsToInsert);
                subAccountDetailRepository.batchInsert(subDetailsToInsert);

                // 5. 逐条更新分录和凭证状态及缓冲明细
                for (BufferPostingDetailPO detail : details) {
                    AccountingVoucherEntryPO entry = accountingVoucherRepository
                            .selectEntryByVoucherNoAndEntryId(detail.getVoucherNo(), detail.getEntryId());
                    if (entry != null) {
                        entry.setStatus(VoucherEntryStatusEnum.POSTED);
                        entry.setBalanceUpdateTime(LocalDateTime.now());
                        accountingVoucherRepository.updateEntryById(entry);
                    }

                    updateVoucherStatus(detail.getVoucherNo());
                    bufferPostingDetailRepository.updateToSuccess(detail.getId(), LocalDateTime.now());
                }

                log.info("[BUFFER-POSTING] 批量过账成功: accountNo={}, details={}, " +
                                "accountEnd={}, subAccountEnd={}",
                        accountNo, details.size(), runningAccountBalance, runningSubBalance);

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    /**
     * 计算增减方向
     */
    private int calculateChangeDirection(BalanceDirectionEnum balanceDirection, DebitCreditEnum debitCredit) {
        int bd = balanceDirection != null ? balanceDirection.getCode() : 1;
        int dc = debitCredit != null ? debitCredit.getCode() : 1;
        return (bd == dc) ? 1 : 2;
    }

    private String buildLockKey(String accountNo) {
        return RedisKeyConstants.Lock.Account.accountMutex(accountNo);
    }

    /**
     * 查询主账户（悲观锁/普通查询）
     */
    private AccountPO getAccount(String accountNo, boolean usePessimistic) {
        if (usePessimistic) {
            List<AccountPO> accounts = accountRepository.selectForUpdateBatch(Collections.singletonList(accountNo));
            return (accounts != null && !accounts.isEmpty()) ? accounts.get(0) : null;
        }
        return accountRepository.selectByAccountNo(accountNo);
    }

    /**
     * 查询子账户（悲观锁/普通查询，精准获取可用余额子账户）
     */
    private SubAccountPO getSubAccount(String accountNo, boolean usePessimistic) {
        if (usePessimistic) {
            List<SubAccountPO> subs = subAccountRepository.selectForUpdate(accountNo);
            return (subs != null)
                ? subs.stream().filter(s -> BalanceTypeEnum.AVAILABLE.equals(s.getBalanceType())).findFirst().orElse(null)
                : null;
        }
        return subAccountRepository.selectByAccountNoAndType(
                accountNo, BalanceTypeEnum.AVAILABLE.getCode());
    }

    /**
     * 更新凭证状态
     * <p>
     * P1-5 修复：仅在状态发生变化时执行 UPDATE。
     */
    private void updateVoucherStatus(String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            return;
        }

        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository.selectEntriesByVoucherNo(voucherNo);
        boolean allBufferedPosted = entries.stream()
                .filter(e -> e.getBuffered() != null && e.getBuffered() == 1)
                .allMatch(e -> e.getStatus() == VoucherEntryStatusEnum.POSTED);

        VoucherStatusEnum targetStatus;
        if (allBufferedPosted && !entries.isEmpty()) {
            targetStatus = VoucherStatusEnum.POSTED;
        } else {
            targetStatus = VoucherStatusEnum.POSTING;
        }

        // P1-5: 仅状态变化时执行更新
        if (!voucher.getStatus().equals(targetStatus)) {
            voucher.setStatus(targetStatus);
            if (targetStatus == VoucherStatusEnum.POSTED) {
                voucher.setPostTime(LocalDateTime.now());
            }
            accountingVoucherRepository.updateById(voucher);
        }
    }

    @Data
    public static class BatchPostingResult {
        private int totalCount;
        private int successCount;
        private int failedCount;
        private long totalDurationMs;
        private List<FailedItemInfo> failedList = new ArrayList<>();
    }

    @Data
    public static class FailedItemInfo {
        private Long detailId;
        private String accountNo;
        private BigDecimal amount;
        private String failReason;

        public FailedItemInfo(Long detailId, String accountNo, BigDecimal amount, String failReason) {
            this.detailId = detailId;
            this.accountNo = accountNo;
            this.amount = amount;
            this.failReason = failReason;
        }
    }
}
