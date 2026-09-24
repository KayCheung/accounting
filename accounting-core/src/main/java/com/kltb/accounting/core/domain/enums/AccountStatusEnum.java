// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/AccountStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 账户状态枚举
 */
@Getter
public enum AccountStatusEnum {

    /**
     * 正常
     */
    NORMAL(1, "正常"),

    /**
     * 冻结
     */
    FROZEN(2, "冻结"),

    /**
     * 注销
     */
    CANCELLED(3, "注销");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    AccountStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AccountStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (AccountStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
