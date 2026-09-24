// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BusinessDetailPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 业务记账流水明细持久化对象
 * <p>
 * 对应表：t_business_detail
 * t_business_record 的从表，记录每笔流水涉及的客户维度明细。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_business_detail")
public class BusinessDetailPO extends BaseEntity {

    /** 系统跟踪号 */
    private String traceNo;

    /** 预留字段，结合 trace_no 实现幂等 */
    private Integer traceSeq;

    /** 客户类型：个人/企业/其他 */
    private CustomerTypeEnum customerType;

    /** 客户ID */
    private String customerId;

    /** 交易款项类型（字典CODE） */
    private String fundsType;

    /**
     * 款项明细编码（字典CODE）
     * DDL 唯一索引 uk_trace_no 包含此字段：(trace_no, trace_seq, customer_id, item_code)
     * 用于区分同一客户在同一笔流水中的不同款项明细
     */
    private String itemCode;

    /**
     * 交易金额
     * 必须使用 BigDecimal，禁止 new BigDecimal(double)
     */
    private BigDecimal amount;
}
