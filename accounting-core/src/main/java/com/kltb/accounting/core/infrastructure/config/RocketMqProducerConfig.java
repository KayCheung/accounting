package com.kltb.accounting.core.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ Producer Bean 配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqProducerConfig {

    private final RocketMqProperties properties;

    /**
     * 仅在 enabled=true 时创建 DefaultMQProducer。
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "accounting.messaging.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQProducer rocketMqProducer() {
        properties.validateWhenEnabled();

        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setSendMsgTimeout(properties.getSendMsgTimeoutMillis());
        producer.setRetryTimesWhenSendFailed(properties.getRetryTimes());
        producer.setMaxMessageSize(properties.getMaxMessageSize());

        log.info("[RocketMQ] DefaultMQProducer 已创建，nameServer={}, producerGroup={}",
                properties.getNameServer(), properties.getProducerGroup());
        return producer;
    }
}
