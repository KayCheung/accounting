// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountSubjectAuxiliaryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 会计科目辅助核算项持久化对象
 * <p>
 * 对应表：t_account_subject_auxiliary
 * t_account_subject 的从表，定义科目需要关联的辅助核算维度（客户/项目/合同等）。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_account_subject_auxiliary")
public class AccountSubjectAuxiliaryPO extends BaseEntity {

    /** 会计科目编码 */
    private String subjectCode;

    /** 辅助核算项类别（字典CODE） */
    private String auxiliaryType;

    /**
     * 是否必填
     * DDL: required TINYINT → Boolean（0-可选，1-必填）
     */
    private Boolean required;

    /** 默认辅助核算项目（字典CODE） */
    private String defaultAuxCode;

    /** 创建人ID */
    private String createId;

    /** 创建人姓名 */
    private String createName;

    /** 更新人ID */
    private String updateId;

    /** 更新人姓名 */
    private String updateName;
}
