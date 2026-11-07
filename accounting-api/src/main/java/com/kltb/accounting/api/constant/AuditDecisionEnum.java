package com.kltb.accounting.api.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 审批决策枚举（初审/复核操作决策：通过 PASS / 驳回 REJECT）
 */
@Getter
@AllArgsConstructor
public enum AuditDecisionEnum {

    /**
     * 审核通过
     */
    PASS("PASS", "通过"),

    /**
     * 审核驳回
     */
    REJECT("REJECT", "驳回");

    private final String code;
    private final String desc;

    public boolean isPass() {
        return this == PASS;
    }

    public boolean isReject() {
        return this == REJECT;
    }

    public static AuditDecisionEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AuditDecisionEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return null;
    }

    public static boolean isPass(String code) {
        return PASS.code.equalsIgnoreCase(code);
    }
}
