// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountSubjectPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.domain.enums.SubjectNatureEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 会计科目持久化对象
 * <p>
 * 对应表：t_account_subject
 * 六大类科目（资产/负债/权益/共同/成本/损益），仅末级科目（leaf=true）允许记账。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_account_subject")
public class AccountSubjectPO extends BaseEntity {

    /** 科目编码（遵循通用科目编码规则，前缀为父科目编码，如101001，101为父科目） */
    private String subjectCode;

    /** 科目名称 */
    private String subjectName;

    /** 科目级别 */
    private Integer subjectLevel;

    /** 父科目ID，顶级科目为0 */
    private Long parentSubjectId;

    /** 账类：资产/负债/权益/共同/成本/损益/表外 */
    private SubjectCategoryEnum subjectCategory;

    /** 科目性质：非特殊/销账/贷款/现金 */
    private SubjectNatureEnum nature;

    /** 借贷方向：借/贷（复用 Java-A 定义的 DebitCreditEnum） */
    private DebitCreditEnum debitCredit;

    /**
     * 是否末级科目
     * DDL: is_leaf → Java 字段去掉 is 前缀
     */
    @TableField("is_leaf")
    private Boolean leaf;

    /**
     * 是否允许记账
     * DDL: allow_post → allowPost
     */
    @TableField("allow_post")
    private Boolean allowPost;

    /**
     * 是否允许建明细账户
     * DDL: allow_open_account → allowOpenAccount
     */
    @TableField("allow_open_account")
    private Boolean allowOpenAccount;

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
