// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/BusinessRecordStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 业务记账流水状态枚举
 * <p>
 * 对应 t_business_record.status 字段。
 */
@Getter
@RequiredArgsConstructor
public enum BusinessRecordStatusEnum {

    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;
}
