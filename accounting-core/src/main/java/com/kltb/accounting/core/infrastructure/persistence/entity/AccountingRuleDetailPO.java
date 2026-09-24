// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingRuleDetailPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AccountScopeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 记账规则明细表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_rule_detail")
public class AccountingRuleDetailPO extends BaseEntity {

    /**
     * 记账规则ID。
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 凭证分录行号。
     */
    @TableField("row_num")
    private Integer rowNum;

    /**
     * 交易款项类型（字典CODE）。
     */
    @TableField("funds_type")
    private String fundsType;

    /**
     * 会计科目编码。
     */
    @TableField("subject_code")
    private String subjectCode;

    /**
     * 账户作用域：1-内部分户；2-外部分户。
     */
    @TableField("account_scope")
    private AccountScopeEnum accountScope;

    /**
     * 借贷方向：1-借，2-贷。
     */
    @TableField("debit_credit")
    private DebitCreditEnum debitCredit;

    /**
     * 币种（字典CODE）。
     */
    @TableField("currency")
    private String currency;

    /**
     * 是否实时更新账户余额：0-否；1-是。
     */
    @TableField("is_unilateral")
    private Boolean unilateral;

    /**
     * SpEL扩展脚本（启动时预加载，规则变更时同步更新）。
     */
    @TableField("extend_script")
    private String extendScript;

    /**
     * 摘要。
     */
    @TableField("summary")
    private String summary;

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