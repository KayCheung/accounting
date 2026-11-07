// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/LocalMessagePO.java
package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.MessageStatusEnum;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 本地消息持久化对象（事务性发件箱 Outbox Pattern）
 * <p>
 * 对应表：t_local_message
 * ⚠️ 此表无 tenant_id 字段，不继承 BaseEntity，所有字段单独定义。
 * 消息与业务数据必须在同一事务内写入，由定时 Job 扫描补偿发送。
 * 重试间隔指数退避（10s/30s/60s），超限标记 FAILED 并触发告警。
 */
@Data
@Accessors(chain = true)
@TableName("t_local_message")
public class LocalMessagePO implements Serializable {

    /** 主键（自增） */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息ID（全局唯一） */
    private String messageId;

    /** 消息主题/队列名 */
    private String topic;

    /** 消息标签（可选） */
    private String tag;

    /** 业务唯一键，用于幂等与对账 */
    private String businessKey;

    /** 消息体（JSON格式） */
    private String payload;

    /**
     * 消息状态：待发送/发送中/发送失败/已确认
     * 状态流转：PENDING(1) → SENT(2) | FAILED(3)，CONFIRMED(4) 预留未来使用。
     */
    private MessageStatusEnum status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数（默认3次） */
    private Integer maxRetry;

    /** 下次重试时间 */
    private LocalDateTime nextRetryTime;

    /** 实际发送时间 */
    private LocalDateTime sendTime;

    /** 创建时间 */
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