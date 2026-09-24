// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TradeTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 交易类别枚举：1-正常，2-调账，3-红，4-蓝。
 */
@Getter
@AllArgsConstructor
public enum TradeTypeEnum {

    NORMAL(1, "正常"),
    ADJUSTMENT(2, "调账"),
    RED(3, "红"),
    BLUE(4, "蓝");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static boolean isValid(Integer code) {
        if (code == null) return false;
        for (TradeTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
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