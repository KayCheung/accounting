// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BaseEntity.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 持久化对象公共基类
 * <p>
 * 包含所有表共有字段：主键、租户、逻辑删除、创建/更新时间。
 * 所有 PO 类必须继承此类，不得重复定义以下字段。
 * <p>
 * 注意：t_local_message / t_message_receipt 无 tenant_id，不继承此类。
 */
@Data
@Accessors(chain = true)
public abstract class BaseEntity implements Serializable {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户ID */
    private Integer tenantId;

    /**
     * 逻辑删除标识：0-未删除，删除时间戳为已删除
     * ⚠️ delval 固定值仅为 MyBatis-Plus 框架要求（非 null），
     * 严禁使用 mapper.deleteById() 执行逻辑删除！
     * 正确做法：手动设置时间戳后调用 updateById()
     *   po.setIsDelete(System.currentTimeMillis());
     *   mapper.updateById(po);
     */
    @TableLogic(value = "0", delval = "1")
    private Long isDelete;

    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
