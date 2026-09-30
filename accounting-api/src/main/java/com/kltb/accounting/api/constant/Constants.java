package com.kltb.accounting.api.constant;

/**
 * 系统通用聚合常量
 * <p>
 * 聚合系统级通用常量与基础默认值约定，避免微小常量类离散碎片化，后续通用常量均统一在此类维护。
 */
public final class Constants {

    private Constants() {
        // 私有构造，防止实例化
    }

    /**
     * 系统默认记账本位币（人民币）
     */
    public static final String DEFAULT_CURRENCY = "CNY";

    /**
     * 系统级默认操作员标识
     */
    public static final String SYSTEM_OPERATOR = "system";
}
