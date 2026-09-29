// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/ManualVoucherAuditLogPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 手工记账流转可追溯审计日志持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_manual_voucher_audit_log")
public class ManualVoucherAuditLogPO extends BaseEntity {

    /**
     * 关联申请单号。
     */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 审批流动作：SUBMIT, AUDIT_PASS, AUDIT_REJECT, REVIEW_PASS, REVIEW_REJECT, BOOKKEEPING, DRAFT, CANCEL。
     */
    @TableField("action")
    private String action;

    /**
     * 动作中文描述。
     */
    @TableField("action_desc")
    private String actionDesc;

    /**
     * 操作人姓名。
     */
    @TableField("operator_name")
    private String operatorName;

    /**
     * 操作角色：MAKER-制单人, AUDITOR-初审人, REVIEWER-复核人, BOOKKEEPER-记账人。
     */
    @TableField("operator_role")
    private String operatorRole;

    /**
     * 流转前状态码。
     */
    @TableField("pre_status")
    private Integer preStatus;

    /**
     * 流转后状态码。
     */
    @TableField("post_status")
    private Integer postStatus;

    /**
     * 审批处理意见或驳回原因。
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 操作时间。
     */
    @TableField("operate_time")
    private LocalDateTime operateTime;
}
