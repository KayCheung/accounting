// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/OwnerTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 所有者类型枚举：1-个人，2-企业，99-其他。
 */
@Getter
@AllArgsConstructor
public enum OwnerTypeEnum {

    INDIVIDUAL(1, "个人"),
    ENTERPRISE(2, "企业"),
    OTHER(99, "其他");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static OwnerTypeEnum fromCode(Integer code) {
        if (code == null) {
            return OTHER;
        }
        for (OwnerTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return OTHER;
    }
}