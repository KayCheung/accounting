package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 财务报表类型枚举
 * <p>
 * 对应 t_financial_report_item_mapping.report_type / t_financial_report_snapshot.report_type 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReportTypeEnum {

    BALANCE_SHEET("BALANCE_SHEET", "资产负债表"),
    INCOME_STATEMENT("INCOME_STATEMENT", "利润表"),
    CASH_FLOW("CASH_FLOW", "现金流量表");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ReportTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReportTypeEnum type : values()) {
            if (Objects.equals(type.getCode(), code)) {
                return type;
            }
        }
        return null;
    }
}
