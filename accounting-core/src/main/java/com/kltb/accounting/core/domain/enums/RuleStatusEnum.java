// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/RuleStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 规则状态枚举：1-待启用，2-启用，3-停用。
 */
@Getter
@AllArgsConstructor
public enum RuleStatusEnum {

    PENDING(1, "待启用"),
    ENABLED(2, "启用"),
    DISABLED(3, "停用");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static RuleStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (RuleStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}