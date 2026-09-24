// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/AccountingVoucherAttachmentPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 记账凭证附件表持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_accounting_voucher_attachment")
public class AccountingVoucherAttachmentPO extends BaseEntity {

    /**
     * 凭证号。
     */
    @TableField("voucher_no")
    private String voucherNo;

    /**
     * 附件地址。
     */
    @TableField("file_path")
    private String filePath;
}