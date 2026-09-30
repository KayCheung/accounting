package com.kltb.accounting.api.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 币种枚举（遵循 ISO 4217 国际标准与系统字典表对齐）
 */
@Getter
@AllArgsConstructor
public enum CurrencyEnum {

    /**
     * 人民币
     */
    CNY("CNY", "人民币"),

    /**
     * 美元
     */
    USD("USD", "美元"),

    /**
     * 欧元
     */
    EUR("EUR", "欧元"),

    /**
     * 港币
     */
    HKD("HKD", "港币");

    /**
     * 系统默认基础币种
     */
    public static final String DEFAULT_CURRENCY = "CNY";

    private final String code;
    private final String desc;

    public static CurrencyEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CurrencyEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
