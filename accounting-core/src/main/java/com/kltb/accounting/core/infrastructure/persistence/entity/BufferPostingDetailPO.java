// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BufferPostingDetailPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缓冲记账明细表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_buffer_posting_detail")
public class BufferPostingDetailPO extends BaseEntity {

    /**
     * 缓冲入账规则ID。
     */
    @TableField("rule_id")
    private Long ruleId;

    /**
     * 缓冲入账模式：1-异步逐条，2-日间批量，3-日终批量汇总。
     */
    @TableField("buffer_mode")
    private BufferModeEnum bufferMode;

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
     * 事务编号。
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
     * 账户编号。
     */
    @TableField("account_no")
    private String accountNo;

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
     * 金额。
     */
    @TableField("amount")
    private BigDecimal amount;

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
     * 缓冲入账状态：1-待入账，2-处理中，3-成功，4-失败。
     */
    @TableField("status")
    private BufferStatusEnum status;

    /**
     * 执行次数。
     */
    @TableField("retry_count")
    private Integer retryCount;

    /**
     * 失败原因。
     */
    @TableField("fail_reason")
    private String failReason;

    /**
     * 执行开始时间。
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 完成时间。
     */
    @TableField("complete_time")
    private LocalDateTime completeTime;

    /**
     * 分片值（同一账户必须在同一分片）。
     */
    @TableField("sharding")
    private Long sharding;

    /**
     * 版本号（乐观锁）。
     */
    @Version
    @TableField("version")
    private Long version;
}