// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingVoucherPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.PostingTypeEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 记账凭证表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_voucher")
public class AccountingVoucherPO extends BaseEntity {

    /**
     * 凭证号。
     */
    @TableField("voucher_no")
    private String voucherNo;

    /**
     * 事务编号（凭证先行，事务编号在凭证生成后补充）。
     */
    @TableField("txn_no")
    private String txnNo;

    /**
     * 系统跟踪号。
     */
    @TableField("trace_no")
    private String traceNo;

    /**
     * 预留字段，结合trace_no实现幂等。
     */
    @TableField("trace_seq")
    private Integer traceSeq;

    /**
     * 凭证类型（字典CODE），如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证。
     */
    @TableField("voucher_type")
    private String voucherType;

    /**
     * 入账类型：1-手工凭证，2-机制凭证。
     */
    @TableField("posting_type")
    private PostingTypeEnum postingType;

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
     * 交易类别：1-正常，2-调账，3-红，4-蓝。
     */
    @TableField("trade_type")
    private TradeTypeEnum tradeType;

    /**
     * 交易时间。
     */
    @TableField("trade_time")
    private LocalDateTime tradeTime;

    /**
     * 金额。
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 凭证状态：1-未过账，2-过账中，3-已过账，4-过账失败，5-已冲销。
     */
    @TableField("status")
    private VoucherStatusEnum status;

    /**
     * 过账时间。
     */
    @TableField("post_time")
    private LocalDateTime postTime;

    /**
     * 会计日期。
     */
    @TableField("accounting_date")
    private LocalDate accountingDate;

    /**
     * 摘要。
     */
    @TableField("summary")
    private String summary;

    /**
     * 附件数。
     */
    @TableField("attachment_count")
    private Integer attachmentCount;

    /**
     * 原凭证号（红冲凭证关联原凭证）。
     */
    @TableField("orig_voucher_no")
    private String origVoucherNo;

    /**
     * 版本号（乐观锁）。
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 记账人姓名。
     */
    @TableField("bookkeeper_name")
    private String bookkeeperName;

    /**
     * 复核人姓名。
     */
    @TableField("reviewer_name")
    private String reviewerName;

    /**
     * 过账失败原因（Step 12 P0-8）。
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 手动重试次数（Step 12 P0-8）。
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 跳过标记：0-未跳过,1-人工跳过（Step 12 P0-8）。
     */
    @TableField("skip_flag")
    private Integer skipFlag;
}