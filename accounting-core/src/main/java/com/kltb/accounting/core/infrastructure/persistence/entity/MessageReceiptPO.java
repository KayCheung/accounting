// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/MessageReceiptPO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.ReceiptStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息回执持久化对象
 * <p>
 * 对应表：t_message_receipt
 * ⚠️ 此表无 tenant_id 字段，不继承 BaseEntity，所有字段单独定义。
 * 用于 MQ 消费幂等防护：消费前查此表，已有记录则跳过，防止重复消费。
 */
@Data
@Accessors(chain = true)
@TableName("t_message_receipt")
public class MessageReceiptPO implements Serializable {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 t_local_message.message_id */
    private String messageId;

    /** 业务唯一键 */
    private String businessKey;

    /** 消费者组 */
    private String consumerGroup;

    /** 消息主题/队列名 */
    private String topic;

    /** 消费结果：成功/失败 */
    private ReceiptStatusEnum status;

    /** 错误码 */
    private String errorCode;

    /** 错误详情 */
    private String errorMessage;

    /** 消息快照（JSON格式，用于排查） */
    private String payload;

    /** 消费者接收时间 */
    private LocalDateTime receivedTime;

    /** 消费者处理完成时间 */
    private LocalDateTime processedTime;

    /** 本记录创建时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标识：0-未删除，非0为删除时间戳
     * ⚠️ delval="1" 仅为 MyBatis-Plus 框架占位（框架要求必须填写），
     * 实际删除时禁止依赖框架自动填充，必须由 Repository 层手动赋值：
     *   po.setIsDelete(System.currentTimeMillis());
     *   mapper.updateById(po);
     */
    @TableLogic(value = "0", delval = "1")
    private Long isDelete;
}
