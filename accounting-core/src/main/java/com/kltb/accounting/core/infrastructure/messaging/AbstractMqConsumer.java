// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/messaging/AbstractMqConsumer.java
package com.kltb.accounting.core.infrastructure.messaging;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import com.kltb.accounting.core.shared.exception.GenericException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * MQ 消费者抽象基类
 *
 * <p>职责：封装消费幂等逻辑，子类只需实现 {@link #doConsume(Message)} 方法，
 * 专注于业务处理，无需关心幂等去重细节。
 *
 * <p>幂等流程：
 * <ol>
 *   <li>以 message_id + consumer_group 查询 t_message_receipt，已存在则直接返回 CommitMessage（幂等跳过）</li>
 *   <li>执行子类 doConsume() 业务逻辑</li>
 *   <li>成功：写入 t_message_receipt（status=1）→ 返回 CommitMessage</li>
 *   <li>失败：写入 t_message_receipt（status=2，含 errorCode / errorMessage）→ 返回 ReconsumeLater</li>
 * </ol>
 *
 * <p>子类扩展点：
 * <ul>
 *   <li>{@link #doConsume(Message)}：实现具体业务消费逻辑</li>
 *   <li>{@link #getConsumerGroup()}：返回当前消费者组名称，不得硬编码</li>
 * </ul>
 *
 * <p>依赖说明：{@link MessageReceiptRepository} 由 Step 3 完成后注入实现类，
 * 当前 Step 4.1 仅依赖接口，两步可并行推进。
 *
 * <p>异常处理：
 * <ul>
 *   <li>业务异常 → 写入失败回执（status=2），返回 ReconsumeLater 触发 ONS 重试</li>
 *   <li>回执写入失败 → 记录 error 日志，不影响消费结果（消息可靠性优先）</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractMqConsumer implements MessageListener {

    /** 消息回执存储接口，Step 3 完成后由 MessageReceiptRepositoryImpl 实现注入 */
    private final MessageReceiptRepository receiptRepository;

    // ==================== 消费状态常量 ====================

    /** 消费成功状态 */
    private static final Integer RECEIPT_STATUS_SUCCESS = 1;

    /** 消费失败状态 */
    private static final Integer RECEIPT_STATUS_FAILED = 2;

    // ==================== MessageListener 实现 ====================

    /**
     * ONS 框架回调入口，内置幂等校验，final 禁止子类覆盖
     */
    @Override
    public final Action consume(Message message, ConsumeContext context) {
        String messageId = message.getMsgID();
        // ONS Key 对应业务 messageId（t_local_message.message_id）
        String businessKey = message.getKey();
        String topic = message.getTopic();
        String consumerGroup = getConsumerGroup();

        log.info("[MQ] 收到消息 topic={} messageId={} businessKey={} consumerGroup={}",
                topic, messageId, businessKey, consumerGroup);

        // ── 步骤 1：幂等检查 ──────────────────────────────────────────────
        if (receiptRepository.existsByMessageIdAndConsumerGroup(messageId, consumerGroup)) {
            // 已消费过，直接提交，防止重复处理（幂等跳过）
            log.info("[MQ] 幂等跳过 messageId={} consumerGroup={}", messageId, consumerGroup);
            return Action.CommitMessage;
        }

        // ── 步骤 2：执行业务逻辑 ─────────────────────────────────────────
        LocalDateTime receivedTime = LocalDateTime.now();
        String payload = extractPayload(message);

        try {
            doConsume(message);
            LocalDateTime processedTime = LocalDateTime.now();

            // ── 步骤 3：写入成功回执 ──────────────────────────────────────
            saveReceipt(messageId, businessKey, consumerGroup, topic,
                    RECEIPT_STATUS_SUCCESS, "", "", payload, receivedTime, processedTime);

            log.info("[MQ] 消费成功 topic={} messageId={} consumerGroup={}", topic, messageId, consumerGroup);
            return Action.CommitMessage;

        } catch (Exception e) {
            LocalDateTime processedTime = LocalDateTime.now();
            // 业务消费异常：记录 error 日志，写入失败回执，返回 ReconsumeLater 触发重试
            log.error("[MQ] 消费失败 topic={} messageId={} consumerGroup={} error={}",
                    topic, messageId, consumerGroup, e.getMessage(), e);

            // ── 步骤 4：写入失败回执 ──────────────────────────────────────
            String errorCode = extractErrorCode(e);
            String errorMessage = truncate(e.getMessage(), 255);
            saveReceipt(messageId, businessKey, consumerGroup, topic,
                    RECEIPT_STATUS_FAILED, errorCode, errorMessage, payload, receivedTime, processedTime);

            return Action.ReconsumeLater;
        }
    }

    // ==================== 子类扩展点 ====================

    /**
     * 子类实现具体业务消费逻辑
     *
     * <p>注意：此方法内抛出任何异常，基类均会捕获并写入失败回执，触发 ONS 重试。
     * 若业务已处理成功但不希望重试，请在方法内自行处理异常，不要向上抛出。
     *
     * @param message ONS 消息对象，含 topic / tag / key / body 等信息
     * @throws Exception 业务处理异常，触发重试
     */
    protected abstract void doConsume(Message message) throws Exception;

    /**
     * 返回当前消费者组名称
     *
     * <p>实现规范：必须从子类注解或构造参数获取，严禁硬编码。
     * 推荐通过 Spring @Value 注入配置文件中的 consumer-group 值。
     *
     * @return 消费者组名称，不可为 null 或空字符串
     */
    protected abstract String getConsumerGroup();

    // ==================== 私有辅助方法 ====================

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
            log.error("[MQ] 回执写入失败 messageId={} consumerGroup={} status={} error={}",
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
     * 提取消息体为 UTF-8 字符串，用于回执快照存储
     *
     * @param message ONS 消息
     * @return 消息体字符串，body 为 null 时返回空字符串
     */
    private String extractPayload(Message message) {
        if (message.getBody() == null) {
            return "";
        }
        return truncate(new String(message.getBody(), StandardCharsets.UTF_8), 65535);
    }

    /**
     * 截断字符串，防止超出数据库字段长度限制
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
