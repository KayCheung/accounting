// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/PeriodCycleEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;

/**
 * 期末结转周期枚举
 * <p>
 * 对应 t_period_end_transfer_rule.period_cycle 字段。
 * 用于定义规则是按日、月末、季末、年末触发，还是仅支持手动执行。
 */
@Getter
@RequiredArgsConstructor
public enum PeriodCycleEnum {

    DAILY(1, "每日/日结"),
    MONTHLY(2, "月末/月结"),
    QUARTERLY(3, "季末/季结"),
    YEARLY(4, "年末/年结"),
    MANUAL(5, "仅手动/自定义");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static PeriodCycleEnum fromCode(Integer code) {
        if (code == null) return null;
        for (PeriodCycleEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 判断在指定的会计日期下，当前结转周期是否满足自动触发条件
     *
     * @param accountingDate 会计日期
     * @return true-满足触发条件, false-不满足
     */
    public boolean isTriggerable(LocalDate accountingDate) {
        if (accountingDate == null) {
            return false;
        }
        LocalDate lastDayOfMonth = accountingDate.with(TemporalAdjusters.lastDayOfMonth());
        boolean isMonthEnd = accountingDate.equals(lastDayOfMonth);

        return switch (this) {
            case DAILY -> true;
            case MONTHLY -> isMonthEnd;
            case QUARTERLY -> {
                Month m = accountingDate.getMonth();
                yield isMonthEnd && (m == Month.MARCH || m == Month.JUNE || m == Month.SEPTEMBER || m == Month.DECEMBER);
            }
            case YEARLY -> isMonthEnd && accountingDate.getMonth() == Month.DECEMBER;
            case MANUAL -> false;
        };
    }
}
