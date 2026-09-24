// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/AccountScopeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账户作用域枚举：1-内部分户，2-外部分户。
 */
@Getter
@AllArgsConstructor
public enum AccountScopeEnum {

    INTERNAL(1, "内部分户"),
    EXTERNAL(2, "外部分户");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}