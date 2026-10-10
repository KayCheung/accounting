package com.kltb.accounting.core.infrastructure.redis;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleMapper;
import com.kltb.accounting.core.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 记账规则二级缓存服务（Caffeine L1 + Redis L2）
 * <p>
 * 读取顺序：Caffeine 本地缓存 → Redis 分布式缓存 → DB 回源。
 * 解决核心记账链路中 accountingRuleRepository.selectByBusinessKey 被反复多次调用导致的数据库连接池压力。
 * <p>
 * 缓存策略：
 * <ul>
 *   <li>Caffeine 本地缓存：TTL 10 分钟，最大 1000 条（纳秒级命中，避免同进程重复 I/O）</li>
 *   <li>Redis 分布式缓存：TTL 30 分钟，Key=accounting:{tenantId}:rule:{businessCode}:{tradingCode}:{payChannel}</li>
 *   <li>空值缓存：TTL 1 分钟，防止不存在的业务键导致缓存穿透攻击</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountingRuleCacheService {

    private static final long LOCAL_CACHE_MAXIMUM_SIZE = 1000L;
    private static final int LOCAL_CACHE_TTL_MINUTES = 10;
    private static final int REDIS_CACHE_TTL_MINUTES = 30;

    /** 哨兵对象：用于空值缓存防穿透 */
    private static final AccountingRulePO NULL_SENTINEL = new AccountingRulePO();

    private final AccountingRuleMapper ruleMapper;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, AccountingRulePO> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(LOCAL_CACHE_TTL_MINUTES))
            .maximumSize(LOCAL_CACHE_MAXIMUM_SIZE)
            .build();

    /**
     * 根据业务键获取记账规则（L1 -> L2 -> DB）
     *
     * @param businessCode 业务线编码
     * @param tradingCode  交易编码
     * @param payChannel   支付渠道
     * @return 规则实体，不存在时返回 null
     */
    public AccountingRulePO getByBusinessKey(String businessCode, String tradingCode, String payChannel) {
        if (StrUtil.isBlank(businessCode) || StrUtil.isBlank(tradingCode) || StrUtil.isBlank(payChannel)) {
            return null;
        }

        String cacheKey = buildCacheKey(businessCode, tradingCode, payChannel);

        // 1. 查询 L1 本地缓存
        AccountingRulePO localValue = localCache.getIfPresent(cacheKey);
        if (localValue != null) {
            return localValue == NULL_SENTINEL ? null : localValue;
        }

        // 2. 查询 L2 Redis 集中缓存
        AccountingRulePO redisValue = readFromRedis(cacheKey);
        if (redisValue != null) {
            localCache.put(cacheKey, redisValue);
            return redisValue == NULL_SENTINEL ? null : redisValue;
        }

        // 3. 回源 DB 查询
        AccountingRulePO dbValue = ruleMapper.selectByBusinessKey(businessCode, tradingCode, payChannel);
        if (dbValue != null) {
            localCache.put(cacheKey, dbValue);
            writeToRedis(cacheKey, dbValue, REDIS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return dbValue;
        } else {
            // 写入空值哨兵，防缓存穿透
            localCache.put(cacheKey, NULL_SENTINEL);
            writeToRedis(cacheKey, NULL_SENTINEL, 1, TimeUnit.MINUTES);
            return null;
        }
    }

    /**
     * 主动失效指定业务键的规则缓存（规则创建、修改、启用、停用时调用）
     */
    public void evictCache(String businessCode, String tradingCode, String payChannel) {
        if (StrUtil.isBlank(businessCode) || StrUtil.isBlank(tradingCode) || StrUtil.isBlank(payChannel)) {
            return;
        }
        String cacheKey = buildCacheKey(businessCode, tradingCode, payChannel);
        localCache.invalidate(cacheKey);
        deleteRedis(cacheKey);
        log.debug("[RuleCache] 已驱逐规则缓存: key={}", cacheKey);
    }

    /**
     * 清空全部本地规则缓存
     */
    public void evictAllLocal() {
        localCache.invalidateAll();
    }

    private String buildCacheKey(String businessCode, String tradingCode, String payChannel) {
        return RedisKeyConstants.Cache.ruleKey(TenantContext.get(), businessCode, tradingCode, payChannel);
    }

    private AccountingRulePO readFromRedis(String cacheKey) {
        if (redissonClient == null) {
            return null;
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(cacheKey);
            String json = bucket.get();
            if (StrUtil.isBlank(json)) {
                return null;
            }
            if ("{}".equals(json.trim()) || "NULL".equals(json.trim())) {
                return NULL_SENTINEL;
            }
            return objectMapper.readValue(json, AccountingRulePO.class);
        } catch (Exception e) {
            log.warn("[RuleCache] Redis 读取规则缓存异常，降级回源 DB: key={}, error={}", cacheKey, e.getMessage());
            return null;
        }
    }

    private void writeToRedis(String cacheKey, AccountingRulePO value, long timeout, TimeUnit timeUnit) {
        if (redissonClient == null) {
            return;
        }
        try {
            RBucket<String> bucket = redissonClient.getBucket(cacheKey);
            String json = (value == NULL_SENTINEL) ? "{}" : objectMapper.writeValueAsString(value);
            bucket.set(json, timeout, timeUnit);
        } catch (Exception e) {
            log.warn("[RuleCache] Redis 写入规则缓存失败（不阻断主流程）: key={}, error={}", cacheKey, e.getMessage());
        }
    }

    private void deleteRedis(String cacheKey) {
        if (redissonClient == null) {
            return;
        }
        try {
            redissonClient.getBucket(cacheKey).delete();
        } catch (Exception e) {
            log.warn("[RuleCache] Redis 删除规则缓存失败: key={}, error={}", cacheKey, e.getMessage());
        }
    }
}
