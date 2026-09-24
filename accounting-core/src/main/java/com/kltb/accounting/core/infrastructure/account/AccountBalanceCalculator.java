package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.exception.AccountException;
import java.math.BigDecimal;

/**
 * 余额计算器，提供过账余额计算的纯函数。
 * 不依赖任何 Spring Bean，不包含任何副作用。
 */
public final class AccountBalanceCalculator {

    private AccountBalanceCalculator() {}

    /**
     * 计算新余额
     *
     * @param currentBalance  当前余额（始终 >= 0）
     * @param amount          变更金额（始终 > 0）
     * @param changeDirection 增减方向（1=增, 2=减）
     * @return 新余额
     * @throws AccountException 当余额不足时抛出
     */
    public static BigDecimal calculateNewBalance(
        BigDecimal currentBalance,
        BigDecimal amount,
        int changeDirection) {

        if (changeDirection == 1) {
            // 余额增加：同向相加
            return currentBalance.add(amount);
        } else {
            // 余额减少：反向相减，前置校验余额充足
            if (currentBalance.compareTo(amount) < 0) {
                throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                    "余额不足: current=" + currentBalance + ", need=" + amount);
            }
            return currentBalance.subtract(amount);
        }
    }
}
