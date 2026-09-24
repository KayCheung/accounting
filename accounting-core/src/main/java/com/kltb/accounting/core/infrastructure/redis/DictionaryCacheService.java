package com.kltb.accounting.core.infrastructure.redis;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.DictionaryMapper;
import com.kltb.accounting.core.shared.context.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 字典二级缓存服务（Caffeine + Redis）
 *
 * <p>读取顺序：Caffeine 本地缓存 → Redis → DB。
 * DB 回源后回填本地缓存与 Redis，保障后续读取性能。
 *
 * <p>是否记账：否（基础设施缓存能力）。
 *
 * <p>缓存策略：
 * <ul>
 *   <li>Caffeine：TTL 5 分钟，最大 1000 条（Redis 故障兜底）</li>
 *   <li>Redis：TTL 10 分钟，Key=accounting:{tenantId}:dict:{dictType}</li>
 * </ul>
 *
 * <p>异常处理：
 * <ul>
 *   <li>Redis 读写异常：仅记录 error 日志，降级 DB 回源，不阻断主流程</li>
 *   <li>序列化异常：记录 error 后回源 DB 重建缓存</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictionaryCacheService {

    private static final String DICT_CACHE_KEY_FORMAT = "accounting:%s:dict:%s";
    private static final long LOCAL_CACHE_MAXIMUM_SIZE = 1000L;
    private static final int LOCAL_CACHE_TTL_MINUTES = 5;
    private static final int REDIS_CACHE_TTL_MINUTES = 10;

    private final DictionaryMapper dictionaryMapper;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    private final Cache<String, List<DictionaryPO>> localCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(LOCAL_CACHE_TTL_MINUTES))
            .maximumSize(LOCAL_CACHE_MAXIMUM_SIZE)
            .build();

    /**
     * 按字典类型读取字典项（全量）
     *
     * @param dictType 字典类型
     * @return 字典项列表
     */
    public List<DictionaryPO> getByType(String dictType) {
        if (isBlank(dictType)) {
            return Collections.emptyList();
        }

        String cacheKey = buildCacheKey(dictType);
        List<DictionaryPO> localValue = localCache.getIfPresent(cacheKey);
        if (localValue != null) {
            return Collections.unmodifiableList(localValue);
        }

        List<DictionaryPO> redisValue = readFromRedis(cacheKey);
        if (redisValue != null) {
            localCache.put(cacheKey, copyOf(redisValue));
            return Collections.unmodifiableList(redisValue);
        }

        List<DictionaryPO> dbValue = queryFromDb(dictType);
        localCache.put(cacheKey, copyOf(dbValue));
        writeToRedis(cacheKey, dbValue);
        return Collections.unmodifiableList(dbValue);
    }

    /**
     * 刷新指定类型缓存（先删后建）
     *
     * @param dictType 字典类型
     * @return 最新字典项列表
     */
    public List<DictionaryPO> refresh(String dictType) {
        invalidate(dictType);
        return getByType(dictType);
    }

    /**
     * 刷新全部字典缓存（谨慎使用）
     */
    public void refreshAll() {
        Set<String> dictTypes = queryAllDictTypes();
        invalidateAll();
        for (String dictType : dictTypes) {
            try {
                getByType(dictType);
            } catch (Exception ex) {
                log.error("[DICT-CACHE] 刷新字典缓存失败 dictType={} tenantId={} error={}",
                        dictType, TenantContext.get(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * 清除指定字典类型缓存（用于字典写操作后的失效）
     *
     * @param dictType 字典类型
     */
    public void invalidate(String dictType) {
        if (isBlank(dictType)) {
            return;
        }
        String cacheKey = buildCacheKey(dictType);
        localCache.invalidate(cacheKey);
        deleteRedis(cacheKey);
    }

    /**
     * 清除全部缓存
     */
    public void invalidateAll() {
        localCache.invalidateAll();
        for (String dictType : queryAllDictTypes()) {
            deleteRedis(buildCacheKey(dictType));
        }
    }

    private List<DictionaryPO> readFromRedis(String cacheKey) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(cacheKey);
            String payload = bucket.get();
            if (isBlank(payload)) {
                return null;
            }
            return objectMapper.readValue(payload, new TypeReference<List<DictionaryPO>>() {
            });
        } catch (Exception ex) {
            log.error("[DICT-CACHE] Redis读取失败 key={} tenantId={} error={}",
                    cacheKey, TenantContext.get(), ex.getMessage(), ex);
            return null;
        }
    }

    private void writeToRedis(String cacheKey, List<DictionaryPO> value) {
        try {
            RBucket<String> bucket = redissonClient.getBucket(cacheKey);
            bucket.set(objectMapper.writeValueAsString(value), REDIS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception ex) {
            // Redis 故障时由本地缓存兜底，记录日志并继续。
            log.error("[DICT-CACHE] Redis写入失败 key={} tenantId={} error={}",
                    cacheKey, TenantContext.get(), ex.getMessage(), ex);
        }
    }

    private void deleteRedis(String cacheKey) {
        try {
            redissonClient.getBucket(cacheKey).delete();
        } catch (Exception ex) {
            log.error("[DICT-CACHE] Redis删除失败 key={} tenantId={} error={}",
                    cacheKey, TenantContext.get(), ex.getMessage(), ex);
        }
    }

    private List<DictionaryPO> queryFromDb(String dictType) {
        return dictionaryMapper.selectList(new LambdaQueryWrapper<DictionaryPO>()
                .eq(DictionaryPO::getDictType, dictType)
                .orderByAsc(DictionaryPO::getSortOrder, DictionaryPO::getId));
    }

    private Set<String> queryAllDictTypes() {
        List<DictionaryPO> rows = dictionaryMapper.selectList(new LambdaQueryWrapper<DictionaryPO>()
                .select(DictionaryPO::getDictType));
        Set<String> dictTypes = new LinkedHashSet<>();
        for (DictionaryPO row : rows) {
            if (!isBlank(row.getDictType())) {
                dictTypes.add(row.getDictType());
            }
        }
        return dictTypes;
    }

    String buildCacheKey(String dictType) {
        return String.format(DICT_CACHE_KEY_FORMAT, TenantContext.get(), dictType);
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 防御性拷贝：防止调用方修改缓存中的 List 引用
     */
    private List<DictionaryPO> copyOf(List<DictionaryPO> source) {
        return source.stream().collect(Collectors.toUnmodifiableList());
    }
}



