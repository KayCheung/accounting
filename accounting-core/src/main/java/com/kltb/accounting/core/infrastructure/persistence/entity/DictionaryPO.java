// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/DictionaryPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典持久化对象
 * <p>
 * 对应表：t_dictionary
 * 系统内置字典（system=true）禁止删除，用户自定义字典可维护。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("t_dictionary")
public class DictionaryPO extends BaseEntity {

    /** 字典类型编码（如：auxiliary_type, trading_code, pay_channel） */
    private String dictType;

    /** 字典项编码（如：ASSET, LIABILITY, WECHAT） */
    private String dictCode;

    /** 字典项名称（中文默认） */
    private String dictName;

    /** 英文名称（用于国际化） */
    private String dictNameEn;

    /** 排序序号，越小越靠前 */
    private Integer sortOrder;

    /** 分组键 */
    private String groupKey;

    /** 状态：启用/停用 */
    private AvailableStatusEnum status;

    /**
     * 是否系统内置
     * DDL: is_system TINYINT → Boolean（去掉 is 前缀）
     * 系统内置字典禁止删除
     */
    @TableField("is_system")
    private Boolean system;

    /** 扩展属性 JSON */
    private String extJson;

    /** 创建人ID */
    private String createId;

    /** 创建人姓名 */
    private String createName;

    /** 更新人ID */
    private String updateId;

    /** 更新人姓名 */
    private String updateName;
}
