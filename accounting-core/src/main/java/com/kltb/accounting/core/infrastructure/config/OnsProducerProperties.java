package com.kltb.accounting.core.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * ONS Producer 配置属性。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "accounting.messaging.ons")
public class OnsProducerProperties {

    /** 是否启用 ONS Producer。 */
    private Boolean enabled = false;

    /** ONS 接入地址，例如：http://onsaddr-internet.aliyun.com:80 */
    private String endpoint;

    /** 生产者 ID（Group），例如：PID_xxx */
    private String producerId;

    /** 阿里云 AccessKey。 */
    private String accessKey;

    /** 阿里云 SecretKey。 */
    private String secretKey;

    /** 实例 ID（可选，多实例场景使用）。 */
    private String instanceId;

    /** 发送超时时间（毫秒）。 */
    private Integer sendMsgTimeoutMillis = 3000;

    /**
     * 启用状态下校验必填项。
     */
    public void validateWhenEnabled() {
        if (!Boolean.TRUE.equals(enabled)) {
            return;
        }
        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalStateException("ONS endpoint 未配置：accounting.messaging.ons.endpoint");
        }
        if (!StringUtils.hasText(producerId)) {
            throw new IllegalStateException("ONS producerId 未配置：accounting.messaging.ons.producer-id");
        }
        if (!StringUtils.hasText(accessKey)) {
            throw new IllegalStateException("ONS accessKey 未配置：accounting.messaging.ons.access-key");
        }
        if (!StringUtils.hasText(secretKey)) {
            throw new IllegalStateException("ONS secretKey 未配置：accounting.messaging.ons.secret-key");
        }
    }
}
