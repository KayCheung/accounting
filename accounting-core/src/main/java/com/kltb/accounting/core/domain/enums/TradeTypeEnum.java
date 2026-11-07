// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TradeTypeEnum.java
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
 * 交易类别枚举：1-正常，2-调账，3-红，4-蓝。
 */
@Getter
@AllArgsConstructor
public enum TradeTypeEnum {

    NORMAL(1, "正常"),
    ADJUSTMENT(2, "调账"),
    RED(3, "红"),
    BLUE(4, "蓝"),
    PRE_FREEZE(5, "预冻结"),
    PRE_UNFREEZE(6, "预冻结解冻");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    private static final Map<Integer, TradeTypeEnum> CODE_MAP = Arrays.stream(values())
            .collect(Collectors.toMap(TradeTypeEnum::getCode, Function.identity()));

    /**
     * 根据编码查找枚举实例（空安全，未匹配时返回 null）
     */
    public static TradeTypeEnum getByCode(Integer code) {
        return code == null ? null : CODE_MAP.get(code);
    }

    /**
     * 根据编码获取中文描述（空安全，未匹配时返回编码本身或空字符串）
     */
    public static String getDescByCode(Integer code) {
        if (code == null) {
            return "";
        }
        TradeTypeEnum type = getByCode(code);
        return type != null ? type.getDesc() : String.valueOf(code);
    }

    public static boolean isValid(Integer code) {
        return code != null && CODE_MAP.containsKey(code);
    }

    public static TradeTypeEnum fromCode(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("交易类别编码不能为空");
        }
        for (TradeTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的交易类别编码: " + code);
    }
}