// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TransactionStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 事务状态枚举
 * <p>
 * 对应 t_transaction.status 字段。
 * 状态流转：PROCESSING → SUCCESS | FAILED
 * 回滚时必须同步更新 status=FAILED 并记录 fail_reason。
 */
@Getter
@RequiredArgsConstructor
public enum TransactionStatusEnum {

    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
