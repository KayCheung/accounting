// accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/TransferDirectionEnum.java
package com.kltb.accounting.core.domain.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 期末结转方向枚举
 * <p>
 * 对应 t_period_end_transfer_rule.transfer_direction 字段。
 * <p>
 * 方向语义说明：
 * <ul>
 *   <li>DEBIT_TO_CREDIT(1)：科目为借方余额时，生成"贷出源科目 借入目标科目"的结转凭证。适用于费用、成本类科目。</li>
 *   <li>CREDIT_TO_DEBIT(2)：科目为贷方余额时，生成"借出源科目 贷入目标科目"的结转凭证。适用于正常利息收入等收益类科目。</li>
 *   <li>ADAPTIVE(3)：自适应方向。根据账户当期末余额的实际方向决定凭证方向：
 *       余额为正（与 transfer_direction 期望方向相符）→ 按正向生成结转凭证；
 *       余额为负（发生方向反转，如跨周期减免/提前结清导致收入科目出现借方余额）→ 生成反向凭证，
 *       将已结转至目标科目的部分冲回，确保利润科目不被虚增。
 *       推荐用于"利息收入 → 本年利润"等存在跨周期冲减风险的规则。</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum TransferDirectionEnum {

    DEBIT_TO_CREDIT(1, "借方余额结转到贷方"),
    CREDIT_TO_DEBIT(2, "贷方余额结转到借方"),
    ADAPTIVE(3, "自适应结转（按余额实际方向决定凭证方向，支持跨周期冲减场景）");

    @EnumValue
    @JsonValue
    private final Integer code;

    private final String desc;

    public static TransferDirectionEnum fromCode(Integer code) {
        if (code == null) return null;
        for (TransferDirectionEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }
}
