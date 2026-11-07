// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountDetailPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户明细表 PO
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_detail)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_account_detail")
public class AccountDetailPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 凭证号
     */
    private String voucherNo;

    /**
     * 分录流水号
     */
    private String entryId;

    /**
     * 事务编号
     */
    private String txnNo;

    /**
     * 系统跟踪号
     */
    private String traceNo;

    /**
     * 预留字段，结合trace_no实现幂等
     */
    private Integer traceSeq;

    /**
     * 会计科目编码
     */
    private String subjectCode;

    /**
     * 账户编号
     */
    private String accountNo;

    /**
     * 业务线编码(字典CODE)
     */
    private String businessCode;

    /**
     * 交易编码(字典CODE)
     */
    private String tradingCode;

    /**
     * 支付渠道(字典CODE)
     */
    private String payChannel;

    /**
     * 交易类别：1-正常,2-调账,3-红,4-蓝
     */
    private TradeTypeEnum tradeType;

    /**
     * 交易时间
     */
    private LocalDateTime tradeTime;

    /**
     * 借贷方向：1-借,2-贷
     */
    private DebitCreditEnum debitCredit;

    /**
     * 增减方向：1-增,2-减
     */
    private ChangeDirectionEnum changeDirection;

    /**
     * 币种(字典CODE)
     */
    private String currency;

    /**
     * 交易前余额
     */
    private BigDecimal preBalance;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 交易后余额
     */
    private BigDecimal postBalance;

    /**
     * 会计日期
     */
    private LocalDate accountingDate;

    /**
     * 摘要
     */
    private String summary;
}
