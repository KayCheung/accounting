package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.shared.exception.AccountException;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 账户状态统一校验器
 * <p>
 * 职责：集中统一校验分户账户的存在性、主状态（正常/冻结/注销）以及风控状态（止入/止出）。
 * 消除散落在应用层、领域服务各处的重复冗余 if-else 状态判断，确保全系统异常码与错误提示严格一致。
 */
public final class AccountValidator {

    private AccountValidator() {}

    /**
     * 校验账户是否存在
     *
     * @param account   账户实体
     * @param accountNo 账户编号（用于异常提示）
     */
    public static void validateExists(AccountPO account, String accountNo) {
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
        }
    }

    /**
     * 校验账户是否可执行过账/记账（基础主状态校验）
     *
     * @param account 账户实体
     */
    public static void validatePostable(AccountPO account) {
        validatePostable(account, null);
    }

    /**
     * 校验账户是否可执行过账/记账（主状态 + 增减方向风控状态综合校验）
     *
     * @param account         账户实体
     * @param changeDirection 增减方向（1-增加/入账, 2-减少/出账，为null时不校验风控方向）
     */
    public static void validatePostable(AccountPO account, Integer changeDirection) {
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在");
        }

        String accountNo = account.getAccountNo();
        AccountStatusEnum status = account.getStatus();

        // 1. 主状态校验
        if (status != AccountStatusEnum.NORMAL) {
            if (status == AccountStatusEnum.FROZEN) {
                throw new AccountException(ResultCode.ACCOUNT_FROZEN,
                        "账户已冻结，禁止记账: accountNo=" + accountNo);
            }
            if (status == AccountStatusEnum.CANCELLED) {
                throw new AccountException(ResultCode.ACCOUNT_CANCELLED,
                        "账户已注销，禁止记账: accountNo=" + accountNo);
            }
            throw new AccountException(ResultCode.ACCOUNT_STATUS_ILLEGAL,
                    "账户状态异常，禁止记账: accountNo=" + accountNo + ", status="
                            + (status != null ? status.getDesc() : "null"));
        }

        // 2. 风控状态校验（止入 / 止出拦截）
        RiskStatusEnum riskStatus = account.getRiskStatus();
        if (riskStatus != null && riskStatus != RiskStatusEnum.NORMAL && changeDirection != null) {
            if (changeDirection == 1) { // 增 (入账)
                if (riskStatus == RiskStatusEnum.NO_IN || riskStatus == RiskStatusEnum.NO_IN_OUT) {
                    throw new AccountException(ResultCode.ACCOUNT_RISK_BLOCKED,
                            "账户风控止入，禁止入账: accountNo=" + accountNo + ", riskStatus=" + riskStatus.getDesc());
                }
            } else if (changeDirection == 2) { // 减 (出账)
                if (riskStatus == RiskStatusEnum.NO_OUT || riskStatus == RiskStatusEnum.NO_IN_OUT) {
                    throw new AccountException(ResultCode.ACCOUNT_RISK_BLOCKED,
                            "账户风控止出，禁止出账: accountNo=" + accountNo + ", riskStatus=" + riskStatus.getDesc());
                }
            }
        }
    }

    /**
     * 校验账户是否为正常状态（常规操作校验）
     *
     * @param account 账户实体
     */
    public static void validateNormal(AccountPO account) {
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在");
        }

        String accountNo = account.getAccountNo();
        AccountStatusEnum status = account.getStatus();

        if (status != AccountStatusEnum.NORMAL) {
            if (status == AccountStatusEnum.FROZEN) {
                throw new AccountException(ResultCode.ACCOUNT_FROZEN,
                        "账户已冻结，无法操作: accountNo=" + accountNo);
            }
            if (status == AccountStatusEnum.CANCELLED) {
                throw new AccountException(ResultCode.ACCOUNT_CANCELLED,
                        "账户已注销，无法操作: accountNo=" + accountNo);
            }
            throw new AccountException(ResultCode.ACCOUNT_STATUS_ILLEGAL,
                    "账户状态异常，无法操作: accountNo=" + accountNo + ", status="
                            + (status != null ? status.getDesc() : "null"));
        }
    }

    /**
     * 校验账户是否可执行资金冻结操作
     *
     * @param account 账户实体
     */
    public static void validateFreezable(AccountPO account) {
        if (account == null) {
            throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在");
        }
        if (account.getStatus() != AccountStatusEnum.NORMAL) {
            throw new AccountException(ResultCode.ACCOUNT_FROZEN_CANNOT_FREEZE,
                    "账户状态非 NORMAL，无法执行资金冻结: " + account.getAccountNo()
                            + ", status=" + (account.getStatus() != null ? account.getStatus().getDesc() : "null"));
        }
    }

    // CANCELLED 无出边（不可逆），故意不放入 Map，确保任何指向 CANCELLED 之外的转换都被拦截
    private static final Map<AccountStatusEnum, List<AccountStatusEnum>> VALID_TRANSITIONS = Map.of(
            AccountStatusEnum.NORMAL, List.of(AccountStatusEnum.FROZEN, AccountStatusEnum.CANCELLED),
            AccountStatusEnum.FROZEN, List.of(AccountStatusEnum.NORMAL, AccountStatusEnum.CANCELLED)
    );

    /**
     * 校验账户状态转换是否合法
     *
     * @param current 当前状态
     * @param target  目标状态
     */
    public static void validateTransition(AccountStatusEnum current, AccountStatusEnum target) {
        if (current == target) {
            return;
        }
        if (current == null || target == null) {
            throw new AccountException(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID,
                    "账户状态转换参数为空");
        }
        List<AccountStatusEnum> allowed = VALID_TRANSITIONS.getOrDefault(current, Collections.emptyList());
        if (!allowed.contains(target)) {
            throw new AccountException(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID,
                    String.format("账户状态不允许转换: %s → %s", current.getDesc(), target.getDesc()));
        }
    }

    /**
     * 判断账户状态转换是否合法（不抛异常版本）
     */
    public static boolean canTransition(AccountStatusEnum current, AccountStatusEnum target) {
        if (current == target) return true;
        if (current == null || target == null) return false;
        return VALID_TRANSITIONS.getOrDefault(current, Collections.emptyList()).contains(target);
    }

    /**
     * 判断账户是否可过账（不抛异常版本）
     *
     * @param account 账户实体
     * @return true-可记账, false-不可记账
     */
    public static boolean isPostable(AccountPO account) {
        return account != null && account.getStatus() == AccountStatusEnum.NORMAL;
    }
}
