// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TransferRecordStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 期末结转记录状态枚举
 * <p>
 * 对应 t_period_end_transfer_record.status 字段。
 */
@Getter
@RequiredArgsConstructor
public enum TransferRecordStatusEnum {

    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
