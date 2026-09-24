// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountBalancePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账户日余额表 PO（按年分区）
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_balance)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_account_balance")
public class AccountBalancePO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 会计日（按日汇总）
     */
    private LocalDate accountingDate;

    /**
     * 会计科目编码
     */
    private String subjectCode;

    /**
     * 账户编号
     */
    private String accountNo;

    /**
     * 币种(字典CODE)
     */
    private String currency;

    /**
     * 余额方向：1-借,2-贷
     */
    private BalanceDirectionEnum balanceDirection;

    /**
     * 期初余额（当日0点）
     */
    private BigDecimal beginBalance;

    /**
     * 当日借方发生额
     */
    private BigDecimal debitAmount;

    /**
     * 当日贷方发生额
     */
    private BigDecimal creditAmount;

    /**
     * 期末余额（当日24点）
     */
    private BigDecimal endBalance;
}
