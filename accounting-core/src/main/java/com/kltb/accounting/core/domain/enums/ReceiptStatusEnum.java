// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/ReceiptStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 消息回执状态枚举
 * <p>
 * 对应 t_message_receipt.status 字段。
 */
@Getter
@RequiredArgsConstructor
public enum ReceiptStatusEnum {

    SUCCESS(1, "成功"),
    FAILED(2, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
