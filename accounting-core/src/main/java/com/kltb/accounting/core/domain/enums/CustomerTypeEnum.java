// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/CustomerTypeEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 客户类型枚举
 * <p>
 * 对应 t_account_template.customer_type / t_business_detail.customer_type 字段。
 */
@Getter
@RequiredArgsConstructor
public enum CustomerTypeEnum {

    PERSONAL(1, "个人"),
    ENTERPRISE(2, "企业"),
    OTHER(99, "其他");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static boolean isValid(Integer code) {
        if (code == null) return false;
        for (CustomerTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public static CustomerTypeEnum fromCode(Integer code) {
        if (code == null) return null;
        for (CustomerTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static CustomerTypeEnum fromValue(Integer code) {
        if (code == null) {
            throw new IllegalArgumentException("客户类型编码不能为空");
        }
        for (CustomerTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("无效的客户类型编码: " + code);
    }
}
