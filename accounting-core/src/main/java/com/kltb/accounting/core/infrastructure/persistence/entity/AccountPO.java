// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.OwnerTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账户表 PO
 * <p>
 * DDL: docs/sql/1-account.sql (t_account)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_account")
public class AccountPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 会计科目编码
     */
    private String subjectCode;

    /**
     * 所有者ID，如果是内部账户，默认为 INNER
     */
    private String ownerId;

    /**
     * 所有者类型：1-个人,2-企业,99-其他
     */
    private OwnerTypeEnum ownerType;

    /**
     * 账户编号
     */
    private String accountNo;

    /**
     * 账户名称
     */
    private String accountName;

    /**
     * 账户类型(字典CODE)，如：BASIC-基本户,PEND_SET-待结算户,LOAN_PRI-贷款本金账户,LOAN_INT-贷款利息账户,LOAN_GUA-贷款担保费账户
     */
    private String accountType;

    /**
     * 币种(字典CODE)，如：CNY-人民币
     */
    private String currency;

    /**
     * 余额方向：1-借,2-贷
     */
    private BalanceDirectionEnum balanceDirection;

    /**
     * 期初余额
     */
    private BigDecimal openingBalance;

    /**
     * 余额
     */
    private BigDecimal balance;

    /**
     * 账户状态：1-正常,2-冻结,3-注销
     */
    private AccountStatusEnum status;

    /**
     * 风控状态：1-正常,2-止入,3-止出,4-止入止出
     */
    private RiskStatusEnum riskStatus;

    /**
     * 开户请求号
     */
    private String requestNo;

    /**
     * 开户日期
     */
    private LocalDate openDate;

    /**
     * 动支日期
     */
    private LocalDate inactiveDate;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Long version;
}
