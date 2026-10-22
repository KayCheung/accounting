package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 财务报表计算规则符号枚举
 * <p>
 * 对应 t_financial_report_item_mapping.calc_operator 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReportCalcOperatorEnum {

    ADD("+", "科目累加"),
    SUBTRACT("-", "科目抵扣/减项"),
    SUM_CATEGORY("SUM_CATEGORY", "分类小计"),
    SUM_TOTAL("SUM_TOTAL", "报表总计"),
    UNDISTRIBUTED_PROFIT("UNDISTRIBUTED_PROFIT", "未分配利润"),
    FORMULA("FORMULA", "公式计算");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ReportCalcOperatorEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ReportCalcOperatorEnum op : values()) {
            if (Objects.equals(op.getCode(), code)) {
                return op;
            }
        }
        return null;
    }
}
