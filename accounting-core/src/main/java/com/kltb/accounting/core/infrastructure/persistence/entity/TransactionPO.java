// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/TransactionPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.TransactionStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 事务持久化对象
 * <p>
 * 对应表：t_transaction
 * 事务状态流转：PROCESSING → SUCCESS | FAILED
 * 回滚时必须同步更新 status=FAILED 并记录 fail_reason（TransactionTemplate 显式控制）。
 * version 字段用于乐观锁 CAS 更新。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_transaction")
public class TransactionPO extends BaseEntity {

    /** 事务编号（唯一） */
    private String txnNo;

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 本次事务涉及的账户总数 */
    private Integer relateAccountCount;

    /**
     * 事务总金额
     * 必须使用 BigDecimal，禁止 new BigDecimal(double)
     */
    private BigDecimal amount;

    /** 交易币种（字典CODE，默认CNY） */
    private String currency;

    /** 事务状态：处理中/成功/失败 */
    private TransactionStatusEnum status;

    /** 失败原因（回滚时必须填写） */
    private String failReason;

    /** 事务最终完成时间 */
    private LocalDateTime finishTime;

    /**
     * 版本号（乐观锁）
     * 缓冲路径 CAS 更新，失败重试最多 3 次，超阈值升级悲观锁
     */
    @Version
    private Integer version;
}
