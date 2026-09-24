package com.kltb.accounting.core.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务异步线程池配置，支持 Nacos 动态刷新。
 *
 * <h3>Nacos 配置示例（accounting-core.yml）</h3>
 * <pre>
 * accounting:
 *   thread-pool:
 *     business-async:
 *       core-pool-size: 4
 *       max-pool-size: 8
 *       queue-capacity: 256
 *       keep-alive-seconds: 60
 * </pre>
 *
 * 修改 Nacos 配置后自动刷新，无需重启。线程池原地调参，已有任务不丢失。
 */
@Data
@Component
@ConfigurationProperties(prefix = "accounting.thread-pool.business-async")
public class ThreadPoolProperties {

    /** 核心线程数，默认 4 */
    private int corePoolSize = 4;

    /** 最大线程数，默认 8 */
    private int maxPoolSize = 8;

    /** 阻塞队列容量，默认 256 */
    private int queueCapacity = 256;

    /** 空闲线程存活时间（秒），默认 60 */
    private int keepAliveSeconds = 60;
}
