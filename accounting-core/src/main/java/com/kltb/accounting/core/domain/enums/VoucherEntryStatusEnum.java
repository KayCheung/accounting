// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/VoucherEntryStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 分录状态枚举：1-未过账，2-已过账，3-过账失败。
 */
@Getter
@AllArgsConstructor
public enum VoucherEntryStatusEnum {

    PENDING(1, "未过账"),
    POSTED(2, "已过账"),
    FAILED(3, "过账失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}