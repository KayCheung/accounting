// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TransferDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 期末结转方向枚举
 * <p>
 * 对应 t_period_end_transfer_rule.transfer_direction 字段。
 */
@Getter
@RequiredArgsConstructor
public enum TransferDirectionEnum {

    DEBIT_TO_CREDIT(1, "借方余额结转到贷方"),
    CREDIT_TO_DEBIT(2, "贷方余额结转到借方");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
