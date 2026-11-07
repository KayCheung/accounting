// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountBalanceSnapshotPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SnapshotTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户余额快照表 PO（按年分区）
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_balance_snapshot)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_account_balance_snapshot")
public class AccountBalanceSnapshotPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 快照日期
     */
    private LocalDate snapshotDate;

    /**
     * 快照类型：1-DAY,2-MONTH,3-YEAR,4-CUSTOM
     */
    private SnapshotTypeEnum snapshotType;

    /**
     * 快照生成时间
     */
    private LocalDateTime snapshotTime;

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
     * 快照时账户余额
     */
    private BigDecimal balance;

    /**
     * 扩展统计信息
     */
    private String extJson;
}
