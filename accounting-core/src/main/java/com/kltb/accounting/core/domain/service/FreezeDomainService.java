package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.infrastructure.account.FreezeIdGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 资金冻结领域服务
 * <p>
 * 职责：
 * 1. 资金冻结（可用余额 → 冻结余额）
 * 2. 资金解冻（冻结余额 → 可用余额）
 * 3. 冻结扣款（冻结余额减少 + 主账户余额减少）
 * 4. 超时自动解冻
 * 5. 冻结记录查询
 * <p>
 * 核心原则：冻结/解冻操作不生成凭证和分录，仅操作余额 + 记录明细。
 * 冻结扣款是唯一需要生成凭证的场景（涉及主账户余额变动）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreezeDomainService {

    private static final LocalDateTime DEFAULT_EXPIRE_TIME = LocalDateTime.of(2099, 12, 31, 0, 0, 0);

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final FreezeDetailRepository freezeDetailRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final FreezeIdGenerator freezeIdGenerator;

    /**
     * 资金冻结（可用余额 → 冻结余额）
     *
     * @param accountNo    账户编号
     * @param freezeAmount 冻结金额
     * @param expireTime   过期时间（null 表示永不过期）
     * @param reason       冻结原因
     * @return 冻结记录 PO
     */
    public AccountFreezeDetailPO freezeFund(String accountNo, BigDecimal freezeAmount,
                                            LocalDateTime expireTime, String reason) {
        validateFreezeAmount(freezeAmount);
        validateExpireTime(expireTime);

        // 锁外预检查
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        if (account.getStatus() != AccountStatusEnum.NORMAL) {
            throw new AccountException(ResultCode.ACCOUNT_FROZEN_CANNOT_FREEZE,
                    "账户状态非 NORMAL，无法执行资金冻结: " + accountNo + ", status=" + account.getStatus().getDesc());
        }

        SubAccountPO availableSub = subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode());
        if (availableSub == null) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
                    "可用子账户不存在: " + accountNo);
        }
        if (availableSub.getBalance().compareTo(freezeAmount) < 0) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
                    "可用余额不足: available=" + availableSub.getBalance() + ", freeze=" + freezeAmount);
        }

        // 分布式锁 + 事务执行
        return distributedLockTemplate.execute(
                "account:fund:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(status -> {
                    // 双重检查
                    SubAccountPO currentAvailable = subAccountRepository.selectByAccountNoAndType(
                            accountNo, BalanceTypeEnum.AVAILABLE.getCode());
                    if (currentAvailable == null) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID, "可用子账户不存在: " + accountNo);
                    }
                    if (currentAvailable.getBalance().compareTo(freezeAmount) < 0) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
                                "可用余额不足（双重检查）: available=" + currentAvailable.getBalance() + ", freeze=" + freezeAmount);
                    }

                    SubAccountPO frozenSub = subAccountRepository.selectByAccountNoAndType(
                            accountNo, BalanceTypeEnum.FROZEN.getCode());
                    if (frozenSub == null) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID, "冻结子账户不存在: " + accountNo);
                    }

                    // 余额转移
                    BigDecimal newAvailable = currentAvailable.getBalance().subtract(freezeAmount);
                    BigDecimal newFrozen = frozenSub.getBalance().add(freezeAmount);
                    subAccountRepository.updateBalance(accountNo, BalanceTypeEnum.AVAILABLE.getCode(),
                            newAvailable, currentAvailable.getVersion());
                    subAccountRepository.updateBalance(accountNo, BalanceTypeEnum.FROZEN.getCode(),
                            newFrozen, frozenSub.getVersion());

                    // 生成冻结编号
                    String freezeId = freezeIdGenerator.generate();
                    LocalDateTime now = LocalDateTime.now();

                    // 写子账户明细
                    String currency = account.getCurrency();
                    insertSubAccountDetail(accountNo, freezeId, "FRZ", currency,
                            BalanceTypeEnum.AVAILABLE, DebitCreditEnum.CREDIT,
                            currentAvailable.getBalance(), freezeAmount, newAvailable, now, "资金冻结-可用减少");
                    insertSubAccountDetail(accountNo, freezeId, "FRZ", currency,
                            BalanceTypeEnum.FROZEN, DebitCreditEnum.DEBIT,
                            frozenSub.getBalance(), freezeAmount, newFrozen, now, "资金冻结-冻结增加");

                    // 创建冻结记录
                    AccountFreezeDetailPO freezeDetail = new AccountFreezeDetailPO();
                    freezeDetail.setVoucherNo(freezeId);
                    freezeDetail.setAccountNo(accountNo);
                    freezeDetail.setBusinessCode("GENERAL");
                    freezeDetail.setFreezeAmount(freezeAmount);
                    freezeDetail.setStatus(FreezeStatusEnum.FROZEN);
                    freezeDetail.setExpireTime(expireTime != null ? expireTime : DEFAULT_EXPIRE_TIME);
                    freezeDetail.setTradeTime(now);
                    freezeDetail.setSummary(StringUtils.isNotBlank(reason) ? reason : "资金冻结");
                    freezeDetailRepository.insert(freezeDetail);

                    log.info("[FREEZE] 资金冻结完成 accountNo={} freezeId={} amount={}", accountNo, freezeId, freezeAmount);
                    return freezeDetail;
                })
        );
    }

    /**
     * 资金解冻（冻结余额 → 可用余额）
     *
     * @param freezeId       冻结编号
     * @param unfreezeAmount 解冻金额
     * @param reason         解冻原因
     */
    public void unfreezeFund(String freezeId, BigDecimal unfreezeAmount, String reason) {
        validateUnfreezeAmount(unfreezeAmount);

        // 锁外预检查
        AccountFreezeDetailPO freezeRecord = freezeDetailRepository.selectByVoucherNo(freezeId);
        if (freezeRecord == null) {
            throw new AccountException(ResultCode.FREEZE_RECORD_NOT_FOUND, "冻结记录不存在: " + freezeId);
        }
        if (freezeRecord.getStatus() != FreezeStatusEnum.FROZEN) {
            throw new AccountException(ResultCode.FREEZE_STATUS_INVALID,
                    "冻结记录状态非法: " + freezeId + ", status=" + freezeRecord.getStatus().getDesc());
        }
        if (unfreezeAmount.compareTo(freezeRecord.getFreezeAmount()) > 0) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_EXCEEDED,
                    "解冻金额超过冻结金额: unfreeze=" + unfreezeAmount + ", freeze=" + freezeRecord.getFreezeAmount());
        }

        unfreezeFundInternal(freezeRecord, unfreezeAmount, reason);
    }

    private void unfreezeFundInternal(AccountFreezeDetailPO freezeRecord, BigDecimal unfreezeAmount, String reason) {
        String accountNo = resolveAccountNoFromFreezeRecord(freezeRecord);

        distributedLockTemplate.execute(
                "account:fund:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(s -> {
                    // 双重检查
                    AccountFreezeDetailPO currentRecord = freezeDetailRepository.selectByVoucherNo(freezeRecord.getVoucherNo());
                    if (currentRecord == null || currentRecord.getStatus() != FreezeStatusEnum.FROZEN) {
                        throw new AccountException(ResultCode.FREEZE_STATUS_INVALID,
                                "冻结记录状态非法（双重检查）: " + freezeRecord.getVoucherNo());
                    }

                    SubAccountPO frozenSub = subAccountRepository.selectByAccountNoAndType(
                            accountNo, BalanceTypeEnum.FROZEN.getCode());
                    if (frozenSub == null || frozenSub.getBalance().compareTo(unfreezeAmount) < 0) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_EXCEEDED,
                                "冻结余额不足: " + accountNo);
                    }

                    SubAccountPO availableSub = subAccountRepository.selectByAccountNoAndType(
                            accountNo, BalanceTypeEnum.AVAILABLE.getCode());
                    if (availableSub == null) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID, "可用子账户不存在: " + accountNo);
                    }

                    // 余额转移
                    BigDecimal newFrozen = frozenSub.getBalance().subtract(unfreezeAmount);
                    BigDecimal newAvailable = availableSub.getBalance().add(unfreezeAmount);
                    subAccountRepository.updateBalance(accountNo, BalanceTypeEnum.FROZEN.getCode(),
                            newFrozen, frozenSub.getVersion());
                    subAccountRepository.updateBalance(accountNo, BalanceTypeEnum.AVAILABLE.getCode(),
                            newAvailable, availableSub.getVersion());

                    LocalDateTime now = LocalDateTime.now();
                    String currency = resolveCurrency(accountNo);
                    insertSubAccountDetail(accountNo, freezeRecord.getVoucherNo(), "UFZ", currency,
                            BalanceTypeEnum.FROZEN, DebitCreditEnum.CREDIT,
                            frozenSub.getBalance(), unfreezeAmount, newFrozen, now, "资金解冻-冻结减少");
                    insertSubAccountDetail(accountNo, freezeRecord.getVoucherNo(), "UFZ", currency,
                            BalanceTypeEnum.AVAILABLE, DebitCreditEnum.DEBIT,
                            availableSub.getBalance(), unfreezeAmount, newAvailable, now, "资金解冻-可用增加");

                    // 完全解冻时更新状态（冻结子账户余额归零）
                    if (newFrozen.compareTo(BigDecimal.ZERO) == 0) {
                        freezeDetailRepository.updateStatus(currentRecord.getVoucherNo(),
                                FreezeStatusEnum.UNFROZEN, currentRecord.getVersion());
                    }

                    log.info("[UNFREEZE] 资金解冻完成 freezeId={} amount={}", freezeRecord.getVoucherNo(), unfreezeAmount);
                    return null;
                })
        );
    }

    /**
     * 冻结扣款（冻结余额减少 + 主账户余额减少）
     *
     * @param freezeId     冻结编号
     * @param deductAmount 扣款金额
     * @param reason       扣款原因
     */
    public void deductFromFreeze(String freezeId, BigDecimal deductAmount, String reason) {
        validateUnfreezeAmount(deductAmount);

        AccountFreezeDetailPO freezeRecord = freezeDetailRepository.selectByVoucherNo(freezeId);
        if (freezeRecord == null) {
            throw new AccountException(ResultCode.FREEZE_RECORD_NOT_FOUND, "冻结记录不存在: " + freezeId);
        }
        if (freezeRecord.getStatus() != FreezeStatusEnum.FROZEN) {
            throw new AccountException(ResultCode.FREEZE_STATUS_INVALID,
                    "冻结记录状态非法: " + freezeId + ", status=" + freezeRecord.getStatus().getDesc());
        }
        if (deductAmount.compareTo(freezeRecord.getFreezeAmount()) > 0) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_EXCEEDED,
                    "扣款金额超过冻结金额: deduct=" + deductAmount + ", freeze=" + freezeRecord.getFreezeAmount());
        }

        String accountNo = resolveAccountNoFromFreezeRecord(freezeRecord);

        // 主账户校验（锁外预检查余额）
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        if (account.getBalance().compareTo(deductAmount) < 0) {
            throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                    "主账户余额不足: available=" + account.getBalance() + ", deduct=" + deductAmount);
        }

        distributedLockTemplate.execute(
                "account:fund:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(s -> {
                    // 双重检查冻结记录状态
                    AccountFreezeDetailPO currentRecord = freezeDetailRepository.selectByVoucherNo(freezeRecord.getVoucherNo());
                    if (currentRecord == null || currentRecord.getStatus() != FreezeStatusEnum.FROZEN) {
                        throw new AccountException(ResultCode.FREEZE_STATUS_INVALID,
                                "冻结记录状态非法（双重检查）: " + freezeRecord.getVoucherNo());
                    }

                    // 双重检查冻结子账户余额
                    SubAccountPO frozenSub = subAccountRepository.selectByAccountNoAndType(
                            accountNo, BalanceTypeEnum.FROZEN.getCode());
                    if (frozenSub == null || frozenSub.getBalance().compareTo(deductAmount) < 0) {
                        throw new AccountException(ResultCode.FREEZE_AMOUNT_EXCEEDED,
                                "冻结余额不足（扣款）: " + accountNo);
                    }

                    // 双重检查主账户余额
                    AccountPO currentAccount = accountRepository.selectByAccountNo(accountNo);
                    if (currentAccount == null || currentAccount.getBalance().compareTo(deductAmount) < 0) {
                        throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                                "主账户余额不足（双重检查）: " + accountNo);
                    }

                    // 冻结子账户余额减少
                    BigDecimal newFrozen = frozenSub.getBalance().subtract(deductAmount);
                    subAccountRepository.updateBalance(accountNo, BalanceTypeEnum.FROZEN.getCode(),
                            newFrozen, frozenSub.getVersion());

                    // 主账户余额减少
                    BigDecimal newMainBalance = currentAccount.getBalance().subtract(deductAmount);
                    accountRepository.updateBalance(accountNo, newMainBalance, currentAccount.getVersion());

                    LocalDateTime now = LocalDateTime.now();

                    // 写子账户明细
                    insertSubAccountDetail(accountNo, freezeRecord.getVoucherNo(), "DED", currentAccount.getCurrency(),
                            BalanceTypeEnum.FROZEN, DebitCreditEnum.CREDIT,
                            frozenSub.getBalance(), deductAmount, newFrozen, now, "冻结扣款-冻结减少");

                    // 写主账户明细
                    insertAccountDetail(account, freezeRecord.getVoucherNo(),
                            currentAccount.getBalance(), deductAmount, newMainBalance, now,
                            DebitCreditEnum.CREDIT, "冻结扣款-主账户减少");

                    // 更新冻结记录为已解冻
                    freezeDetailRepository.updateStatus(currentRecord.getVoucherNo(),
                            FreezeStatusEnum.UNFROZEN, currentRecord.getVersion());

                    log.info("[DEDUCT] 冻结扣款完成 freezeId={} amount={}", freezeRecord.getVoucherNo(), deductAmount);
                    return null;
                })
        );
    }

    /**
     * 查询冻结记录
     */
    public AccountFreezeDetailPO queryFreezeRecord(String freezeId) {
        AccountFreezeDetailPO record = freezeDetailRepository.selectByVoucherNo(freezeId);
        if (record == null) {
            throw new AccountException(ResultCode.FREEZE_RECORD_NOT_FOUND, "冻结记录不存在: " + freezeId);
        }
        return record;
    }

    /**
     * 查询指定账户的冻结记录列表
     */
    public List<AccountFreezeDetailPO> queryFreezeRecords(String accountNo, Integer status) {
        // 使用 MyBatis-Plus 条件查询
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AccountFreezeDetailPO> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(AccountFreezeDetailPO::getAccountNo, accountNo)
               .eq(status != null, AccountFreezeDetailPO::getStatus, status)
               .eq(AccountFreezeDetailPO::getIsDelete, 0)
               .orderByDesc(AccountFreezeDetailPO::getCreateTime);
        return freezeDetailRepository.selectByCondition(wrapper);
    }

    /**
     * 单笔自动解冻（供 Job 调用，独立事务）
     *
     * @param expiredRecord 过期冻结记录
     */
    public void autoUnfreezeOne(AccountFreezeDetailPO expiredRecord) {
        if (expiredRecord.getStatus() != FreezeStatusEnum.FROZEN) {
            log.warn("[AUTO-UNFREEZE] 冻结记录状态非 FROZEN，跳过: freezeId={}", expiredRecord.getVoucherNo());
            return;
        }
        unfreezeFundInternal(expiredRecord, expiredRecord.getFreezeAmount(), "超时自动解冻");
    }

    // ==================== Private Helpers ====================

    private void validateFreezeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
                    "冻结金额必须大于 0: " + amount);
        }
    }

    private void validateExpireTime(LocalDateTime expireTime) {
        if (expireTime != null && expireTime.isBefore(LocalDateTime.now())) {
            throw new AccountException(ResultCode.PARAM_ERROR,
                    "过期时间不能是过去时间: " + expireTime);
        }
    }

    private void validateUnfreezeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
                    "解冻/扣款金额必须大于 0: " + amount);
        }
    }

    /**
     * 从冻结记录中解析账户编号
     */
    private String resolveAccountNoFromFreezeRecord(AccountFreezeDetailPO record) {
        String accountNo = record.getAccountNo();
        if (StringUtils.isBlank(accountNo)) {
            throw new AccountException(ResultCode.DATA_NOT_FOUND,
                    "无法从冻结记录解析账户编号: " + record.getVoucherNo());
        }
        return accountNo;
    }

    /**
     * 查询账户币种（解冻/扣款时用于获取主账户 currency）
     */
    private String resolveCurrency(String accountNo) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        return account.getCurrency();
    }

    /**
     * 写入主账户余额变动明细
     */
    private void insertAccountDetail(AccountPO account, String voucherNo,
                                     BigDecimal preBalance, BigDecimal amount, BigDecimal postBalance,
                                     LocalDateTime tradeTime, DebitCreditEnum debitCredit, String summary) {
        AccountDetailPO detail = new AccountDetailPO();
        detail.setVoucherNo(voucherNo);
        detail.setEntryId(voucherNo + "-MAIN-0");
        detail.setAccountNo(account.getAccountNo());
        detail.setSubjectCode(account.getSubjectCode());
        detail.setTradeType(TradeTypeEnum.NORMAL);
        detail.setTradeTime(tradeTime);
        detail.setDebitCredit(debitCredit);
        detail.setChangeDirection(resolveChangeDirection(preBalance, postBalance));
        detail.setCurrency(account.getCurrency());
        detail.setPreBalance(preBalance);
        detail.setAmount(amount);
        detail.setPostBalance(postBalance);
        detail.setAccountingDate(LocalDate.now());
        detail.setSummary(summary);
        detail.setTenantId(com.kltb.accounting.core.shared.context.TenantContext.get());
        accountDetailRepository.insert(detail);
    }

    /**
     * 写入子账户余额变动明细
     */
    private void insertSubAccountDetail(String accountNo, String voucherNo, String operation, String currency,
                                        BalanceTypeEnum balanceType, DebitCreditEnum debitCredit,
                                        BigDecimal preBalance, BigDecimal amount, BigDecimal postBalance,
                                        LocalDateTime tradeTime, String summary) {
        SubAccountDetailPO detail = new SubAccountDetailPO();
        detail.setVoucherNo(voucherNo);
        detail.setEntryId(voucherNo + "-" + balanceType.getCode() + "-" + operation);
        detail.setAccountNo(accountNo);
        detail.setBalanceType(balanceType);
        detail.setTradeType(TradeTypeEnum.NORMAL);
        detail.setTradeTime(tradeTime);
        detail.setDebitCredit(debitCredit);
        detail.setChangeDirection(resolveChangeDirection(preBalance, postBalance));
        detail.setCurrency(currency);
        detail.setPreBalance(preBalance);
        detail.setAmount(amount);
        detail.setPostBalance(postBalance);
        detail.setAccountingDate(LocalDate.now());
        detail.setSummary(summary);
        detail.setTenantId(com.kltb.accounting.core.shared.context.TenantContext.get());
        subAccountDetailRepository.insert(detail);
    }

    private ChangeDirectionEnum resolveChangeDirection(BigDecimal preBalance, BigDecimal postBalance) {
        return postBalance.compareTo(preBalance) >= 0
                ? ChangeDirectionEnum.INCREASE : ChangeDirectionEnum.DECREASE;
    }
}
