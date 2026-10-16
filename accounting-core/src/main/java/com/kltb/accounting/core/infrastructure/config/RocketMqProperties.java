package com.kltb.accounting.core.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * RocketMQ 属性配置
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "accounting.messaging.rocketmq")
public class RocketMqProperties {

    /** 是否启用 RocketMQ Producer。 */
    private Boolean enabled = false;

    /** NameServer 接入地址，例如：127.0.0.1:9876 */
    private String nameServer = "127.0.0.1:9876";

    /** 生产者分组，例如：PG_ACCOUNTING_CORE */
    private String producerGroup = "PG_ACCOUNTING_CORE";

    /** 发送超时时间（毫秒）。 */
    private Integer sendMsgTimeoutMillis = 3000;

    /** 发送失败重试次数。 */
    private Integer retryTimes = 2;

    /** 最大消息大小（字节），默认 4MB。 */
    private Integer maxMessageSize = 4 * 1024 * 1024;

    /** AccessKey（可选，用于启用 ACL）。 */
    private String accessKey;

    /** SecretKey（可选，用于启用 ACL）。 */
    private String secretKey;

    /** 消费者配置 */
    private ConsumerProperties consumer = new ConsumerProperties();

    @Getter
    @Setter
    public static class ConsumerProperties {
        /** 异步过账消费组 */
        private String postingGroup = "GID_ACCOUNTING_POSTING";
        /** 异步过账 Topic */
        private String postingTopic = "posting_topic";
    }

    /**
     * 启用状态下校验必填项。
     */
    public void validateWhenEnabled() {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        if (!StringUtils.hasText(nameServer)) {
            throw new IllegalStateException("RocketMQ nameServer 未配置：accounting.messaging.rocketmq.name-server");
        }
        if (!StringUtils.hasText(producerGroup)) {
            throw new IllegalStateException("RocketMQ producerGroup 未配置：accounting.messaging.rocketmq.producer-group");
        }
    }
}
