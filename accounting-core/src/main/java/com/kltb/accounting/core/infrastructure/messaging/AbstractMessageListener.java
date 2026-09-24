package com.kltb.accounting.core.infrastructure.messaging;

import com.alibaba.fastjson2.JSON;
import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.MessageListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StopWatch;

import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;

/**
 * ONS MQ 消费公共处理抽象类（模板方法模式）
 * <p>
 * 封装公共逻辑：消息反序列化、日志记录、耗时统计、异常处理。
 * 子类只需实现 {@link #handleMessage(Object, Message, ConsumeContext)} 处理业务逻辑。
 *
 * @param <T> 消息体类型
 */
@Slf4j
public abstract class AbstractMessageListener<T> implements MessageListener {

    @Override
    public Action consume(Message message, ConsumeContext context) {
        StopWatch stopWatch = new StopWatch(getClass().getSimpleName());
        stopWatch.start();
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0) {
                log.error("[MQ消费] 消息体为空，消息ID：{}", message.getMsgID());
                return Action.CommitMessage;
            }
            String messageStr = new String(body, StandardCharsets.UTF_8);
            log.info("[MQ消费] 收到消息: msgId={}, body={}", message.getMsgID(), messageStr);

            // 反序列化
            Class<T> clazz = resolveMessageType();
            T data = JSON.parseObject(messageStr, clazz);

            // 委托子类处理
            return handleMessage(data, message, context);
        } catch (Exception e) {
            log.error("[MQ消费] 消费异常: msgId={}, body={}", message.getMsgID(),
                new String(message.getBody(), StandardCharsets.UTF_8), e);
            throw e;
        } finally {
            stopWatch.stop();
            log.info("[MQ消费] {}", stopWatch.shortSummary());
        }
    }

    /**
     * 处理消息业务逻辑（由子类实现）
     */
    protected abstract Action handleMessage(T data, Message message, ConsumeContext context);

    /**
     * 通过反射解析泛型实际类型
     */
    @SuppressWarnings("unchecked")
    private Class<T> resolveMessageType() {
        ParameterizedType type = (ParameterizedType) getClass().getGenericSuperclass();
        return (Class<T>) type.getActualTypeArguments()[0];
    }
}
