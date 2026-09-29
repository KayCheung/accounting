// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/ManualVoucherApplyEntryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 手工记账申请分录明细持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_manual_voucher_apply_entry")
public class ManualVoucherApplyEntryPO extends BaseEntity {

    /**
     * 关联申请单号。
     */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 分录行号。
     */
    @TableField("row_num")
    private Integer rowNum;

    /**
     * 借贷方向：1-借, 2-贷。
     */
    @TableField("debit_credit")
    private DebitCreditEnum debitCredit;

    /**
     * 会计科目编码。
     */
    @TableField("subject_code")
    private String subjectCode;

    /**
     * 账户编号。
     */
    @TableField("account_no")
    private String accountNo;

    /**
     * 分录金额（绝对值法则，正数）。
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 币种。
     */
    @TableField("currency")
    private String currency;

    /**
     * 分录摘要。
     */
    @TableField("summary")
    private String summary;

    /**
     * 资金单边处理(是否实时更新账户余额)：1-是(实时), 0-否。
     */
    @TableField("is_unilateral")
    private Integer unilateral;
}
