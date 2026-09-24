// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BalanceTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 余额类型枚举
 */
@Getter
public enum BalanceTypeEnum {

    /**
     * 可用余额
     */
    AVAILABLE(1, "可用余额"),

    /**
     * 冻结余额
     */
    FROZEN(2, "冻结余额");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    BalanceTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
