package com.kltb.accounting.api.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 手工记账申请制单/编辑保存操作类型
 */
@Getter
@AllArgsConstructor
public enum ManualVoucherSaveActionEnum {

    /**
     * 保存草稿
     */
    DRAFT("DRAFT", "保存草稿"),

    /**
     * 提交初审
     */
    SUBMIT("SUBMIT", "提交初审");

    private final String code;
    private final String desc;

    public boolean isDraft() {
        return this == DRAFT;
    }

    public static ManualVoucherSaveActionEnum fromCode(String code) {
        if (code == null) {
            return SUBMIT;
        }
        for (ManualVoucherSaveActionEnum item : values()) {
            if (item.code.equalsIgnoreCase(code)) {
                return item;
            }
        }
        return SUBMIT;
    }
}
