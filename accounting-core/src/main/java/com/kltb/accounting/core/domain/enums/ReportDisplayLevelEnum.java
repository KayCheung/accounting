package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 财务报表展示层级枚举
 * <p>
 * 对应 t_financial_report_item_mapping.display_level 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReportDisplayLevelEnum {

    HEADER(1, "大类标题"),
    DETAIL(2, "数据明细项"),
    TOTAL(3, "合计小计项");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static ReportDisplayLevelEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ReportDisplayLevelEnum level : values()) {
            if (Objects.equals(level.getCode(), code)) {
                return level;
            }
        }
        return null;
    }
}
