package com.kltb.accounting.core.infrastructure.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AccountingRuleCacheService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AccountingRuleCacheServiceTest {

    @Mock
    private AccountingRuleMapper ruleMapper;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<Object> rBucket;

    private ObjectMapper objectMapper;
    private AccountingRuleCacheService cacheService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        cacheService = new AccountingRuleCacheService(ruleMapper, redissonClient, objectMapper);
    }

    @Test
    @DisplayName("首次查询回源 DB 并回填 L1/L2，第二次查询直接命中 L1 本地缓存（0 DB 调用）")
    void getByBusinessKey_firstMissThenHitL1() {
        AccountingRulePO po = new AccountingRulePO();
        po.setId(100L);
        po.setBusinessCode("LOAN");
        po.setTradingCode("DISBURSE");
        po.setPayChannel("BANK");
        po.setStatus(RuleStatusEnum.ENABLED);

        when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
        when(rBucket.get()).thenReturn(null); // L2 未命中
        when(ruleMapper.selectByBusinessKey("LOAN", "DISBURSE", "BANK")).thenReturn(po);

        // 第一次查询：回源 DB
        AccountingRulePO first = cacheService.getByBusinessKey("LOAN", "DISBURSE", "BANK");
        assertThat(first).isNotNull();
        assertThat(first.getId()).isEqualTo(100L);
        verify(ruleMapper, times(1)).selectByBusinessKey("LOAN", "DISBURSE", "BANK");
        verify(rBucket, times(1)).set(anyString(), anyLong(), any(TimeUnit.class));

        // 第二次查询：L1 本地缓存命中，完全不查 DB
        AccountingRulePO second = cacheService.getByBusinessKey("LOAN", "DISBURSE", "BANK");
        assertThat(second).isNotNull();
        assertThat(second.getId()).isEqualTo(100L);
        verify(ruleMapper, times(1)).selectByBusinessKey("LOAN", "DISBURSE", "BANK");
    }

    @Test
    @DisplayName("数据库不存在时缓存空值哨兵，防缓存穿透")
    void getByBusinessKey_nullValue_shouldCacheSentinel() {
        when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
        when(rBucket.get()).thenReturn(null);
        when(ruleMapper.selectByBusinessKey("LOAN", "UNKNOWN", "BANK")).thenReturn(null);

        // 第一次查询：返回 null
        AccountingRulePO first = cacheService.getByBusinessKey("LOAN", "UNKNOWN", "BANK");
        assertThat(first).isNull();
        verify(ruleMapper, times(1)).selectByBusinessKey("LOAN", "UNKNOWN", "BANK");

        // 第二次查询：命中空值哨兵，返回 null 且不查 DB
        AccountingRulePO second = cacheService.getByBusinessKey("LOAN", "UNKNOWN", "BANK");
        assertThat(second).isNull();
        verify(ruleMapper, times(1)).selectByBusinessKey("LOAN", "UNKNOWN", "BANK");
    }

    @Test
    @DisplayName("主动驱逐缓存后，再次查询重新回源 DB")
    void evictCache_shouldInvalidateL1AndL2() {
        AccountingRulePO po = new AccountingRulePO();
        po.setId(200L);
        po.setBusinessCode("RETAIL");
        po.setTradingCode("PAY");
        po.setPayChannel("ALIPAY");

        when(redissonClient.getBucket(anyString())).thenReturn(rBucket);
        when(rBucket.get()).thenReturn(null);
        when(ruleMapper.selectByBusinessKey("RETAIL", "PAY", "ALIPAY")).thenReturn(po);

        // 填充缓存
        cacheService.getByBusinessKey("RETAIL", "PAY", "ALIPAY");
        verify(ruleMapper, times(1)).selectByBusinessKey("RETAIL", "PAY", "ALIPAY");

        // 驱逐缓存
        cacheService.evictCache("RETAIL", "PAY", "ALIPAY");
        verify(rBucket, times(1)).delete();

        // 再次查询：重新回源 DB
        cacheService.getByBusinessKey("RETAIL", "PAY", "ALIPAY");
        verify(ruleMapper, times(2)).selectByBusinessKey("RETAIL", "PAY", "ALIPAY");
    }
}
