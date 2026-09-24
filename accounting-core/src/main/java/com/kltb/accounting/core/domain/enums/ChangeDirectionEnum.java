// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/ChangeDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 增减方向枚举
 */
@Getter
public enum ChangeDirectionEnum {

    /**
     * 增
     */
    INCREASE(1, "增"),

    /**
     * 减
     */
    DECREASE(2, "减");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    ChangeDirectionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ChangeDirectionEnum fromCode(Integer code) {
        for (ChangeDirectionEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown ChangeDirectionEnum code: " + code);
    }
}
