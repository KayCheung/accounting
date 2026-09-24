// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/SubjectCategoryEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 会计科目账类枚举
 * <p>
 * 对应 t_account_subject.subject_category 字段。
 * 六大类科目（资产/负债/权益/共同/成本/损益）+ 表外科目。
 */
@Getter
@RequiredArgsConstructor
public enum SubjectCategoryEnum {

    OFF_BALANCE(0, "表外科目"),
    ASSET(1, "资产类"),
    LIABILITY(2, "负债类"),
    EQUITY(3, "权益类"),
    COMMON(4, "共同类"),
    COST(5, "成本类"),
    PROFIT_LOSS(6, "损益类");

    /** 持久化值 */
    @EnumValue
    @JsonValue
    private final Integer code;

    /** 中文描述 */
    private final String desc;

    public static SubjectCategoryEnum fromCode(Integer code) {
        if (code == null) return null;
        for (SubjectCategoryEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
