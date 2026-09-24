// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingRulePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.OpenAccountFlagEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 记账规则表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_rule")
public class AccountingRulePO extends BaseEntity {

    /**
     * 规则名称。
     */
    @TableField("rule_name")
    private String ruleName;

    /**
     * 凭证类型（字典CODE）。
     */
    @TableField("voucher_type")
    private String voucherType;

    /**
     * 业务线编码（字典CODE）。
     */
    @TableField("business_code")
    private String businessCode;

    /**
     * 交易编码（字典CODE）。
     */
    @TableField("trading_code")
    private String tradingCode;

    /**
     * 支付渠道（字典CODE）。
     */
    @TableField("pay_channel")
    private String payChannel;

    /**
     * 是否允许自动开户：0-否；1-是。
     */
    @TableField("is_open_account")
    private OpenAccountFlagEnum openAccount;

    /**
     * 冻结时长，单位：秒。
     */
    @TableField("freeze_duration")
    private Integer freezeDuration;

    /**
     * 前置入账规则ID。
     */
    @TableField("pre_rule_id")
    private Long preRuleId;

    /**
     * 状态：1-待启用；2-启用，3-停用。
     */
    @TableField("status")
    private RuleStatusEnum status;

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