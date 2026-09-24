// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/PostingTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入账类型枚举：1-手工凭证，2-机制凭证。
 */
@Getter
@AllArgsConstructor
public enum PostingTypeEnum {

    MANUAL(1, "手工凭证"),
    AUTOMATIC(2, "机制凭证");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}