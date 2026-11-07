// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/messaging/MessageReceiptRepository.java
package com.kltb.accounting.core.infrastructure.messaging;

import java.time.LocalDateTime;

/**
 * 消息回执存储接口
 *
 * <p>职责：解耦 AbstractMqConsumer 与持久层，由
 * MessageReceiptRepositoryImpl 实现，注入 MessageReceiptMapper。
 *
 * <p>用于 MQ 消费幂等校验（t_message_receipt）与消费回执全生命周期追踪。
 */
public interface MessageReceiptRepository {

    /**
     * 检查消息是否已被指定消费者组【成功消费】过
     * <p>
     * 幂等防重准则：仅当且仅当 status=1 (SUCCESS) 时返回 true（触发幂等跳过）；
     * 若无记录或此前消费失败（status=2），返回 false，允许重试。
     *
     * @param messageId     RocketMQ 消息 ID
     * @param consumerGroup 消费者组
     * @return true 表示已成功消费，应幂等跳过；false 表示未消费或消费失败需重试
     */
    boolean isSuccessConsumed(String messageId, String consumerGroup);

    /**
     * 写入或更新消息回执记录（SaveOrUpdate 语义）
     * <p>
     * 若 (message_id, consumer_group) 不存在则新增；
     * 若已存在（如上次重试失败后本次重试成功或再次失败），则更新其状态、错误信息与时间戳。
     *
     * @param messageId     RocketMQ 消息 ID
     * @param businessKey   业务唯一键
     * @param consumerGroup 消费者组
     * @param topic         消息主题
     * @param status        消费状态：1-成功，2-失败
     * @param errorCode     错误码（成功时传空字符串）
     * @param errorMessage  错误详情（成功时传空字符串）
     * @param payload       消息体快照（用于排查）
     * @param receivedTime  消费者接收时间
     * @param processedTime 消费者处理完成时间
     */
    void save(String messageId, String businessKey, String consumerGroup, String topic,
              Integer status, String errorCode, String errorMessage, String payload,
              LocalDateTime receivedTime, LocalDateTime processedTime);
}
