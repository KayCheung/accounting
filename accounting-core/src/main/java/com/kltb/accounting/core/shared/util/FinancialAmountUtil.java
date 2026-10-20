package com.kltb.accounting.core.shared.util;

import cn.hutool.core.convert.Convert;

import java.math.BigDecimal;

/**
 * 财务金额与币种格式化公共工具类
 */
public final class FinancialAmountUtil {

    private FinancialAmountUtil() {
        // 私有构造，工具类禁止实例化
    }

    /**
     * 将金额转换为中文大写（如 100.50 -> 壹佰元伍角整）
     *
     * @param amount 金额对象
     * @return 中文大写字符串
     */
    public static String toChineseWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            return "零元整";
        }
        try {
            return Convert.digitToChinese(amount.doubleValue());
        } catch (Exception e) {
            return amount.toPlainString() + " 元整";
        }
    }

    /**
     * 安全地将对象转换为 BigDecimal，若为 null 或解析失败则返回 BigDecimal.ZERO
     *
     * @param value 待转换对象（支持 BigDecimal、Number、数字字符串等）
     * @return BigDecimal 结果
     */
    public static BigDecimal toBigDecimal(Object value) {
        return toBigDecimal(value, BigDecimal.ZERO);
    }

    /**
     * 安全地将对象转换为 BigDecimal，若为 null 或解析失败则返回指定的默认值
     *
     * @param value        待转换对象（支持 BigDecimal、Number、数字字符串等）
     * @param defaultValue 默认值
     * @return BigDecimal 结果
     */
    public static BigDecimal toBigDecimal(Object value, BigDecimal defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        if (value instanceof CharSequence) {
            String str = value.toString().trim();
            if (str.isEmpty()) {
                return defaultValue;
            }
            try {
                return new BigDecimal(str);
            } catch (Exception e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 安全地将对象转换为 BigDecimal，若为 null 或解析失败则返回 null
     *
     * @param value 待转换对象
     * @return BigDecimal 结果，无法解析时返回 null
     */
    public static BigDecimal toBigDecimalOrNull(Object value) {
        return toBigDecimal(value, null);
    }
}
