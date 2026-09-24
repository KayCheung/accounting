// 文件路径：accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/OpenAccountFlagEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 是否允许自动开户：0-否；1-是。
 */
@Getter
@AllArgsConstructor
public enum OpenAccountFlagEnum {

    DISABLED(0, "不允许自动开户"),
    ENABLED(1, "允许自动开户");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}

