// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/VoucherStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 凭证状态枚举：1-未过账，2-过账中，3-已过账，4-过账失败，5-已冲销。
 */
@Getter
@AllArgsConstructor
public enum VoucherStatusEnum {

    PENDING(1, "未过账"),
    POSTING(2, "过账中"),
    POSTED(3, "已过账"),
    FAILED(4, "过账失败"),
    REVERSED(5, "已冲销");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}