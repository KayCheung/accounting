package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum;
import com.kltb.accounting.core.shared.exception.AccountException;

import java.math.BigDecimal;

/**
 * 余额计算器，提供过账余额计算的纯函数。
 * 不依赖任何 Spring Bean，不包含任何副作用。
 * 严格遵循金融核心财务律法与绝对值法则（余额始终 >= 0，严禁负数运算）。
 */
public final class AccountBalanceCalculator {

    private AccountBalanceCalculator() {}

    /**
     * 核心全场景账户余额与方向试算引擎（不指定显式增减方向时默认由系统推导）
     */
    public static AccountBalanceResult calculate(
            BigDecimal currentBalance,
            BalanceDirectionEnum currentDirection,
            SubjectBalanceDirectionEnum subjectDirection,
            DebitCreditEnum entryDebitCredit,
            BigDecimal amount) {
        return calculate(currentBalance, currentDirection, subjectDirection, entryDebitCredit, null, amount);
    }

    /**
     * 核心全场景账户余额与方向试算引擎
     *
     * @param currentBalance 当前账户余额（始终 >= 0）
     * @param currentDirection 当前账户余额方向（0-平/无, 1-借, 2-贷）
     * @param subjectDirection 所属科目余额方向（0-无(表外), 1-借, 2-贷, 3-双向）
     * @param entryDebitCredit 分录借贷方向（1-借, 2-贷）
     * @param entryChangeDirection 分录显式指定的增减方向（可为 null）
     * @param amount 变更金额（始终 > 0）
     * @return 试算结果（新余额、新余额方向、实际生效的增减方向）
     */
    public static AccountBalanceResult calculate(
            BigDecimal currentBalance,
            BalanceDirectionEnum currentDirection,
            SubjectBalanceDirectionEnum subjectDirection,
            DebitCreditEnum entryDebitCredit,
            ChangeDirectionEnum entryChangeDirection,
            BigDecimal amount) {

        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new AccountException(ResultCode.PARAM_ERROR, "记账金额必须大于0");
        }
        if (subjectDirection == null) {
            subjectDirection = SubjectBalanceDirectionEnum.DEBIT;
        }

        // ==================== 场景 1：表外科目 (0-无 / 不适用) ====================
        if (subjectDirection.isOffBalance()) {
            // 表外科目不适用“同向相加、异向相减”！
            // 判定增减方向：优先取分录显式指定的增减，若未指定按通用表外惯例：借=增(收入), 贷=减(付出)
            ChangeDirectionEnum effChangeDir = entryChangeDirection;
            if (effChangeDir == null) {
                effChangeDir = (entryDebitCredit == DebitCreditEnum.DEBIT)
                        ? ChangeDirectionEnum.INCREASE
                        : ChangeDirectionEnum.DECREASE;
            }

            BigDecimal newBal;
            if (effChangeDir == ChangeDirectionEnum.INCREASE) {
                newBal = currentBalance.add(amount);
            } else {
                if (currentBalance.compareTo(amount) < 0) {
                    throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                            "表外账户余额不足: current=" + currentBalance + ", need=" + amount);
                }
                newBal = currentBalance.subtract(amount);
            }
            return AccountBalanceResult.builder()
                    .newBalance(newBal)
                    .newDirection(BalanceDirectionEnum.FLAT) // 表外余额方向恒为 0 (无/平)
                    .changeDirection(effChangeDir)
                    .build();
        }

        // ==================== 场景 2：双向科目 (3-双向) ====================
        if (subjectDirection.isBidirectional()) {
            if (entryDebitCredit == null) {
                throw new AccountException(ResultCode.PARAM_ERROR, "双向科目入账分录借贷方向不能为空");
            }
            // 确定当前账户有效方向
            BalanceDirectionEnum currDir = currentDirection;
            if (currentBalance.compareTo(BigDecimal.ZERO) == 0 || currDir == null) {
                currDir = BalanceDirectionEnum.FLAT;
            }

            // 1. 若当前余额为 0（平）：分录直接建立新余额，方向取分录方向，增减效果为增
            if (currDir == BalanceDirectionEnum.FLAT) {
                BalanceDirectionEnum newDir = (entryDebitCredit == DebitCreditEnum.DEBIT)
                        ? BalanceDirectionEnum.DEBIT
                        : BalanceDirectionEnum.CREDIT;
                return AccountBalanceResult.builder()
                        .newBalance(amount)
                        .newDirection(newDir)
                        .changeDirection(ChangeDirectionEnum.INCREASE)
                        .build();
            }

            // 2. 若分录方向与当前账户余额方向同向：同向相加，方向不变，增减效果为增
            boolean isSameDirection = (currDir == BalanceDirectionEnum.DEBIT && entryDebitCredit == DebitCreditEnum.DEBIT)
                    || (currDir == BalanceDirectionEnum.CREDIT && entryDebitCredit == DebitCreditEnum.CREDIT);
            if (isSameDirection) {
                return AccountBalanceResult.builder()
                        .newBalance(currentBalance.add(amount))
                        .newDirection(currDir)
                        .changeDirection(ChangeDirectionEnum.INCREASE)
                        .build();
            }

            // 3. 若分录方向与当前账户余额方向异向：异向冲减或翻转
            int cmp = currentBalance.compareTo(amount);
            if (cmp > 0) {
                // 原方向余额充足，部分冲减，方向保持不变
                return AccountBalanceResult.builder()
                        .newBalance(currentBalance.subtract(amount))
                        .newDirection(currDir)
                        .changeDirection(ChangeDirectionEnum.DECREASE)
                        .build();
            } else if (cmp == 0) {
                // 正好两清轧平，余额归零，方向转为 FLAT
                return AccountBalanceResult.builder()
                        .newBalance(BigDecimal.ZERO)
                        .newDirection(BalanceDirectionEnum.FLAT)
                        .changeDirection(ChangeDirectionEnum.DECREASE)
                        .build();
            } else {
                // 方向穿透翻转 (Direction Flip)！
                // 原方向余额被全额冲销，超出部分建立反向新余额
                BigDecimal flippedBalance = amount.subtract(currentBalance);
                BalanceDirectionEnum flippedDir = (entryDebitCredit == DebitCreditEnum.DEBIT)
                        ? BalanceDirectionEnum.DEBIT
                        : BalanceDirectionEnum.CREDIT;
                return AccountBalanceResult.builder()
                        .newBalance(flippedBalance)
                        .newDirection(flippedDir)
                        .changeDirection(ChangeDirectionEnum.INCREASE)
                        .build();
            }
        }

        // ==================== 场景 3：单向科目 (1-借 或 2-贷) ====================
        BalanceDirectionEnum targetSubjectDir = (subjectDirection == SubjectBalanceDirectionEnum.CREDIT)
                ? BalanceDirectionEnum.CREDIT
                : BalanceDirectionEnum.DEBIT;

        // 确定增减方向：分录借贷与科目方向同向相加(增)，异向相减(减)
        ChangeDirectionEnum effChangeDir;
        if (entryChangeDirection != null) {
            effChangeDir = entryChangeDirection;
        } else {
            if (entryDebitCredit == null) {
                throw new AccountException(ResultCode.PARAM_ERROR, "分录借贷方向不能为空");
            }
            boolean isSame = (entryDebitCredit == DebitCreditEnum.DEBIT && targetSubjectDir == BalanceDirectionEnum.DEBIT)
                    || (entryDebitCredit == DebitCreditEnum.CREDIT && targetSubjectDir == BalanceDirectionEnum.CREDIT);
            effChangeDir = isSame ? ChangeDirectionEnum.INCREASE : ChangeDirectionEnum.DECREASE;
        }

        if (effChangeDir == ChangeDirectionEnum.INCREASE) {
            BigDecimal newBal = currentBalance.add(amount);
            return AccountBalanceResult.builder()
                    .newBalance(newBal)
                    .newDirection(targetSubjectDir)
                    .changeDirection(ChangeDirectionEnum.INCREASE)
                    .build();
        } else {
            if (currentBalance.compareTo(amount) < 0) {
                throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                        "余额不足: current=" + currentBalance + ", need=" + amount);
            }
            BigDecimal newBal = currentBalance.subtract(amount);
            BalanceDirectionEnum newDir = (newBal.compareTo(BigDecimal.ZERO) == 0)
                    ? BalanceDirectionEnum.FLAT
                    : targetSubjectDir;
            return AccountBalanceResult.builder()
                    .newBalance(newBal)
                    .newDirection(newDir)
                    .changeDirection(ChangeDirectionEnum.DECREASE)
                    .build();
        }
    }

    /**
     * 计算新余额
     *
     * @param currentBalance  当前余额（始终 >= 0）
     * @param amount          变更金额（始终 > 0）
     * @param changeDirection 增减方向枚举
     * @return 新余额
     * @throws AccountException 当余额不足或增减方向为空时抛出
     */
    public static BigDecimal calculateNewBalance(
            BigDecimal currentBalance,
            BigDecimal amount,
            ChangeDirectionEnum changeDirection) {
        if (changeDirection == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "增减方向不能为空");
        }
        if (currentBalance == null) {
            currentBalance = BigDecimal.ZERO;
        }

        if (changeDirection == ChangeDirectionEnum.INCREASE) {
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

    /**
     * 计算新余额（兼容历史数字方法，委托枚举核心实现）
     *
     * @param currentBalance  当前余额（始终 >= 0）
     * @param amount          变更金额（始终 > 0）
     * @param changeDirection 增减方向（1=增, 2=减）
     * @return 新余额
     * @throws AccountException 当余额不足或未知方向时抛出
     */
    public static BigDecimal calculateNewBalance(
            BigDecimal currentBalance,
            BigDecimal amount,
            int changeDirection) {
        ChangeDirectionEnum dir = ChangeDirectionEnum.fromCode(changeDirection);
        if (dir == null) {
            throw new AccountException(ResultCode.PARAM_ERROR, "未知的增减方向: " + changeDirection);
        }
        return calculateNewBalance(currentBalance, amount, dir);
    }
}
