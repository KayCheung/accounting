package com.kltb.accounting.core.shared.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialAmountUtilTest {

    @Test
    @DisplayName("toBigDecimal: null 输入应返回 BigDecimal.ZERO")
    void toBigDecimal_nullInput_shouldReturnZero() {
        assertThat(FinancialAmountUtil.toBigDecimal(null)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("toBigDecimal: BigDecimal 输入应直接返回该对象")
    void toBigDecimal_bigDecimalInput_shouldReturnSame() {
        BigDecimal val = new BigDecimal("123.45");
        assertThat(FinancialAmountUtil.toBigDecimal(val)).isSameAs(val);
    }

    @Test
    @DisplayName("toBigDecimal: 各种 Number 类型安全转换")
    void toBigDecimal_numberInput_shouldConvertAccurately() {
        assertThat(FinancialAmountUtil.toBigDecimal(100)).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(FinancialAmountUtil.toBigDecimal(10000000000L)).isEqualByComparingTo(new BigDecimal("10000000000"));
        assertThat(FinancialAmountUtil.toBigDecimal(123.45d)).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(FinancialAmountUtil.toBigDecimal(12.5f)).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(FinancialAmountUtil.toBigDecimal(new BigInteger("999999"))).isEqualByComparingTo(new BigDecimal("999999"));
    }

    @Test
    @DisplayName("toBigDecimal: 字符串数值输入应安全解析并处理首尾空格")
    void toBigDecimal_stringInput_shouldParseCorrectly() {
        assertThat(FinancialAmountUtil.toBigDecimal("123.45")).isEqualByComparingTo(new BigDecimal("123.45"));
        assertThat(FinancialAmountUtil.toBigDecimal("  678.90  ")).isEqualByComparingTo(new BigDecimal("678.90"));
        assertThat(FinancialAmountUtil.toBigDecimal("-50.00")).isEqualByComparingTo(new BigDecimal("-50.00"));
    }

    @Test
    @DisplayName("toBigDecimal: 空白或非法字符串应安全返回 BigDecimal.ZERO")
    void toBigDecimal_invalidString_shouldReturnZero() {
        assertThat(FinancialAmountUtil.toBigDecimal("")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(FinancialAmountUtil.toBigDecimal("   ")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(FinancialAmountUtil.toBigDecimal("invalid-amount")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("toBigDecimal: 不受支持的任意对象应安全返回 BigDecimal.ZERO")
    void toBigDecimal_unsupportedObject_shouldReturnZero() {
        assertThat(FinancialAmountUtil.toBigDecimal(new Object())).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("toBigDecimal(value, defaultValue): 支持自定义默认值")
    void toBigDecimal_withDefaultValue_shouldReturnDefaultOnFailure() {
        BigDecimal defaultVal = new BigDecimal("99.99");
        assertThat(FinancialAmountUtil.toBigDecimal(null, defaultVal)).isEqualByComparingTo(defaultVal);
        assertThat(FinancialAmountUtil.toBigDecimal("abc", defaultVal)).isEqualByComparingTo(defaultVal);
        assertThat(FinancialAmountUtil.toBigDecimal("10.00", defaultVal)).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("toBigDecimalOrNull: 无法转换时返回 null")
    void toBigDecimalOrNull_shouldReturnNullWhenInvalid() {
        assertThat(FinancialAmountUtil.toBigDecimalOrNull(null)).isNull();
        assertThat(FinancialAmountUtil.toBigDecimalOrNull("")).isNull();
        assertThat(FinancialAmountUtil.toBigDecimalOrNull("invalid")).isNull();
        assertThat(FinancialAmountUtil.toBigDecimalOrNull(new Object())).isNull();
        assertThat(FinancialAmountUtil.toBigDecimalOrNull("123.45")).isEqualByComparingTo(new BigDecimal("123.45"));
    }

    @Test
    @DisplayName("PERCENT_BASE: 验证百分比换算基数常量正确性")
    void percentBase_shouldEqualHundred() {
        assertThat(FinancialAmountUtil.PERCENT_BASE).isNotNull();
        assertThat(FinancialAmountUtil.PERCENT_BASE).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(FinancialAmountUtil.PERCENT_BASE.scale()).isZero();
    }

    @Test
    @DisplayName("toChineseWords: 中文大写金额转换")
    void toChineseWords_shouldFormatCorrectly() {
        assertThat(FinancialAmountUtil.toChineseWords(null)).isEqualTo("零元整");
        assertThat(FinancialAmountUtil.toChineseWords(BigDecimal.ZERO)).isEqualTo("零元整");
        assertThat(FinancialAmountUtil.toChineseWords(new BigDecimal("100.00"))).isEqualTo("壹佰元整");
        assertThat(FinancialAmountUtil.toChineseWords(new BigDecimal("100.50"))).contains("壹佰元").contains("伍角");
    }
}
