// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/FreezeStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 冻结状态枚举
 */
@Getter
public enum FreezeStatusEnum {

    /**
     * 冻结
     */
    FROZEN(1, "冻结"),

    /**
     * 已解冻
     */
    UNFROZEN(2, "已解冻");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    FreezeStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
