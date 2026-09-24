// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/PeriodEndTransferRulePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.TransferDirectionEnum;
import com.kltb.accounting.core.domain.enums.TransferTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 期末结转规则持久化对象
 * <p>
 * 对应表：t_period_end_transfer_rule
 * EOD 阶段 4.5 按 execute_order 升序执行，支持损益/成本/自定义结转。
 * source_subject_code 支持通配符（如 6* 表示所有6开头的科目）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_period_end_transfer_rule")
public class PeriodEndTransferRulePO extends BaseEntity {

    /** 规则编码（唯一） */
    private String ruleCode;

    /** 规则名称 */
    private String ruleName;

    /** 结转类型：损益结转/成本结转/自定义结转 */
    private TransferTypeEnum transferType;

    /** 源科目编码（支持通配符，如：6* 表示所有6开头的科目） */
    private String sourceSubjectCode;

    /** 目标科目编码 */
    private String targetSubjectCode;

    /** 结转方向：借方余额结转到贷方/贷方余额结转到借方 */
    private TransferDirectionEnum transferDirection;

    /** 摘要模板（支持变量，如：{year}年{month}月损益结转） */
    private String summaryTemplate;

    /** 执行顺序（数字越小越先执行） */
    private Integer executeOrder;

    /** 状态：启用/停用 */
    private AvailableStatusEnum status;

    /** 创建人ID */
    private String createId;

    /** 创建人姓名 */
    private String createName;

    /** 更新人ID */
    private String updateId;

    /** 更新人姓名 */
    private String updateName;
}
