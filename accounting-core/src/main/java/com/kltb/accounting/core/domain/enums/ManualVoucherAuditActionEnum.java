// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/ManualVoucherAuditActionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 手工记账审批流转动作枚举
 */
@Getter
@AllArgsConstructor
public enum ManualVoucherAuditActionEnum {

    CREATE_DRAFT("CREATE_DRAFT", "保存草稿"),
    SUBMIT_AUDIT("SUBMIT_AUDIT", "提交初审"),
    UPDATE_DRAFT("UPDATE_DRAFT", "更新草稿"),
    RESUBMIT_AUDIT("RESUBMIT_AUDIT", "重新提交初审"),
    AUDIT_PASS("AUDIT_PASS", "初审通过"),
    AUDIT_REJECT("AUDIT_REJECT", "初审驳回"),
    REVIEW_PASS("REVIEW_PASS", "复核通过"),
    REVIEW_REJECT("REVIEW_REJECT", "复核驳回"),
    BOOKKEEPING("BOOKKEEPING", "确认记账入账"),
    CANCEL("CANCEL", "作废申请");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ManualVoucherAuditActionEnum fromCode(String code) {
        if (code == null) return null;
        for (ManualVoucherAuditActionEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }
}
