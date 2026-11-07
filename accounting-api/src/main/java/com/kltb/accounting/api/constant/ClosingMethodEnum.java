package com.kltb.accounting.api.constant;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 资产负债表结账核算方式枚举
 */
@Getter
@RequiredArgsConstructor
public enum ClosingMethodEnum {

    /**
     * 表结法：报表层动态汇算当期未结转净损益并纳入未分配利润，资产负债表随时随地保持平衡（推荐日常经营与动态核算）
     */
    REPORT("REPORT", "表结法", "动态汇算当期未结转损益并计入未分配利润，资产负债实时保持平衡"),

    /**
     * 账结法：严格依据总账实际已过账科目余额，未执行期末损益结转时如实反映账面差额（用于期末关账审计）
     */
    ACCOUNT("ACCOUNT", "账结法", "严格依据总账实际过账科目余额，未执行期末结转时展现账面差额");

    @JsonValue
    private final String code;

    private final String desc;

    private final String detail;

    public static ClosingMethodEnum fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return REPORT; // 默认表结法
        }
        for (ClosingMethodEnum value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return REPORT;
    }

    public boolean isReport() {
        return this == REPORT;
    }

    public boolean isAccount() {
        return this == ACCOUNT;
    }
}
