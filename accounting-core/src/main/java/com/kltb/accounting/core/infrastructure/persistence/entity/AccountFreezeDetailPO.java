// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountFreezeDetailPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 账户资金冻结明细表 PO
 * <p>
 * DDL: docs/sql/1-account.sql (t_account_freeze_detail)
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_account_freeze_detail")
public class AccountFreezeDetailPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 账户编号
     */
    private String accountNo;

    /**
     * 凭证号
     */
    private String voucherNo;

    /**
     * 事务编号
     */
    private String txnNo;

    /**
     * 业务线编码(字典CODE)
     */
    private String businessCode;

    /**
     * 交易编码(字典CODE)
     */
    private String tradingCode;

    /**
     * 系统跟踪号
     */
    private String traceNo;

    /**
     * 预留字段，结合trace_no实现幂等
     */
    private Integer traceSeq;

    /**
     * 交易时间
     */
    private LocalDateTime tradeTime;

    /**
     * 冻结金额
     */
    private BigDecimal freezeAmount;

    /**
     * 状态：1-冻结,2-已解冻
     */
    private FreezeStatusEnum status;

    /**
     * 冻结过期时间，2099 表示永不过期
     */
    private LocalDateTime expireTime;

    /**
     * 摘要
     */
    private String summary;

    /**
     * 版本号（乐观锁）
     */
    @Version
    private Integer version;
}
