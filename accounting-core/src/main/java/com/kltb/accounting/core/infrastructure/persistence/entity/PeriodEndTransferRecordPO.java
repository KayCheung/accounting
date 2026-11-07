// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/PeriodEndTransferRecordPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 期末结转记录持久化对象
 * <p>
 * 对应表：t_period_end_transfer_record
 * 记录每次期末结转的执行结果，关联生成的凭证号。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_period_end_transfer_record")
public class PeriodEndTransferRecordPO extends BaseEntity {

    /** 结转流水号（唯一） */
    private String transferNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 结转类型：损益结转/成本结转/自定义结转 */
    private TransferTypeEnum transferType;

    /** 规则编码 */
    private String ruleCode;

    /** 生成的凭证号 */
    private String voucherNo;

    /**
     * 结转总金额
     * 必须使用 BigDecimal，禁止 new BigDecimal(double)
     */
    private BigDecimal totalAmount;

    /** 状态：处理中/成功/失败 */
    private TransferRecordStatusEnum status;

    /** 失败原因 */
    private String failReason;

    /** 执行时间 */
    private LocalDateTime executeTime;

    /** 完成时间 */
    private LocalDateTime finishTime;
}
