package com.kltb.accounting.api.constant;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /**
     * 默认通用业务线编码（适用于常规资金冻结、通用非特定业务线场景）
     */
    public static final String DEFAULT_BUSINESS_CODE = "GENERAL";

    /**
     * 资金冻结交易编码
     */
    public static final String TRADING_CODE_FREEZE = "FREEZE";

    /**
     * 资金解冻交易编码
     */
    public static final String TRADING_CODE_UNFREEZE = "UNFREEZE";

    /**
     * 资金扣款交易编码
     */
    public static final String TRADING_CODE_DEDUCT = "DEDUCT";

    /**
     * 数据库日期字段 DDL 默认无效起始基准日期 (1970-01-01)
     */
    public static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);

    /**
     * 数据库时间字段 DDL 默认无效起始基准时间戳 (1970-01-01 00:00:00)
     */
    public static final LocalDateTime EPOCH_DATE_TIME = LocalDateTime.of(1970, 1, 1, 0, 0, 0);
}
