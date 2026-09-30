// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/ManualVoucherOperatorRoleEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 手工记账操作人岗位角色枚举
 */
@Getter
@AllArgsConstructor
public enum ManualVoucherOperatorRoleEnum {

    MAKER("MAKER", "制单人"),
    AUDITOR("AUDITOR", "初审人"),
    REVIEWER("REVIEWER", "复核人"),
    BOOKKEEPER("BOOKKEEPER", "记账人"),
    SYSTEM("SYSTEM", "系统");

    @EnumValue
    @JsonValue
    private final String code;

    private final String desc;

    public static ManualVoucherOperatorRoleEnum fromCode(String code) {
        if (code == null) return null;
        for (ManualVoucherOperatorRoleEnum e : values()) {
            if (e.code.equalsIgnoreCase(code)) {
                return e;
            }
        }
        return null;
    }
}
