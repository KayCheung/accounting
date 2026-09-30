package com.kltb.accounting.api.constant;

/**
 * 币种相关基础常量
 * <p>
 * 遵循“单一事实来源”架构原则：业务币种（如 CNY、USD、EUR、HKD 等）全面由系统字典表
 * t_dictionary(dict_type='currency') 动态驱动，此常量类仅保留底层系统级缺省记账本位币。
 */
public final class CurrencyConstant {

    private CurrencyConstant() {
        // 私有构造，防止实例化
    }

    /**
     * 系统默认记账本位币（人民币）
     */
    public static final String DEFAULT_CURRENCY = "CNY";
}
