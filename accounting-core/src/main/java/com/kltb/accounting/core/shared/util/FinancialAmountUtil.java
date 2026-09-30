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
        if (amount == null) {
            return "零元整";
        }
        try {
            return Convert.digitToChinese(amount.doubleValue());
        } catch (Exception e) {
            return amount.toPlainString() + " 元整";
        }
    }
}
