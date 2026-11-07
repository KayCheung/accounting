// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/messaging/AbstractMqConsumer.java
package com.kltb.accounting.core.infrastructure.messaging;

import com.alibaba.fastjson2.JSON;
import com.kltb.accounting.core.shared.exception.GenericException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * RocketMQ 消费者抽象基类
 *
 * <p>职责：封装消费幂等逻辑，子类只需实现 {@link #doConsume(Object, MessageExt)} 方法，
 * 专注于业务处理，无需关心幂等去重细节。
 *
 * <p>幂等流程：
 * <ol>
 *   <li>以 message_id + consumer_group 查询 t_message_receipt，已存在则直接返回 CONSUME_SUCCESS（幂等跳过）</li>
 *   <li>执行子类 doConsume() 业务逻辑</li>
 *   <li>成功：写入 t_message_receipt（status=1）→ 返回 CONSUME_SUCCESS</li>
 *   <li>失败：写入 t_message_receipt（status=2，含 errorCode / errorMessage）→ 返回 RECONSUME_LATER</li>
 * </ol>
 *
 * <p>子类扩展点：
 * <ul>
 *   <li>{@link #doConsume(Object, MessageExt)}：实现具体业务消费逻辑</li>
 *   <li>{@link #getConsumerGroup()}：返回当前消费者组名称，不得硬编码</li>
 * </ul>
 *
 * <p>依赖说明：由 {@link MessageReceiptRepository}（实现类为 MessageReceiptRepositoryImpl）提供消费回执与幂等持久化支持。
 *
 * <p>异常处理：
 * <ul>
 *   <li>业务异常 → 写入失败回执（status=2），返回 RECONSUME_LATER 触发 RocketMQ 重试</li>
 *   <li>回执写入失败 → 记录 error 日志，不影响消费结果（消息可靠性优先）</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMqConsumer<T> implements MessageListenerConcurrently {

    /** 消息回执存储接口 */
    private final MessageReceiptRepository receiptRepository;

    // ==================== 消费状态常量 ====================

    /** 消费成功状态 */
    private static final Integer RECEIPT_STATUS_SUCCESS = 1;

    /** 消费失败状态 */
    private static final Integer RECEIPT_STATUS_FAILED = 2;

    // ==================== MessageListenerConcurrently 实现 ====================

    /**
     * RocketMQ 框架回调入口（批量列表），委托单条消息处理，final 禁止子类覆盖
     */
    @Override
    public final ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        if (msgs == null || msgs.isEmpty()) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        for (MessageExt message : msgs) {
            ConsumeConcurrentlyStatus status = consume(message, context);
            if (status != ConsumeConcurrentlyStatus.CONSUME_SUCCESS) {
                return status;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    /**
     * 单条消息消费处理入口，内置幂等校验与反序列化，final 禁止子类覆盖
     */
    public final ConsumeConcurrentlyStatus consume(MessageExt message, ConsumeConcurrentlyContext context) {
        String messageId = message.getMsgId();
        // RocketMQ Keys 对应业务 messageId（t_local_message.message_id）
        String businessKey = message.getKeys();
        String topic = message.getTopic();
        String consumerGroup = getConsumerGroup();

        log.info("[RocketMQ] 收到消息 topic={} messageId={} businessKey={} consumerGroup={}",
                topic, messageId, businessKey, consumerGroup);

        // ── 步骤 1：幂等检查（仅当已被指定消费者组成功消费时幂等跳过，失败允许重试） ───────
        if (receiptRepository.isSuccessConsumed(messageId, consumerGroup)) {
            // 已成功消费过，直接提交，防止重复处理（幂等跳过）
            log.info("[RocketMQ] 幂等跳过（已成功消费） topic={} messageId={} consumerGroup={}", topic, messageId, consumerGroup);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }

        // ── 步骤 2：执行业务逻辑 ─────────────────────────────────────────
        LocalDateTime receivedTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();
        String payload = extractPayload(message);

        try {
            T data = deserializePayload(payload, message);
            doConsume(data, message);
            LocalDateTime processedTime = LocalDateTime.now();
            long costMs = System.currentTimeMillis() - startTime;

            // ── 步骤 3：写入成功回执 ──────────────────────────────────────
            saveReceipt(messageId, businessKey, consumerGroup, topic,
                    RECEIPT_STATUS_SUCCESS, "", "", payload, receivedTime, processedTime);

            log.info("[RocketMQ] 消费成功 topic={} messageId={} consumerGroup={} cost={}ms",
                    topic, messageId, consumerGroup, costMs);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;

        } catch (Exception e) {
            LocalDateTime processedTime = LocalDateTime.now();
            long costMs = System.currentTimeMillis() - startTime;
            // 业务消费异常：记录 error 日志，写入失败回执，返回 RECONSUME_LATER 触发重试
            log.error("[RocketMQ] 消费失败 topic={} messageId={} consumerGroup={} cost={}ms error={}",
                    topic, messageId, consumerGroup, costMs, e.getMessage(), e);

            // ── 步骤 4：写入失败回执 ──────────────────────────────────────
            String errorCode = extractErrorCode(e);
            String errorMessage = truncate(e.getMessage(), 255);
            saveReceipt(messageId, businessKey, consumerGroup, topic,
                    RECEIPT_STATUS_FAILED, errorCode, errorMessage, payload, receivedTime, processedTime);

            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    // ==================== 子类扩展点 ====================

    /**
     * 子类实现具体业务消费逻辑
     *
     * <p>注意：此方法内抛出任何异常，基类均会捕获并写入失败回执，触发 RocketMQ 重试。
     * 若业务已处理成功（或已执行补偿/单边回滚）不希望重试，请在方法内自行处理异常，不要向上抛出。
     *
     * @param data    反序列化后的泛型业务对象
     * @param message RocketMQ 消息对象，含 topic / tags / keys / reconsumeTimes 等上下文
     * @throws Exception 业务处理异常，触发重试
     */
    protected abstract void doConsume(T data, MessageExt message) throws Exception;

    /**
     * 返回当前消费者组名称
     *
     * <p>实现规范：必须从子类注解或构造参数获取，严禁硬编码。
     * 推荐通过 Spring @Value 注入配置文件中的 consumer-group 值。
     *
     * @return 消费者组名称，不可为 null 或空字符串
     */
    protected abstract String getConsumerGroup();

    // ==================== 泛型反序列化与私有辅助方法 ====================

    /**
     * 反序列化消息体为泛型类型 T
     */
    @SuppressWarnings("unchecked")
    protected T deserializePayload(String payload, MessageExt message) {
        Class<T> messageType = resolveMessageType();
        if (messageType == null || messageType == Object.class) {
            return (T) payload;
        }
        if (messageType == MessageExt.class) {
            return (T) message;
        }
        if (messageType == String.class) {
            return (T) payload;
        }
        if (messageType == byte[].class) {
            return (T) message.getBody();
        }
        if (payload == null || payload.isBlank()) {
            return null;
        }
        return JSON.parseObject(payload, messageType);
    }

    /**
     * 解析子类指定的实际泛型类型
     */
    @SuppressWarnings("unchecked")
    private Class<T> resolveMessageType() {
        Class<?> currentClass = getClass();
        while (currentClass != null && currentClass != Object.class) {
            Type genericSuperclass = currentClass.getGenericSuperclass();
            if (genericSuperclass instanceof ParameterizedType parameterizedType) {
                Type rawType = parameterizedType.getRawType();
                if (rawType instanceof Class<?> rawClass && AbstractMqConsumer.class.isAssignableFrom(rawClass)) {
                    Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                    if (actualTypeArguments.length > 0) {
                        Type targetType = actualTypeArguments[0];
                        if (targetType instanceof Class<?> clazz) {
                            return (Class<T>) clazz;
                        }
                        if (targetType instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> clazz) {
                            return (Class<T>) clazz;
                        }
                    }
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        return null;
    }

    /**
     * 调用 receiptRepository 写入回执，写入失败仅记录日志，不影响消费结果
     */
    private void saveReceipt(String messageId, String businessKey, String consumerGroup,
                              String topic, Integer status, String errorCode, String errorMessage,
                              String payload, LocalDateTime receivedTime, LocalDateTime processedTime) {
        try {
            receiptRepository.save(messageId,
                    businessKey != null ? businessKey : "",
                    consumerGroup, topic, status,
                    errorCode, errorMessage, payload,
                    receivedTime, processedTime);
        } catch (Exception e) {
            // 回执写入失败：记录 error 日志，不影响消费结果（消息可靠性优先于回执记录）
            log.error("[RocketMQ] 回执写入失败 messageId={} consumerGroup={} status={} error={}",
                    messageId, consumerGroup, status, e.getMessage(), e);
        }
    }

    /**
     * 从异常中提取错误码
     * <p>若为 GenericException 子类，取其 ResultCode.code；否则取异常类名简称
     *
     * @param e 异常对象
     * @return 错误码字符串，最长 16 位
     */
    private String extractErrorCode(Exception e) {
        if (e instanceof GenericException ge) {
            return truncate(ge.getResultCode().getCode(), 16);
        }
        // 非业务异常，取类名简称作为错误码，便于排查
        return truncate(e.getClass().getSimpleName(), 16);
    }

    /**
     * 提取消息体为 UTF-8 字符串
     * <p>注意：消费者不对消息内容进行任何变动与截断
     *
     * @param message RocketMQ 消息
     * @return 消息体字符串，body 为 null 时返回空字符串
     */
    private String extractPayload(MessageExt message) {
        if (message == null || message.getBody() == null) {
            return "";
        }
        return new String(message.getBody(), StandardCharsets.UTF_8);
    }

    /**
     * 截断字符串，防止超出数据库字段长度限制（仅用于错误码和错误信息）
     *
     * @param str       原始字符串
     * @param maxLength 最大长度
     * @return 截断后的字符串，str 为 null 时返回空字符串
     */
    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLength ? str.substring(0, maxLength) : str;
    }
}
