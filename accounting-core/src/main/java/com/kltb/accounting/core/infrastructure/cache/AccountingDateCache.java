package com.kltb.accounting.core.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * 全局会计日期二级缓存（Step 17S Phase 2 新增）
 * <p>
 * L1: Caffeine 本地缓存（单实例内共享，TTL 24h）
 * L2: Redis 分布式缓存（多实例共享，TTL 24h）
 * DB:  t_eod_status 兜底
 * <p>
 * Redis Key: "accounting:date:current"
 * 读取顺序：L1 → L2 → DB
 * 写入顺序：DB → L2 → Pub/Sub 通知 → 刷新所有实例 L1
 * <p>
 * Pub/Sub: 切日时通过 RTopic 发布通知，所有实例收到后清除本地 L1 缓存
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountingDateCache {

    private static final String REDIS_KEY = "accounting:date:current";
    private static final String PUBSUB_CHANNEL = "accounting:date:notify";
    private static final long TTL_HOURS = 24;

    private final RedissonClient redissonClient;
    private final EodStatusRepository eodStatusRepository;

    /** L1: Caffeine 本地缓存 */
    private Cache<String, LocalDate> localCache;

    @PostConstruct
    public void init() {
        localCache = Caffeine.newBuilder()
                .maximumSize(10)
                .expireAfterWrite(TTL_HOURS, TimeUnit.HOURS)
                .build();

        // 订阅 Pub/Sub 频道，切日通知到达后清除本地 L1 缓存
        RTopic topic = redissonClient.getTopic(PUBSUB_CHANNEL);
        topic.addListener(String.class, (MessageListener<String>) (channel, msg) -> {
            log.info("[ACCOUNTING-DATE] 收到切日通知: newDate={}", msg);
            invalidate();
        });
    }

    /**
     * 获取当前会计日期
     * <p>
     * 读取顺序：L1 → L2 → DB
     *
     * @return 当前会计日期
     */
    public LocalDate getCurrentDate() {
        // L1: Caffeine
        LocalDate cached = localCache.getIfPresent(REDIS_KEY);
        if (cached != null) {
            return cached;
        }

        // L2: Redis
        // 注：Redis 中的值由 setCurrentDate() 写入，来源必定是 DB 兜底（eod_status=8），
        // 所以读取时不再校验 eod_status，直接信任。
        RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY);
        String redisValue = bucket.get();
        if (redisValue != null) {
            LocalDate date = LocalDate.parse(redisValue);
            localCache.put(REDIS_KEY, date);
            return date;
        }

        // DB 兜底
        EodStatusPO latest = eodStatusRepository.findLatestCompleted();
        if (latest != null && latest.getEodStatus() == 8) {
            LocalDate date = latest.getAccountingDate().plusDays(1);
            localCache.put(REDIS_KEY, date);
            bucket.set(date.toString(), TTL_HOURS, TimeUnit.HOURS);
            log.info("[ACCOUNTING-DATE] 从 DB 初始化缓存: date={}", date);
            return date;
        }

        // 首次启动，无历史记录
        LocalDate today = LocalDate.now();
        localCache.put(REDIS_KEY, today);
        bucket.set(today.toString(), TTL_HOURS, TimeUnit.HOURS);
        log.info("[ACCOUNTING-DATE] 首次启动，使用系统日期: date={}", today);
        return today;
    }

    /**
     * 设置会计日期（切日时调用）
     * <p>
     * 写入顺序：L2 → L1 → Pub/Sub 通知其他实例
     *
     * @param newDate 新会计日期
     */
    public void setCurrentDate(LocalDate newDate) {
        // L2: Redis
        RBucket<String> bucket = redissonClient.getBucket(REDIS_KEY);
        bucket.set(newDate.toString(), TTL_HOURS, TimeUnit.HOURS);

        // L1: Caffeine
        localCache.put(REDIS_KEY, newDate);

        // Pub/Sub: 通知其他实例刷新本地缓存
        RTopic topic = redissonClient.getTopic(PUBSUB_CHANNEL);
        topic.publish(newDate.toString());

        log.info("[ACCOUNTING-DATE] 缓存更新: date={}", newDate);
    }

    /**
     * 清除本地缓存（用于 Pub/Sub 通知后刷新）
     */
    public void invalidate() {
        localCache.invalidate(REDIS_KEY);
        log.info("[ACCOUNTING-DATE] 本地缓存已清除");
    }
}
