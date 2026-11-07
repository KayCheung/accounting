// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/ManualVoucherApplyAuxiliaryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 手工凭证申请辅助核算分摊持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_manual_voucher_apply_auxiliary")
public class ManualVoucherApplyAuxiliaryPO extends BaseEntity {

    /**
     * 关联申请单号。
     */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 关联分录行号。
     */
    @TableField("entry_row_num")
    private Integer entryRowNum;

    /**
     * 会计科目编码。
     */
    @TableField("subject_code")
    private String subjectCode;

    /**
     * 辅助核算类别代码(如 DEPT/PROJECT/CUSTOMER/SUPPLIER)。
     */
    @TableField("aux_type")
    private String auxType;

    /**
     * 辅助核算类别名称(如 部门、项目、客户、供应商)。
     */
    @TableField("aux_type_name")
    private String auxTypeName;

    /**
     * 辅助核算项目编码。
     */
    @TableField("aux_code")
    private String auxCode;

    /**
     * 辅助核算项目名称。
     */
    @TableField("aux_name")
    private String auxName;

    /**
     * 增减方向：1-增, 2-减。
     */
    @TableField("change_direction")
    private ChangeDirectionEnum changeDirection;

    /**
     * 核算金额。
     */
    @TableField("amount")
    private BigDecimal amount;
}
