// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/ChangeDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 增减方向枚举
 */
@Getter
@AllArgsConstructor
public enum ChangeDirectionEnum {

    /**
     * 增
     */
    INCREASE(1, "增"),

    /**
     * 减
     */
    DECREASE(2, "减");

    @EnumValue
    private final Integer code;

    @JsonValue
    private final String desc;

    private static final Map<Integer, ChangeDirectionEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(ChangeDirectionEnum::getCode, Function.identity()));

    /**
     * 根据数字编码查找枚举实例（空安全，未匹配时返回 null）
     */
    public static ChangeDirectionEnum fromCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    /**
     * 根据数字编码获取中文描述（空安全，未匹配时返回编码本身或空字符串）
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        ChangeDirectionEnum val = fromCode(code);
        return val != null ? val.getDesc() : String.valueOf(code);
    }

    /**
     * 判断状态码是否与当前枚举匹配
     */
    public boolean matches(Integer code) {
        return code != null && this.code.equals(code);
    }
}
