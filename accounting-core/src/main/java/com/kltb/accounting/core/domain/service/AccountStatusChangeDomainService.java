package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 账户状态变更领域服务
 * <p>
 * 职责：
 * 1. 冻结/解冻/注销账户（主状态变更）
 * 2. 风控状态变更
 * 3. 状态机校验（合法转换表 + 同状态幂等）
 * 4. 注销前置余额校验（主账户 + 子账户）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountStatusChangeDomainService {

    // CANCELLED 无出边（不可逆），故意不放入 Map，确保任何指向 CANCELLED 之外的转换都被拦截
    private static final Map<AccountStatusEnum, List<AccountStatusEnum>> VALID_TRANSITIONS = Map.of(
            AccountStatusEnum.NORMAL, List.of(AccountStatusEnum.FROZEN, AccountStatusEnum.CANCELLED),
            AccountStatusEnum.FROZEN, List.of(AccountStatusEnum.NORMAL, AccountStatusEnum.CANCELLED)
    );

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 冻结账户（NORMAL → FROZEN）
     */
    public AccountPO freezeAccount(String accountNo, String reason) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        validateTransition(account.getStatus(), AccountStatusEnum.FROZEN);
        if (account.getStatus() == AccountStatusEnum.FROZEN) {
            return account;
        }

        return distributedLockTemplate.execute(
                "account:status:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(status -> {
                    AccountPO current = accountRepository.selectByAccountNo(accountNo);
                    if (current == null) {
                        throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
                    }
                    validateTransition(current.getStatus(), AccountStatusEnum.FROZEN);

                    MDC.put("accountNo", accountNo);
                    try {
                        MDC.put("operation", "FREEZE");
                        MDC.put("reason", reason != null ? reason : "");
                        log.info("[ACCOUNT_STATUS] 冻结账户 accountNo={} reason={}", accountNo, reason);

                        accountRepository.updateStatus(accountNo, AccountStatusEnum.FROZEN, current.getVersion());

                        log.info("[ACCOUNT_STATUS] 冻结完成 accountNo={} status=FROZEN", accountNo);
                    } finally {
                        MDC.remove("operation");
                        MDC.remove("reason");
                        MDC.remove("accountNo");
                    }
                    return constructUpdated(current, AccountStatusEnum.FROZEN, current.getRiskStatus());
                })
        );
    }

    /**
     * 解冻账户（FROZEN → NORMAL）
     */
    public AccountPO unfreezeAccount(String accountNo) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        validateTransition(account.getStatus(), AccountStatusEnum.NORMAL);
        if (account.getStatus() == AccountStatusEnum.NORMAL) {
            return account;
        }

        return distributedLockTemplate.execute(
                "account:status:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(status -> {
                    AccountPO current = accountRepository.selectByAccountNo(accountNo);
                    if (current == null) {
                        throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
                    }
                    validateTransition(current.getStatus(), AccountStatusEnum.NORMAL);

                    MDC.put("accountNo", accountNo);
                    try {
                        MDC.put("operation", "UNFREEZE");
                        log.info("[ACCOUNT_STATUS] 解冻账户 accountNo={}", accountNo);

                        accountRepository.updateStatus(accountNo, AccountStatusEnum.NORMAL, current.getVersion());

                        log.info("[ACCOUNT_STATUS] 解冻完成 accountNo={} status=NORMAL", accountNo);
                    } finally {
                        MDC.remove("operation");
                        MDC.remove("accountNo");
                    }
                    return constructUpdated(current, AccountStatusEnum.NORMAL, current.getRiskStatus());
                })
        );
    }

    /**
     * 注销账户（NORMAL/FROZEN → CANCELLED）
     * 前置校验：主账户余额为零 + 子账户余额为零 + 未注销
     */
    public AccountPO cancelAccount(String accountNo, String reason) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        validateTransition(account.getStatus(), AccountStatusEnum.CANCELLED);
        if (account.getStatus() == AccountStatusEnum.CANCELLED) {
            return account;
        }

        return distributedLockTemplate.execute(
                "account:status:" + accountNo,
                3, -1,
                () -> transactionTemplate.execute(status -> {
                    AccountPO current = accountRepository.selectByAccountNo(accountNo);
                    if (current == null) {
                        throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
                    }
                    validateTransition(current.getStatus(), AccountStatusEnum.CANCELLED);

                    validateBalanceZero(current);
                    validateSubAccountsBalanceZero(accountNo);

                    LocalDate cancelDate = LocalDate.now();
                    MDC.put("accountNo", accountNo);
                    try {
                        MDC.put("operation", "CANCEL");
                        MDC.put("reason", reason != null ? reason : "");
                        log.info("[ACCOUNT_STATUS] 注销账户 accountNo={} reason={}", accountNo, reason);

                        AccountPO toUpdate = new AccountPO();
                        toUpdate.setId(current.getId());
                        toUpdate.setStatus(AccountStatusEnum.CANCELLED);
                        toUpdate.setInactiveDate(cancelDate);
                        toUpdate.setVersion(current.getVersion());
                        accountRepository.updateById(toUpdate);

                        log.info("[ACCOUNT_STATUS] 注销完成 accountNo={} status=CANCELLED", accountNo);
                    } finally {
                        MDC.remove("operation");
                        MDC.remove("reason");
                        MDC.remove("accountNo");
                    }
                    AccountPO updated = constructUpdated(current, AccountStatusEnum.CANCELLED, current.getRiskStatus());
                    updated.setInactiveDate(cancelDate);
                    return updated;
                })
        );
    }

    /**
     * 变更风控状态（独立操作，不加分布式锁）
     */
    public AccountPO changeRiskStatus(String accountNo, RiskStatusEnum riskStatus) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }

        return transactionTemplate.execute(s -> {
            AccountPO current = accountRepository.selectByAccountNo(accountNo);
            if (current == null) {
                throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
            }

            MDC.put("accountNo", accountNo);
            try {
                MDC.put("operation", "RISK_CHANGE");
                MDC.put("riskStatus", riskStatus.getDesc());
                log.info("[ACCOUNT_STATUS] 变更风控状态 accountNo={} riskStatus={}", accountNo, riskStatus.getDesc());

                accountRepository.updateRiskStatus(accountNo, riskStatus, current.getVersion());

                String riskDesc = riskStatus != null ? riskStatus.getDesc() : null;
                log.info("[ACCOUNT_STATUS] 风控状态变更完成 accountNo={} riskStatus={}", accountNo, riskDesc);
            } finally {
                MDC.remove("operation");
                MDC.remove("riskStatus");
                MDC.remove("accountNo");
            }
            return constructUpdated(current, current.getStatus(), riskStatus);
        });
    }

    /**
     * 查询账户状态
     */
    public AccountPO queryAccountStatus(String accountNo) {
        AccountPO account = accountRepository.selectByAccountNo(accountNo);
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
        return account;
    }

    private void validateTransition(AccountStatusEnum current, AccountStatusEnum target) {
        if (current == target) {
            return;
        }
        List<AccountStatusEnum> allowed = VALID_TRANSITIONS.getOrDefault(current, Collections.emptyList());
        if (!allowed.contains(target)) {
            throw new AccountException(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID,
                    String.format("账户状态不允许转换: %s → %s", current.getDesc(), target.getDesc()));
        }
    }

    private AccountPO constructUpdated(AccountPO original, AccountStatusEnum status, RiskStatusEnum riskStatus) {
        AccountPO updated = new AccountPO();
        updated.setId(original.getId());
        updated.setTenantId(original.getTenantId());
        updated.setIsDelete(original.getIsDelete());
        updated.setCreateTime(original.getCreateTime());
        updated.setUpdateTime(original.getUpdateTime());
        updated.setAccountNo(original.getAccountNo());
        updated.setAccountName(original.getAccountName());
        updated.setSubjectCode(original.getSubjectCode());
        updated.setOwnerId(original.getOwnerId());
        updated.setOwnerType(original.getOwnerType());
        updated.setAccountType(original.getAccountType());
        updated.setCurrency(original.getCurrency());
        updated.setBalanceDirection(original.getBalanceDirection());
        updated.setOpeningBalance(original.getOpeningBalance());
        updated.setBalance(original.getBalance());
        updated.setStatus(status);
        updated.setRiskStatus(riskStatus);
        updated.setRequestNo(original.getRequestNo());
        updated.setOpenDate(original.getOpenDate());
        updated.setInactiveDate(original.getInactiveDate());
        updated.setVersion(original.getVersion() + 1);
        return updated;
    }

    private void validateBalanceZero(AccountPO account) {
        if (account.getBalance() != null && account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new AccountException(ResultCode.ACCOUNT_BALANCE_NOT_ZERO,
                    "账户余额不为零，无法注销: " + account.getAccountNo() + ", balance=" + account.getBalance());
        }
    }

    private void validateSubAccountsBalanceZero(String accountNo) {
        List<SubAccountPO> subAccounts = subAccountRepository.selectByAccountNo(accountNo);
        for (SubAccountPO sub : subAccounts) {
            if (sub.getBalance() != null && sub.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                throw new AccountException(ResultCode.ACCOUNT_BALANCE_NOT_ZERO,
                        "子账户余额不为零，无法注销: " + accountNo + ", subBalanceType=" + sub.getBalanceType()
                                + ", balance=" + sub.getBalance());
            }
        }
    }
}
