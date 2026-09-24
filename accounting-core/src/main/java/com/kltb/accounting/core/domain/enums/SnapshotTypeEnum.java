// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/SnapshotTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

/**
 * 快照类型枚举
 */
@Getter
public enum SnapshotTypeEnum {

    /**
     * 日快照
     */
    DAY(1, "DAY"),

    /**
     * 月快照
     */
    MONTH(2, "MONTH"),

    /**
     * 年快照
     */
    YEAR(3, "YEAR"),

    /**
     * 自定义快照
     */
    CUSTOM(4, "CUSTOM");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    SnapshotTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
