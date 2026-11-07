// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TemplateStatusEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 账户模板状态枚举
 * <p>
 * 对应 t_account_template.status 字段。
 */
@Getter
@RequiredArgsConstructor
public enum TemplateStatusEnum {

    PENDING(1, "待启用"),
    ENABLED(2, "启用"),
    DISABLED(3, "停用");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static TemplateStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (TemplateStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
