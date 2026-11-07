// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/MessageStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 本地消息状态枚举
 * <p>
 * 对应 t_local_message.status 字段。
 * 状态流转：PENDING(1) → SENT(2) | FAILED(3)，CONFIRMED(4) 预留未来使用。
 * 重试超限后标记 FAILED 并触发告警。
 */
@Getter
@RequiredArgsConstructor
public enum MessageStatusEnum {

    PENDING(1, "待发送"),
    SENT(2, "已发送"),
    FAILED(3, "发送失败"),
    CONFIRMED(4, "已确认");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
