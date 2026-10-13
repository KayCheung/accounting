// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/messaging/RocketMqProducerTemplate.java
package com.kltb.accounting.core.infrastructure.messaging;

import com.kltb.accounting.core.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 统一消息发送模板
 *
 * <p>职责：封装开源 Apache RocketMQ Client 原生 API，提供同步、异步、延迟三种发送模式。
 * 每条消息自动注入 tenantId（来自 TenantContext）和 traceId（来自 Skywalking TraceContext）。
 *
 * <p>可靠性说明：本类不做重试，发送失败仅记录 error 日志，不抛异常阻塞主流程。
 * 消息可靠性由本地消息表（Outbox Pattern）保障。
 *
 * <p>未配置 DefaultMQProducer 时：仅记录 warn 日志并跳过发送，避免启动失败。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RocketMqProducerTemplate {

    /** RocketMQ 生产者 Bean 提供器。未配置 DefaultMQProducer 时返回 null。 */
    private final ObjectProvider<DefaultMQProducer> producerProvider;

    /** UserProperty 中注入租户 ID 的 Key */
    private static final String PROP_TENANT_ID = "tenantId";

    /** UserProperty 中注入链路追踪 ID 的 Key */
    private static final String PROP_TRACE_ID = "traceId";

    /** TraceId 获取失败时的兜底值 */
    private static final String TRACE_ID_DEFAULT = "unknown";

    /**
     * 同步发送消息
     *
     * @param topic 消息主题
     * @param tag 消息标签（可为空字符串）
     * @param businessKey 业务唯一键，对应 t_local_message.message_id，用于幂等
     * @param body 消息体字节数组
     * @return 发送结果，失败或未配置 DefaultMQProducer 时返回 null
     */
    public SendResult send(String topic, String tag, String businessKey, byte[] body) {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            log.warn("[RocketMQ] DefaultMQProducer 未配置，跳过发送 topic={} businessKey={}", topic, businessKey);
            return null;
        }

        Message message = buildMessage(topic, tag, businessKey, body);
        try {
            SendResult result = producer.send(message);
            log.info("[RocketMQ] 同步发送成功 topic={} businessKey={} msgId={}", topic, businessKey, result.getMsgId());
            return result;
        } catch (Exception e) {
            log.error("[RocketMQ] 同步发送失败 topic={} businessKey={} tenantId={} error={}",
                    topic, businessKey, extractTenantId(), e.getMessage(), e);
            return null;
        }
    }

    /**
     * 异步发送消息
     *
     * @param topic 消息主题
     * @param tag 消息标签（可为空字符串）
     * @param businessKey 业务唯一键
     * @param body 消息体字节数组
     */
    public void sendAsync(String topic, String tag, String businessKey, byte[] body) {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            log.warn("[RocketMQ] DefaultMQProducer 未配置，跳过异步发送 topic={} businessKey={}", topic, businessKey);
            return;
        }

        Message message = buildMessage(topic, tag, businessKey, body);
        Integer tenantId = extractTenantId();
        try {
            producer.send(message, new SendCallback() {
                @Override
                public void onSuccess(SendResult sendResult) {
                    log.info("[RocketMQ] 异步发送成功 topic={} businessKey={} msgId={}",
                            topic, businessKey, sendResult.getMsgId());
                }

                @Override
                public void onException(Throwable e) {
                    log.error("[RocketMQ] 异步发送回调失败 topic={} businessKey={} tenantId={} error={}",
                            topic, businessKey, tenantId, e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("[RocketMQ] 异步发送提交失败 topic={} businessKey={} tenantId={} error={}",
                    topic, businessKey, tenantId, e.getMessage(), e);
        }
    }

    /**
     * 延迟发送消息
     *
     * @param topic 消息主题
     * @param tag 消息标签（可为空字符串）
     * @param businessKey 业务唯一键
     * @param body 消息体字节数组
     * @param delayMillis 延迟投递时间（毫秒）
     * @return 发送结果，失败或未配置 DefaultMQProducer 时返回 null
     */
    public SendResult sendDelay(String topic, String tag, String businessKey, byte[] body, long delayMillis) {
        DefaultMQProducer producer = producerProvider.getIfAvailable();
        if (producer == null) {
            log.warn("[RocketMQ] DefaultMQProducer 未配置，跳过延迟发送 topic={} businessKey={}", topic, businessKey);
            return null;
        }

        Message message = buildMessage(topic, tag, businessKey, body);
        message.setDeliverTimeMs(System.currentTimeMillis() + delayMillis);
        try {
            SendResult result = producer.send(message);
            log.info("[RocketMQ] 延迟发送成功 topic={} businessKey={} delayMillis={} msgId={}",
                    topic, businessKey, delayMillis, result.getMsgId());
            return result;
        } catch (Exception e) {
            log.error("[RocketMQ] 延迟发送失败 topic={} businessKey={} tenantId={} delayMillis={} error={}",
                    topic, businessKey, extractTenantId(), delayMillis, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建 RocketMQ Message，强制注入 tenantId 和 traceId 到 UserProperties
     */
    private Message buildMessage(String topic, String tag, String businessKey, byte[] body) {
        Message message = new Message(topic, tag, businessKey, body);
        message.putUserProperty(PROP_TENANT_ID, extractTenantId().toString());
        message.putUserProperty(PROP_TRACE_ID, resolveTraceId());
        return message;
    }

    /**
     * 从 Skywalking 获取 traceId，获取失败时返回默认值
     */
    private String resolveTraceId() {
        try {
            String traceId = TraceContext.traceId();
            return (traceId != null && !traceId.isEmpty()) ? traceId : TRACE_ID_DEFAULT;
        } catch (Exception e) {
            return TRACE_ID_DEFAULT;
        }
    }

    private Integer extractTenantId() {
        return TenantContext.get();
    }
}
