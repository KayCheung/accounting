// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BufferStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 缓冲入账状态枚举：1-待入账，2-处理中，3-成功，4-失败。
 */
@Getter
@AllArgsConstructor
public enum BufferStatusEnum {

    PENDING(1, "待入账"),
    PROCESSING(2, "处理中"),
    SUCCESS(3, "成功"),
    FAILED(4, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static BufferStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (BufferStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}