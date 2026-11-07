// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingRuleAuxiliaryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 记账规则辅助核算项表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_rule_auxiliary")
public class AccountingRuleAuxiliaryPO extends BaseEntity {

    /**
     * 记账规则ID（冗余字段）。
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 记账规则明细ID。
     */
    @TableField("rule_detail_id")
    private Long ruleDetailId;

    /**
     * 辅助核算类型（字典CODE）。
     */
    @TableField("aux_type")
    private String auxType;

    /**
     * 辅助核算项目编码（字典CODE）。
     */
    @TableField("aux_code")
    private String auxCode;

    /**
     * 分摊方式：1-不分摊，2-固定金额，3-按比例。
     */
    @TableField("allocation_method")
    private AllocationMethodEnum allocationMethod;

    /**
     * 分摊值。
     */
    @TableField("allocation_value")
    private BigDecimal allocationValue;

    /**
     * SpEL扩展脚本。
     */
    @TableField("extend_script")
    private String extendScript;

    /**
     * 创建人ID。
     */
    @TableField("create_id")
    private String createId;

    /**
     * 创建人姓名。
     */
    @TableField("create_name")
    private String createName;

    /**
     * 更新人ID。
     */
    @TableField("update_id")
    private String updateId;

    /**
     * 更新人姓名。
     */
    @TableField("update_name")
    private String updateName;
}