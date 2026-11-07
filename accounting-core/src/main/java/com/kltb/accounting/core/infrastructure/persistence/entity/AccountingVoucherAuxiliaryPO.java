// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingVoucherAuxiliaryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 记账凭证辅助核算项目持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_voucher_auxiliary")
public class AccountingVoucherAuxiliaryPO extends BaseEntity {

    /**
     * 凭证号。
     */
    @TableField("voucher_no")
    private String voucherNo;

    /**
     * 分录流水号。
     */
    @TableField("entry_id")
    private String entryId;

    /**
     * 会计科目编码。
     */
    @TableField("subject_code")
    private String subjectCode;

    /**
     * 辅助核算类型（字典CODE），如 DEPT/CUSTOMER/PROJECT。
     */
    @TableField("aux_type")
    private String auxType;

    /**
     * 辅助核算项目编码（字典CODE）。
     */
    @TableField("aux_code")
    private String auxCode;

    /**
     * 辅助核算项目名称。
     */
    @TableField("aux_name")
    private String auxName;

    /**
     * 增减方向：1-增，2-减。
     */
    @TableField("change_direction")
    private ChangeDirectionEnum changeDirection;

    /**
     * 金额。
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 会计日。
     */
    @TableField("accounting_date")
    private LocalDate accountingDate;
}