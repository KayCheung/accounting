// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/AvailableStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * 通用启用/停用状态枚举
 * <p>
 * 对应 t_dictionary.status / t_period_end_transfer_rule.status 字段。
 */
@Getter
@RequiredArgsConstructor
public enum AvailableStatusEnum {

    ENABLED(1, "启用"),
    DISABLED(2, "停用");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static AvailableStatusEnum fromCode(Integer code) {
        for (AvailableStatusEnum status : values()) {
            if (Objects.equals(status.getCode(), code)) {
                return status;
            }
        }
        return null;
    }
}
