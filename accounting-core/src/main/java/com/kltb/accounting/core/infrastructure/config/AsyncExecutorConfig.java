package com.kltb.accounting.core.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 全局线程池配置。
 * <p>
 * 集中管理异步任务的线程池，支持 Nacos 动态调参：
 * 修改 {@code accounting.thread-pool.business-async.*} 后自动原地更新，
 * 无需重启，已有任务不丢失。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AsyncExecutorConfig implements ApplicationListener<EnvironmentChangeEvent> {

    private final ThreadPoolProperties threadPoolProperties;
    private final Environment environment;
    private volatile ThreadPoolExecutor businessAsyncPool;

    /** 需要监听的配置键集合 */
    private static final Set<String> WATCHED_KEYS = Set.of(
            "accounting.thread-pool.business-async.core-pool-size",
            "accounting.thread-pool.business-async.max-pool-size",
            "accounting.thread-pool.business-async.queue-capacity",
            "accounting.thread-pool.business-async.keep-alive-seconds"
    );

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService businessAsyncExecutor() {
        businessAsyncPool = createPool(threadPoolProperties);
        log.info("[THREAD-POOL] businessAsyncExecutor 初始化: core={}, max={}, queue={}, keepAlive={}s",
                threadPoolProperties.getCorePoolSize(),
                threadPoolProperties.getMaxPoolSize(),
                threadPoolProperties.getQueueCapacity(),
                threadPoolProperties.getKeepAliveSeconds());
        return businessAsyncPool;
    }

    /**
     * Nacos 配置刷新后，原地调整线程池参数。
     * <p>
     * {@link EnvironmentChangeEvent} 在配置刷新后触发，此时
     * {@link ThreadPoolProperties} 已被 {@code @RefreshScope} 更新，
     * 只需原地调整线程池即可。
     */
    @Override
    public void onApplicationEvent(EnvironmentChangeEvent event) {
        if (businessAsyncPool == null) {
            return;
        }
        Set<String> keys = event.getKeys();
        if (keys == null || keys.isEmpty()) {
            return;
        }
        boolean changed = !keys.isEmpty() && !keys.stream().noneMatch(WATCHED_KEYS::contains);
        if (!changed) {
            return;
        }

        // 重新从 Environment 读取最新值（@RefreshScope 已更新 properties）
        int core = environment.getProperty("accounting.thread-pool.business-async.core-pool-size", Integer.class, 4);
        int max = environment.getProperty("accounting.thread-pool.business-async.max-pool-size", Integer.class, 8);
        int keepAlive = environment.getProperty("accounting.thread-pool.business-async.keep-alive-seconds", Integer.class, 60);

        businessAsyncPool.setCorePoolSize(core);
        businessAsyncPool.setMaximumPoolSize(max);
        businessAsyncPool.setKeepAliveTime(keepAlive, TimeUnit.SECONDS);

        log.info("[THREAD-POOL] 配置已刷新: core={}, max={}, keepAlive={}s", core, max, keepAlive);
    }

    private static ThreadPoolExecutor createPool(ThreadPoolProperties props) {
        return new ThreadPoolExecutor(
                props.getCorePoolSize(),
                props.getMaxPoolSize(),
                props.getKeepAliveSeconds(), TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(props.getQueueCapacity()),
                new CustomizableThreadFactory("biz-async-"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
