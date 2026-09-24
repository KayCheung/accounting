// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BalanceDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 余额方向枚举
 */
@Getter
public enum BalanceDirectionEnum {

    /**
     * 借
     */
    DEBIT(1, "借"),

    /**
     * 贷
     */
    CREDIT(2, "贷");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    BalanceDirectionEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static BalanceDirectionEnum fromCode(Integer code) {
        if (code == null) {
            return DEBIT;
        }
        for (BalanceDirectionEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return DEBIT;
    }
}
