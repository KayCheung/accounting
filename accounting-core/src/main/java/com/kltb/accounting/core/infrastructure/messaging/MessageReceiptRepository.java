// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/messaging/MessageReceiptRepository.java
package com.kltb.accounting.core.infrastructure.messaging;

import java.time.LocalDateTime;

/**
 * 消息回执存储接口
 *
 * <p>职责：解耦 AbstractMqConsumer 与持久层，Step 3 完成后由
 * MessageReceiptRepositoryImpl 实现，注入 MessageReceiptMapper。
 *
 * <p>此接口在 Step 4.1 中定义，Step 3 完成后实现，两步可并行推进。
 *
 * <p>⚠️ 当前状态：仅接口定义，无实现类。消费幂等功能在实现类注入前不可用。
 * 需在 Step 5（Domain Alignment）中补上 MessageReceiptRepositoryImpl 实现。
 */
public interface MessageReceiptRepository {

    /**
     * 检查消息是否已被指定消费者组消费过
     *
     * @param messageId     ONS 消息 ID
     * @param consumerGroup 消费者组
     * @return true 表示已消费，应幂等跳过
     */
    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);

    /**
     * 写入消息回执记录
     *
     * @param messageId     ONS 消息 ID
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
