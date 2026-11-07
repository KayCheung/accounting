package com.kltb.accounting.core.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 资金冻结配置属性，支持 Nacos 动态刷新。
 *
 * <h3>Nacos 配置示例（accounting-core.yml）</h3>
 * <pre>
 * accounting:
 *   freeze:
 *     default-expire-seconds: 1800
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "accounting.freeze")
public class FreezeProperties {

    /**
     * 预冻结默认有效时长（秒），默认 1800 秒（30 分钟）
     */
    private long defaultExpireSeconds = 1800L;
}
