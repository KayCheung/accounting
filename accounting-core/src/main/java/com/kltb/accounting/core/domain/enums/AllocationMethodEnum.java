// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/AllocationMethodEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分摊方式枚举：1-不分摊，2-固定金额，3-按比例。
 */
@Getter
@AllArgsConstructor
public enum AllocationMethodEnum {

    NONE(1, "不分摊"),
    FIXED_AMOUNT(2, "固定金额"),
    PERCENTAGE(3, "按比例");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static AllocationMethodEnum fromCode(Integer code) {
        if (code == null) return null;
        for (AllocationMethodEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}