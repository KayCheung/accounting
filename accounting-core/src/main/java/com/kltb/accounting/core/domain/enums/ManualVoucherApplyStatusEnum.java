package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 手工记账申请审批流转状态领域枚举：
 * 1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废
 */
@Getter
@AllArgsConstructor
public enum ManualVoucherApplyStatusEnum {

    DRAFT(1, "草稿"),
    PENDING_AUDIT(2, "待初审"),
    AUDIT_REJECTED(3, "初审驳回"),
    PENDING_REVIEW(4, "待复核"),
    REVIEW_REJECTED(5, "复核驳回"),
    PENDING_BOOKKEEPING(6, "待记账"),
    BOOKED(7, "已记账"),
    CANCELLED(8, "已作废");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static ManualVoucherApplyStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ManualVoucherApplyStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
