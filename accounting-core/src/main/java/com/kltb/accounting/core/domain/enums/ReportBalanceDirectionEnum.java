package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 财务报表取数余额方向枚举
 * <p>
 * 对应 t_financial_report_item_mapping.balance_direction 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReportBalanceDirectionEnum {

    AUTO("AUTO", "跟随科目本身性质"),
    DEBIT("DEBIT", "仅取借方发生/余额"),
    CREDIT("CREDIT", "仅取贷方发生/余额");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ReportBalanceDirectionEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReportBalanceDirectionEnum dir : values()) {
            if (Objects.equals(dir.getCode(), code)) {
                return dir;
            }
        }
        return null;
    }
}
