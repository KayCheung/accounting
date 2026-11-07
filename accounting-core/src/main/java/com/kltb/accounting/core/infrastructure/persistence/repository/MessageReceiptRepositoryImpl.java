// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/MessageReceiptRepositoryImpl.java
package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.ReceiptStatusEnum;
import com.kltb.accounting.core.infrastructure.messaging.MessageReceiptRepository;
import com.kltb.accounting.core.infrastructure.persistence.entity.MessageReceiptPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.MessageReceiptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * 消息回执持久化仓储实现
 * <p>
 * 职责：实现 MessageReceiptRepository 接口，基于 MyBatis-Plus 操作 t_message_receipt 表。
 * 用于 MQ 消费者幂等防重与消费回执全生命周期追踪。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MessageReceiptRepositoryImpl implements MessageReceiptRepository {

    private final MessageReceiptMapper messageReceiptMapper;

    @Override
    public boolean isSuccessConsumed(String messageId, String consumerGroup) {
        if (messageId == null || consumerGroup == null) {
            return false;
        }
        LambdaQueryWrapper<MessageReceiptPO> wrapper = new LambdaQueryWrapper<MessageReceiptPO>()
                .eq(MessageReceiptPO::getMessageId, messageId)
                .eq(MessageReceiptPO::getConsumerGroup, consumerGroup)
                .eq(MessageReceiptPO::getStatus, ReceiptStatusEnum.SUCCESS);
        Long count = messageReceiptMapper.selectCount(wrapper);
        return count != null && count > 0;
    }

    @Override
    public void save(String messageId, String businessKey, String consumerGroup, String topic,
                     Integer status, String errorCode, String errorMessage, String payload,
                     LocalDateTime receivedTime, LocalDateTime processedTime) {
        if (messageId == null || consumerGroup == null) {
            log.warn("[MQ回执] 保存回执失败，核心参数缺失: messageId={}, consumerGroup={}", messageId, consumerGroup);
            return;
        }

        ReceiptStatusEnum receiptStatus = ReceiptStatusEnum.fromCode(status);
        if (receiptStatus == null) {
            receiptStatus = ReceiptStatusEnum.SUCCESS;
        }

        // 按 (message_id, consumer_group) 查询是否已存在记录（如上次消费失败重试）
        LambdaQueryWrapper<MessageReceiptPO> wrapper = new LambdaQueryWrapper<MessageReceiptPO>()
                .eq(MessageReceiptPO::getMessageId, messageId)
                .eq(MessageReceiptPO::getConsumerGroup, consumerGroup);
        MessageReceiptPO existing = messageReceiptMapper.selectOne(wrapper);

        if (existing == null) {
            MessageReceiptPO po = new MessageReceiptPO()
                    .setMessageId(messageId)
                    .setBusinessKey(businessKey != null ? businessKey : "")
                    .setConsumerGroup(consumerGroup)
                    .setTopic(topic != null ? topic : "")
                    .setStatus(receiptStatus)
                    .setErrorCode(errorCode != null ? errorCode : "")
                    .setErrorMessage(errorMessage != null ? errorMessage : "")
                    .setPayload(payload != null ? payload : "")
                    .setReceivedTime(receivedTime != null ? receivedTime : LocalDateTime.now())
                    .setProcessedTime(processedTime != null ? processedTime : LocalDateTime.now());
            messageReceiptMapper.insert(po);
            log.debug("[MQ回执] 插入回执成功: messageId={}, consumerGroup={}, status={}", messageId, consumerGroup, receiptStatus);
        } else {
            existing.setBusinessKey(businessKey != null ? businessKey : existing.getBusinessKey())
                    .setTopic(topic != null ? topic : existing.getTopic())
                    .setStatus(receiptStatus)
                    .setErrorCode(errorCode != null ? errorCode : "")
                    .setErrorMessage(errorMessage != null ? errorMessage : "")
                    .setPayload(payload != null ? payload : existing.getPayload())
                    .setReceivedTime(receivedTime != null ? receivedTime : existing.getReceivedTime())
                    .setProcessedTime(processedTime != null ? processedTime : LocalDateTime.now());
            messageReceiptMapper.updateById(existing);
            log.debug("[MQ回执] 更新回执成功: messageId={}, consumerGroup={}, status={}", messageId, consumerGroup, receiptStatus);
        }
    }
}
