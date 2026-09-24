// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/monitor/AccountingMetrics.java
package com.kltb.accounting.core.infrastructure.monitor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 记账业务自定义指标（Micrometer）
 *
 * <p>职责：暴露记账链路核心健康状态，供 Prometheus 采集与告警。
 *
 * <p>命名说明：Java 代码中使用点号分隔（如 accounting.posting.total），
 * Micrometer 注册到 Prometheus 时会自动转换为下划线（accounting_posting_total），
 * 与 alert-rules.yml 中的查询表达式保持一致。
 *
 * <p>指标列表：
 * <ul>
 *   <li>accounting_posting_total（Counter）：记账请求总数，含 status=success/failed 标签。
 *       每次记账完成后调用 {@link #recordPosting(boolean)} 采集。</li>
 *   <li>accounting_posting_duration_seconds（Timer）：记账执行耗时，P99 重点关注。
 *       记账开始调用 {@link #startPostingTimer()}，结束调用 {@link Timer.Sample#stop(Timer)}。</li>
 *   <li>accounting_buffer_pending_count（Gauge）：缓冲队列待处理数量（实时从 DB 读取）。
 *       由外部定时任务通过 {@link #setBufferPendingCount(long)} 更新。</li>
 *   <li>accounting_local_message_failed_count（Gauge）：本地消息表失败数量（status=3）。
 *       由外部定时任务通过 {@link #setLocalMessageFailedCount(long)} 更新。</li>
 *   <li>accounting_eod_status（Gauge）：日切状态：1-正常 / 0-异常。
 *       日切 Job 执行后通过 {@link #setEodStatus(int)} 更新。</li>
 * </ul>
 *
 * <p>是否记账：否（可观测性基础设施）。
 */
@Slf4j
@Component
public class AccountingMetrics {

    private static final String POSTING_TOTAL = "accounting.posting.total";
    private static final String POSTING_DURATION = "accounting.posting.duration.seconds";
    private static final String BUFFER_PENDING = "accounting.buffer.pending.count";
    private static final String LOCAL_MSG_FAILED = "accounting.local.message.failed.count";
    private static final String EOD_STATUS = "accounting.eod.status";

    private final MeterRegistry registry;
    private final Counter postingSuccessCounter;
    private final Counter postingFailedCounter;
    private final Timer postingDurationTimer;

    private final AtomicLong bufferPendingCount = new AtomicLong(0);
    private final AtomicLong localMessageFailedCount = new AtomicLong(0);
    private final AtomicLong eodStatus = new AtomicLong(1);

    /**
     * Spring 容器初始化时注册指标
     */
    public AccountingMetrics(MeterRegistry registry) {
        this.registry = registry;

        this.postingSuccessCounter = Counter.builder(POSTING_TOTAL)
                .tag("status", "success")
                .description("记账请求总数（成功）")
                .register(registry);

        this.postingFailedCounter = Counter.builder(POSTING_TOTAL)
                .tag("status", "failed")
                .description("记账请求总数（失败）")
                .register(registry);

        this.postingDurationTimer = Timer.builder(POSTING_DURATION)
                .description("记账执行耗时")
                .sla(Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofSeconds(5))
                .register(registry);

        Gauge.builder(BUFFER_PENDING, bufferPendingCount, AtomicLong::get)
                .description("缓冲队列待处理数量")
                .register(registry);

        Gauge.builder(LOCAL_MSG_FAILED, localMessageFailedCount, AtomicLong::get)
                .description("本地消息表失败数量（status=3）")
                .register(registry);

        Gauge.builder(EOD_STATUS, eodStatus, AtomicLong::get)
                .description("日切状态：1-正常 / 0-异常")
                .register(registry);
    }

    /**
     * 记录一次记账请求结果
     *
     * @param success true-成功，false-失败
     */
    public void recordPosting(boolean success) {
        if (success) {
            postingSuccessCounter.increment();
        } else {
            postingFailedCounter.increment();
        }
    }

    /**
     * 创建记账耗时计时器样本，调用方需在记账完成后调用 sample.stop()
     *
     * @return Timer.Sample 实例
     */
    public Timer.Sample startPostingTimer() {
        return Timer.start(registry);
    }

    /**
     * 更新缓冲队列待处理数量
     *
     * @param count 待处理数量
     */
    public void setBufferPendingCount(long count) {
        bufferPendingCount.set(count);
    }

    /**
     * 更新本地消息表失败数量
     *
     * @param count 失败数量
     */
    public void setLocalMessageFailedCount(long count) {
        localMessageFailedCount.set(count);
    }

    /**
     * 更新日切状态
     *
     * @param status 1-正常 / 0-异常
     */
    public void setEodStatus(int status) {
        eodStatus.set(status);
    }

    // ---- Getter 供外部读取 ----

    public Counter getPostingSuccessCounter() {
        return postingSuccessCounter;
    }

    public Counter getPostingFailedCounter() {
        return postingFailedCounter;
    }

    public Timer getPostingDurationTimer() {
        return postingDurationTimer;
    }
}
