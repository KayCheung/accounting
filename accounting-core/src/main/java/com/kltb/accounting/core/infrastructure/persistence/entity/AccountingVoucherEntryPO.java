// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingVoucherEntryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 分录流水表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_voucher_entry")
public class AccountingVoucherEntryPO extends BaseEntity {

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
     * 分录行号。
     */
    @TableField("row_num")
    private Integer rowNum;

    /**
     * 会计科目编码。
     */
    @TableField("subject_code")
    private String subjectCode;

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
     * 交易金额。
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 币种（字典CODE）。
     */
    @TableField("currency")
    private String currency;

    /**
     * 记账汇率。
     */
    @TableField("exchange_rate")
    private BigDecimal exchangeRate;

    /**
     * 单价。
     */
    @TableField("unit_price")
    private BigDecimal unitPrice;

    /**
     * 数量。
     */
    @TableField("quantity")
    private Integer quantity;

    /**
     * 计价单位。
     */
    @TableField("pricing_unit")
    private String pricingUnit;

    /**
     * 摘要。
     */
    @TableField("summary")
    private String summary;

    /**
     * 分录明细状态：1-未过账，2-已过账，3-过账失败。
     */
    @TableField("status")
    private VoucherEntryStatusEnum status;

    /**
     * 会计日。
     */
    @TableField("accounting_date")
    private LocalDate accountingDate;

    /**
     * 资金单边处理(是否实时更新账户余额)：0-否(MQ异步过账/缓冲),1-是(实时过账)。
     */
    @TableField("is_unilateral")
    private Integer unilateral;

    /**
     * 是否缓冲入账：0-否,1-是(匹配到缓冲规则)。
     */
    @TableField("is_buffered")
    private Integer buffered;

    /**
     * 增减方向：1-增,2-减（过账时直接使用，由Step10规则推导写入）。
     */
    @TableField("change_direction")
    private Integer changeDirection;

    /**
     * 余额更新时间。
     */
    @TableField("balance_update_time")
    private LocalDateTime balanceUpdateTime;

    /**
     * 版本号（乐观锁）。
     */
    @Version
    @TableField("version")
    private Integer version;
}