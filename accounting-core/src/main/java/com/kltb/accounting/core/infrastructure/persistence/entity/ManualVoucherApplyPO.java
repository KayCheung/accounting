// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/ManualVoucherApplyPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.ManualVoucherApplyStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 手工记账申请单持久化对象（物理完全独立，解耦法定凭证表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_manual_voucher_apply")
public class ManualVoucherApplyPO extends BaseEntity {

    /**
     * 申请单号（MVA + 日期 + 序号）。
     */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 凭证类型（字典CODE/名称），如：记账凭证、调整凭证。
     */
    @TableField("voucher_type")
    private String voucherType;

    /**
     * 交易类别：1-正常，2-调账。
     */
    @TableField("trade_type")
    private Integer tradeType;

    /**
     * 会计日期。
     */
    @TableField("accounting_date")
    private LocalDate accountingDate;

    /**
     * 申请摘要。
     */
    @TableField("summary")
    private String summary;

    /**
     * 附件数。
     */
    @TableField("attachment_count")
    private Integer attachmentCount;

    /**
     * 借方合计金额。
     */
    @TableField("total_debit_amount")
    private BigDecimal totalDebitAmount;

    /**
     * 贷方合计金额。
     */
    @TableField("total_credit_amount")
    private BigDecimal totalCreditAmount;

    /**
     * 审批流状态：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废。
     */
    @TableField("apply_status")
    private ManualVoucherApplyStatusEnum applyStatus;

    /**
     * 制单人姓名。
     */
    @TableField("maker_name")
    private String makerName;

    /**
     * 初审人姓名。
     */
    @TableField("auditor_name")
    private String auditorName;

    /**
     * 初审时间。
     */
    @TableField("audit_time")
    private LocalDateTime auditTime;

    /**
     * 初审意见/驳回原因。
     */
    @TableField("audit_opinion")
    private String auditOpinion;

    /**
     * 复核人姓名（会计主管）。
     */
    @TableField("reviewer_name")
    private String reviewerName;

    /**
     * 复核时间。
     */
    @TableField("review_time")
    private LocalDateTime reviewTime;

    /**
     * 复核意见/驳回原因。
     */
    @TableField("review_opinion")
    private String reviewOpinion;

    /**
     * 记账人姓名。
     */
    @TableField("bookkeeper_name")
    private String bookkeeperName;

    /**
     * 记账时间。
     */
    @TableField("bookkeeping_time")
    private LocalDateTime bookkeepingTime;

    /**
     * 正式入账凭证号（经终审复核通过并确认记账后回填）。
     */
    @TableField("voucher_no")
    private String voucherNo;
}
