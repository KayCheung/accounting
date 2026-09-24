// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BusinessRecordPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务记账流水持久化对象
 * <p>
 * 对应表：t_business_record
 * 会计日期在此处确定（accounting_date），全链路不可变更。
 * version 字段用于缓冲路径乐观锁 CAS 更新。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_business_record")
public class BusinessRecordPO extends BaseEntity {

    /** 业务线编码（字典CODE） */
    private String businessCode;

    /** 系统跟踪号（与 trace_seq 联合唯一，实现入口幂等） */
    private String traceNo;

    /** 预留字段，结合 trace_no 实现幂等 */
    private Integer traceSeq;

    /** 交易编码（字典CODE） */
    private String tradingCode;

    /** 支付渠道（字典CODE） */
    private String payChannel;

    /** 交易类别：正常/调账/红/蓝（复用 Java-B 定义的 TradeTypeEnum） */
    private TradeTypeEnum tradeType;

    /**
     * 交易金额
     * 必须使用 BigDecimal，禁止 new BigDecimal(double)
     */
    private BigDecimal amount;

    /** 交易时间 */
    private LocalDateTime tradeTime;

    /**
     * 会计日期（在此确定，全链路不可变更）
     * 日切后新请求写 T+1，T 日存量继续在原日期处理
     */
    private LocalDate accountingDate;

    /** 摘要 */
    private String summary;

    /** 状态：处理中/成功/失败 */
    private BusinessRecordStatusEnum status;

    /**
     * 版本号（乐观锁）
     * 缓冲路径 CAS 更新，失败重试最多 3 次，超阈值升级悲观锁
     */
    @Version
    private Integer version;
}
