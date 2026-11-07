// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountTemplatePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 外部客户账户开户模板持久化对象
 * <p>
 * 对应表：t_account_template
 * 定义不同业务线、客户类型的账户开户规则，支持自动开户。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_account_template")
public class AccountTemplatePO extends BaseEntity {

    /** 模板名称 */
    private String templateName;

    /** 业务线编码（字典CODE） */
    private String businessCode;

    /** 客户类型：个人/企业/其他 */
    private CustomerTypeEnum customerType;

    /**
     * 是否支持自动开户
     * DDL: auto_open TINYINT → Boolean（0-否，1-是）
     */
    private Boolean autoOpen;

    /** 状态：待启用/启用/停用 */
    private TemplateStatusEnum status;

    /** 会计科目编码 */
    private String subjectCode;

    /** 账户类型（字典CODE） */
    private String accountType;

    /** 币种（字典CODE，默认CNY） */
    private String currency;

    /** 余额方向：借/贷（复用 Java-A 定义的 BalanceDirectionEnum） */
    private BalanceDirectionEnum balanceDirection;

    /** 账户编号生成规则 */
    private String acctNoRule;

    /** 账户名称生成规则 */
    private String acctNameRule;

    /** 创建人ID */
    private String createId;

    /** 创建人姓名 */
    private String createName;

    /** 更新人ID */
    private String updateId;

    /** 更新人姓名 */
    private String updateName;
}
