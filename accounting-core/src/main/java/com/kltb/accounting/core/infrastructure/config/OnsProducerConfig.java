package com.kltb.accounting.core.infrastructure.config;

import com.aliyun.openservices.ons.api.PropertyKeyConst;
import com.aliyun.openservices.ons.api.bean.ProducerBean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.util.Properties;

/**
 * ONS Producer Bean 配置。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(OnsProducerProperties.class)
public class OnsProducerConfig {

    private final OnsProducerProperties properties;

    /**
     * 仅在 enabled=true 时创建 ProducerBean。
     */
    @Bean(initMethod = "start", destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "accounting.messaging.ons", name = "enabled", havingValue = "true")
    public ProducerBean onsProducerBean() {
        properties.validateWhenEnabled();

        Properties onsProperties = new Properties();
        onsProperties.put(PropertyKeyConst.NAMESRV_ADDR, properties.getEndpoint());
        onsProperties.put(PropertyKeyConst.GROUP_ID, properties.getProducerId());
        onsProperties.put(PropertyKeyConst.AccessKey, properties.getAccessKey());
        onsProperties.put(PropertyKeyConst.SecretKey, properties.getSecretKey());
        onsProperties.put(PropertyKeyConst.SendMsgTimeoutMillis, String.valueOf(properties.getSendMsgTimeoutMillis()));

        if (StringUtils.hasText(properties.getInstanceId())) {
            onsProperties.put(PropertyKeyConst.INSTANCE_ID, properties.getInstanceId());
        }

        ProducerBean producerBean = new ProducerBean();
        producerBean.setProperties(onsProperties);

        log.info("[ONS] ProducerBean 已创建，endpoint={}, producerId={}",
                properties.getEndpoint(), properties.getProducerId());
        return producerBean;
    }
}

