// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BufferPostingRulePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 缓冲入账规则表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_buffer_posting_rule")
public class BufferPostingRulePO extends BaseEntity {

    /**
     * 规则名称。
     */
    @TableField("rule_name")
    private String ruleName;

    /**
     * 缓冲入账模式：1-异步逐条，2-日间批量，3-日终批量汇总。
     */
    @TableField("buffer_mode")
    private BufferModeEnum bufferMode;

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
     * 会计科目编码，与账户编号必须有一个不为空。
     */
    @TableField("subject_code")
    private String subjectCode = "";

    /**
     * 账户编号，与会计科目必须有一个不为空。
     */
    @TableField("account_no")
    private String accountNo = "";

    /**
     * 借贷方向：1-借，2-贷。
     */
    @TableField("debit_credit")
    private DebitCreditEnum debitCredit;

    /**
     * 状态：1-待启用；2-启用，3-停用。
     */
    @TableField("status")
    private RuleStatusEnum status;

    /**
     * 生效时间。
     */
    @TableField("effective_time")
    private LocalDateTime effectiveTime;

    /**
     * 失效时间。
     */
    @TableField("expiration_time")
    private LocalDateTime expirationTime;

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