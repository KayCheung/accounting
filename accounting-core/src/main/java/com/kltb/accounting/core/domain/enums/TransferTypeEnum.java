// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TransferTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 期末结转类型枚举
 * <p>
 * 对应 t_period_end_transfer_rule.transfer_type / t_period_end_transfer_record.transfer_type 字段。
 */
@Getter
@RequiredArgsConstructor
public enum TransferTypeEnum {

    PROFIT_LOSS(1, "损益结转"),
    COST(2, "成本结转"),
    CUSTOM(3, "自定义结转");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
