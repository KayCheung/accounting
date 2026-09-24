// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/SubjectNatureEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 科目性质枚举
 * <p>
 * 对应 t_account_subject.nature 字段。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectNatureEnum {

    NORMAL(1, "非特殊性科目"),
    WRITE_OFF(2, "销账类科目"),
    LOAN(3, "贷款类科目"),
    CASH(4, "现金类科目");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static SubjectNatureEnum fromCode(Integer code) {
        if (code == null) return null;
        for (SubjectNatureEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
