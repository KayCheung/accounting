package com.kltb.accounting.core.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.DictionaryMapper;
import com.kltb.accounting.core.shared.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DictionaryCacheService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DictionaryCacheServiceTest {

    @Mock
    private DictionaryMapper dictionaryMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Object> bucket;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("读取路径：本地缓存命中")
    void getByType_shouldHitLocalCache() {
        TenantContext.set(2001);
        when(redissonClient.getBucket("accounting:2001:dict:PAY_CHANNEL")).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        List<DictionaryPO> dbRows = List.of(new DictionaryPO().setDictType("PAY_CHANNEL").setDictCode("WX"));
        when(dictionaryMapper.selectList(any())).thenReturn(dbRows);

        DictionaryCacheService service = new DictionaryCacheService(dictionaryMapper, redissonClient, objectMapper);

        List<DictionaryPO> first = service.getByType("PAY_CHANNEL");
        List<DictionaryPO> second = service.getByType("PAY_CHANNEL");

        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        verify(dictionaryMapper, times(1)).selectList(any());
        verify(bucket, times(1)).get();
    }

    @Test
    @DisplayName("读取路径：Redis 命中并回填本地")
    void getByType_shouldHitRedisBeforeDb() throws Exception {
        TenantContext.set(2002);
        String key = "accounting:2002:dict:AUXILIARY_TYPE";
        List<DictionaryPO> redisRows = List.of(new DictionaryPO().setDictType("AUXILIARY_TYPE").setDictCode("ASSET"));
        String payload = objectMapper.writeValueAsString(redisRows);

        when(redissonClient.getBucket(key)).thenReturn(bucket);
        when(bucket.get()).thenReturn(payload);

        DictionaryCacheService service = new DictionaryCacheService(dictionaryMapper, redissonClient, objectMapper);

        List<DictionaryPO> result = service.getByType("AUXILIARY_TYPE");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDictCode()).isEqualTo("ASSET");
        verify(dictionaryMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("读取路径：Redis 未命中时 DB 回源并写回 Redis")
    void getByType_shouldFallbackToDbAndWriteRedis() {
        TenantContext.set(2003);
        String key = "accounting:2003:dict:TRADING_CODE";
        when(redissonClient.getBucket(key)).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        List<DictionaryPO> dbRows = List.of(new DictionaryPO().setDictType("TRADING_CODE").setDictCode("INCOME"));
        when(dictionaryMapper.selectList(any())).thenReturn(dbRows);

        DictionaryCacheService service = new DictionaryCacheService(dictionaryMapper, redissonClient, objectMapper);

        List<DictionaryPO> result = service.getByType("TRADING_CODE");

        assertThat(result).hasSize(1);
        verify(dictionaryMapper, times(1)).selectList(any());
        verify(bucket).set(any(), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("字典更新后，本地缓存和 Redis 缓存均被清除")
    void invalidate_shouldClearLocalAndRedis() {
        TenantContext.set(2004);
        String key = "accounting:2004:dict:RISK_FLAG";
        when(redissonClient.getBucket(key)).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);
        when(dictionaryMapper.selectList(any())).thenReturn(
                List.of(new DictionaryPO().setDictType("RISK_FLAG").setDictCode("BLOCK_IN")),
                List.of(new DictionaryPO().setDictType("RISK_FLAG").setDictCode("BLOCK_OUT"))
        );

        DictionaryCacheService service = new DictionaryCacheService(dictionaryMapper, redissonClient, objectMapper);

        List<DictionaryPO> first = service.getByType("RISK_FLAG");
        service.invalidate("RISK_FLAG");
        List<DictionaryPO> second = service.getByType("RISK_FLAG");

        assertThat(first.get(0).getDictCode()).isEqualTo("BLOCK_IN");
        assertThat(second.get(0).getDictCode()).isEqualTo("BLOCK_OUT");
        verify(bucket, atLeastOnce()).delete();
        verify(dictionaryMapper, times(2)).selectList(any());
    }
}
