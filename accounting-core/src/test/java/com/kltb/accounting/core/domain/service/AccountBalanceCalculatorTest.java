package com.kltb.accounting.core.domain.service;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountBalanceCalculatorTest {

    @Test
    @DisplayName("余额增加: changeDirection=1 -> oldBalance + amount")
    void calculate_increase_shouldAdd() {
        BigDecimal result = AccountBalanceCalculator.calculateNewBalance(
                new BigDecimal("1000"), new BigDecimal("500"), 1);
        assertThat(result).isEqualTo(new BigDecimal("1500"));
    }

    @Test
    @DisplayName("余额减少: changeDirection=2 余额充足 -> oldBalance - amount")
    void calculate_decrease_sufficient_shouldSubtract() {
        BigDecimal result = AccountBalanceCalculator.calculateNewBalance(
                new BigDecimal("1000"), new BigDecimal("300"), 2);
        assertThat(result).isEqualTo(new BigDecimal("700"));
    }

    @Test
    @DisplayName("余额减少: changeDirection=2 余额不足 -> 抛出 INSUFFICIENT_BALANCE")
    void calculate_decrease_insufficient_shouldThrow() {
        assertThatThrownBy(() -> AccountBalanceCalculator.calculateNewBalance(
                new BigDecimal("100"), new BigDecimal("500"), 2))
                .isInstanceOf(com.kltb.accounting.core.shared.exception.AccountException.class)
                .satisfies(ex -> {
                    com.kltb.accounting.core.shared.exception.AccountException e =
                            (com.kltb.accounting.core.shared.exception.AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE);
                    assertThat(e.getMessage()).contains("余额不足");
                });
    }

    @Test
    @DisplayName("余额减少: changeDirection=2 余额刚好相等 -> 返回0")
    void calculate_decrease_exact_shouldReturnZero() {
        BigDecimal result = AccountBalanceCalculator.calculateNewBalance(
                new BigDecimal("1000"), new BigDecimal("1000"), 2);
        assertThat(result).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("余额增加: 从零开始增加")
    void calculate_increase_fromZero_shouldWork() {
        BigDecimal result = AccountBalanceCalculator.calculateNewBalance(
                BigDecimal.ZERO, new BigDecimal("100"), 1);
        assertThat(result).isEqualTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("余额精度: 处理6位小数")
    void calculate_precision_sixDecimalPlaces() {
        BigDecimal result = AccountBalanceCalculator.calculateNewBalance(
                new BigDecimal("100.123456"), new BigDecimal("0.000001"), 1);
        assertThat(result).isEqualTo(new BigDecimal("100.123457"));
    }
}
