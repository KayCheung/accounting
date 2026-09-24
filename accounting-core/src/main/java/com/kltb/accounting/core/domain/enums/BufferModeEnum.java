// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BufferModeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缓冲入账模式枚举：1-异步逐条，2-日间批量，3-日终批量。
 */
@Getter
@AllArgsConstructor
public enum BufferModeEnum {

    ASYNC_SINGLE(1, "异步逐条"),
    DAILY_BATCH(2, "日间批量"),
    EOD_BATCH(3, "日终批量");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static BufferModeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (BufferModeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}