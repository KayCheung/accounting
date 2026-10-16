package com.kltb.accounting.core.infrastructure.config;

import com.kltb.accounting.core.infrastructure.messaging.PostingMessageConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/**
 * RocketMQ Consumer 消费者 Bean 配置
 * <p>
 * 仅在 accounting.messaging.rocketmq.enabled=true 时生效。
 * 负责初始化并常驻运行 DefaultMQPushConsumer，订阅 posting_topic，
 * 并预建 Topic 保证开箱即用与 RocketMQ Dashboard 实时可见。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RocketMqConsumerConfig {

    private final RocketMqProperties properties;
    private final PostingMessageConsumer postingMessageConsumer;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "accounting.messaging.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQPushConsumer postingPushConsumer(DefaultMQProducer producer) throws Exception {
        String group = properties.getConsumer() != null && properties.getConsumer().getPostingGroup() != null
                ? properties.getConsumer().getPostingGroup()
                : "GID_ACCOUNTING_POSTING";
        String topic = properties.getConsumer() != null && properties.getConsumer().getPostingTopic() != null
                ? properties.getConsumer().getPostingTopic()
                : "posting_topic";

        // 1. 若 Broker 开启自动创建 Topic，主动探测/预创建 Topic，确保 Dashboard 立即呈现且 Consumer 订阅就绪
        try {
            if (producer != null) {
                producer.createTopic(producer.getCreateTopicKey(), topic, 4, Collections.emptyMap());
                log.info("[RocketMQ] 预建核心业务 Topic 成功: topic={}", topic);
            }
        } catch (Exception e) {
            log.warn("[RocketMQ] 预建 Topic 提示（可能已存在或 Broker 自动创建生效）: topic={}, error={}", topic, e.getMessage());
        }

        // 2. 初始化 PushConsumer 并订阅业务 Topic
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(group);
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.subscribe(topic, "*");
        consumer.registerMessageListener(postingMessageConsumer);
        consumer.start();

        log.info("[RocketMQ] DefaultMQPushConsumer 启动完成并成功订阅，group={}, topic={}, nameServer={}",
                group, topic, properties.getNameServer());
        return consumer;
    }
}
