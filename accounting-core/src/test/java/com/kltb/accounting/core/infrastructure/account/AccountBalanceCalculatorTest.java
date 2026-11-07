package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AccountBalanceCalculator 余额计算纯函数单元测试
 * 覆盖：
 * 1. 单向科目（借/贷）同向增加、异向冲减、余额不足拦截
 * 2. 双向科目（共同类）：0余额建仓、同向相加、异向冲减、跨零方向翻转（绝对值法则）
 * 3. 表外科目：无借贷方向限制，纯按收增付减记账
 * 4. 边界参数防御：负数、空参数拦截
 */
class AccountBalanceCalculatorTest {

    // ==================== 1. 单向借方科目测试 ====================

    @Test
    @DisplayName("单向借方科目: 借方入账 -> 同向增加")
    void calculate_unilateralDebit_debitEntry_shouldIncrease() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.DEBIT,
                new BigDecimal("50.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("单向借方科目: 贷方入账且余额充足 -> 异向冲减")
    void calculate_unilateralDebit_creditEntry_sufficientBalance_shouldDecrease() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.CREDIT,
                new BigDecimal("40.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE);
    }

    @Test
    @DisplayName("单向借方科目: 贷方入账且冲减至零 -> 方向转为 FLAT")
    void calculate_unilateralDebit_creditEntry_exactBalance_shouldBecomeFlat() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.CREDIT,
                new BigDecimal("100.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.FLAT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE);
    }

    @Test
    @DisplayName("单向借方科目: 贷方冲减超额 -> 抛出 INSUFFICIENT_BALANCE 异常，禁止负数")
    void calculate_unilateralDebit_creditEntry_insufficientBalance_shouldThrow() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.CREDIT,
                new BigDecimal("150.00")
        )).isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE));
    }

    // ==================== 2. 单向贷方科目测试 ====================

    @Test
    @DisplayName("单向贷方科目: 贷方入账 -> 同向增加")
    void calculate_unilateralCredit_creditEntry_shouldIncrease() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("200.00"),
                BalanceDirectionEnum.CREDIT,
                SubjectBalanceDirectionEnum.CREDIT,
                DebitCreditEnum.CREDIT,
                new BigDecimal("80.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("280.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.CREDIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("单向贷方科目: 借方冲减超额 -> 抛出 INSUFFICIENT_BALANCE 异常")
    void calculate_unilateralCredit_debitEntry_insufficientBalance_shouldThrow() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculate(
                new BigDecimal("50.00"),
                BalanceDirectionEnum.CREDIT,
                SubjectBalanceDirectionEnum.CREDIT,
                DebitCreditEnum.DEBIT,
                new BigDecimal("100.00")
        )).isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE));
    }

    // ==================== 3. 双向科目（共同类/往来互抵）测试 ====================

    @Test
    @DisplayName("双向科目: 初始余额为0(FLAT) + 借方入账 -> 建仓借方余额")
    void calculate_bothDirection_initialFlat_debitEntry() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                BigDecimal.ZERO,
                BalanceDirectionEnum.FLAT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.DEBIT,
                new BigDecimal("500.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("双向科目: 初始余额为0(FLAT) + 贷方入账 -> 建仓贷方余额")
    void calculate_bothDirection_initialFlat_creditEntry() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                BigDecimal.ZERO,
                BalanceDirectionEnum.FLAT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.CREDIT,
                new BigDecimal("800.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.CREDIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("双向科目: 当前借方余额 + 贷方冲减未穿透 -> 仍为借方，正常冲减")
    void calculate_bothDirection_currentDebit_creditUnderflow() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("1000.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.CREDIT,
                new BigDecimal("400.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE);
    }

    @Test
    @DisplayName("双向科目: 当前借方余额 + 贷方冲减恰好为0 -> 余额为0，方向为 FLAT")
    void calculate_bothDirection_currentDebit_creditExactZero() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("1000.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.CREDIT,
                new BigDecimal("1000.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.FLAT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE);
    }

    @Test
    @DisplayName("双向科目: 当前借方余额 + 贷方超额冲减 -> 严格遵循绝对值法则，余额翻转为贷方，不抛异常也不产生负数")
    void calculate_bothDirection_currentDebit_creditOverflow_flipToCredit() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.CREDIT,
                new BigDecimal("250.00")
        );

        // 250 - 100 = 150，方向翻转为贷方（新方向建仓增加）
        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.CREDIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("双向科目: 当前贷方余额 + 借方超额冲减 -> 余额翻转为借方")
    void calculate_bothDirection_currentCredit_debitOverflow_flipToDebit() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("300.00"),
                BalanceDirectionEnum.CREDIT,
                SubjectBalanceDirectionEnum.BOTH,
                DebitCreditEnum.DEBIT,
                new BigDecimal("500.00")
        );

        // 500 - 300 = 200，方向翻转为借方（新方向建仓增加）
        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    // ==================== 4. 表外科目测试 ====================

    @Test
    @DisplayName("表外科目: 借方入账 -> 增加")
    void calculate_offBalance_debitEntry_increase() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("5000.00"),
                BalanceDirectionEnum.FLAT,
                SubjectBalanceDirectionEnum.NONE,
                DebitCreditEnum.DEBIT,
                new BigDecimal("2000.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("7000.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.FLAT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.INCREASE);
    }

    @Test
    @DisplayName("表外科目: 贷方入账且余额充足 -> 减少")
    void calculate_offBalance_creditEntry_decrease() {
        AccountBalanceResult result = AccountBalanceCalculator.calculate(
                new BigDecimal("5000.00"),
                BalanceDirectionEnum.FLAT,
                SubjectBalanceDirectionEnum.NONE,
                DebitCreditEnum.CREDIT,
                new BigDecimal("2000.00")
        );

        assertThat(result.getNewBalance()).isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(result.getNewDirection()).isEqualTo(BalanceDirectionEnum.FLAT);
        assertThat(result.getChangeDirection()).isEqualTo(ChangeDirectionEnum.DECREASE);
    }

    @Test
    @DisplayName("表外科目: 贷方减少超额 -> 抛出 INSUFFICIENT_BALANCE 异常")
    void calculate_offBalance_creditEntry_insufficient_throws() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.FLAT,
                SubjectBalanceDirectionEnum.NONE,
                DebitCreditEnum.CREDIT,
                new BigDecimal("200.00")
        )).isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE));
    }

    // ==================== 5. 异常防护测试 ====================

    @Test
    @DisplayName("参数防御: 金额为负数 -> 抛出 PARAM_ERROR")
    void calculate_negativeAmount_shouldThrow() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.DEBIT,
                new BigDecimal("-50.00")
        )).isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("参数防御: 金额为 null -> 抛出 PARAM_ERROR")
    void calculate_nullAmount_shouldThrow() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculate(
                new BigDecimal("100.00"),
                BalanceDirectionEnum.DEBIT,
                SubjectBalanceDirectionEnum.DEBIT,
                DebitCreditEnum.DEBIT,
                null
        )).isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }
}
