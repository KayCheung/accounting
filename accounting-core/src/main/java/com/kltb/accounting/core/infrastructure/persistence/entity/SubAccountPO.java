// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/SubAccountPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 子账户表 PO
 * <p>
 * DDL: docs/sql/1-account.sql (t_sub_account)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_sub_account")
public class SubAccountPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 账户编号（等于t_account表中的account_no）
     */
    private String accountNo;

    /**
     * 余额类型：1-可用余额,2-冻结余额
     */
    private BalanceTypeEnum balanceType;

    /**
     * 余额方向：1-借,2-贷
     */
    private BalanceDirectionEnum balanceDirection;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Long version;
}
