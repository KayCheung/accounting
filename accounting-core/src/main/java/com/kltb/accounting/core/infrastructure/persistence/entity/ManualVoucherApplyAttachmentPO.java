// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/ManualVoucherApplyAttachmentPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 手工凭证申请附件持久化对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_manual_voucher_apply_attachment")
public class ManualVoucherApplyAttachmentPO extends BaseEntity {

    /**
     * 关联申请单号。
     */
    @TableField("apply_no")
    private String applyNo;

    /**
     * 附件文件名称。
     */
    @TableField("file_name")
    private String fileName;

    /**
     * 附件存储地址/URL。
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 附件大小(字节)。
     */
    @TableField("file_size")
    private Long fileSize;
}
