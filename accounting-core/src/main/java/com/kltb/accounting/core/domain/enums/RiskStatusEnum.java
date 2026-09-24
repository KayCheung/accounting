// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/RiskStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 风控状态枚举
 */
@Getter
public enum RiskStatusEnum {

    /**
     * 正常
     */
    NORMAL(1, "正常"),

    /**
     * 止入
     */
    NO_IN(2, "止入"),

    /**
     * 止出
     */
    NO_OUT(3, "止出"),

    /**
     * 止入止出
     */
    NO_IN_OUT(4, "止入止出");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    RiskStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RiskStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (RiskStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
