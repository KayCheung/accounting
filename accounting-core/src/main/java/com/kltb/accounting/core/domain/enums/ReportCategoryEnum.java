package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 财务报表大类分类枚举
 * <p>
 * 对应 t_financial_report_item_mapping.item_category 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReportCategoryEnum {

    // 资产端大类
    CURRENT_ASSET("CURRENT_ASSET", "流动资产"),
    NON_CURRENT_ASSET("NON_CURRENT_ASSET", "非流动资产"),
    ASSET_TOTAL("ASSET_TOTAL", "资产总计"),

    // 负债端大类
    CURRENT_LIABILITY("CURRENT_LIABILITY", "流动负债"),
    NON_CURRENT_LIABILITY("NON_CURRENT_LIABILITY", "非流动负债"),
    LIAB_TOTAL("LIAB_TOTAL", "负债合计"),

    // 所有者权益与合计
    EQUITY("EQUITY", "所有者权益"),
    LIAB_EQUITY_TOTAL("LIAB_EQUITY_TOTAL", "负债和所有者权益总计"),

    // 利润表大类
    OPERATING_REVENUE("OPERATING_REVENUE", "营业收入"),
    OPERATING_COST("OPERATING_COST", "营业成本"),
    EXPENSE("EXPENSE", "期间费用与税金"),
    INCOME("INCOME", "投资与营业外收益"),
    PROFIT("PROFIT", "利润指标");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ReportCategoryEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReportCategoryEnum item : values()) {
            if (Objects.equals(item.getCode(), code)) {
                return item;
            }
        }
        return null;
    }
}
