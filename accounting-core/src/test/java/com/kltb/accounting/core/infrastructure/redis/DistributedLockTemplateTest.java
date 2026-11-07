package com.kltb.accounting.core.infrastructure.redis;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * DistributedLockTemplate 单元测试
 */
@ExtendWith(MockitoExtension.class)
class DistributedLockTemplateTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("加锁失败抛 IDEMPOTENT_CONFLICT")
    void execute_lockFailed_shouldThrowIdempotentConflict() throws Exception {
        TenantContext.set(1001);
        when(redissonClient.getLock("accounting:1001:lock:idempotent:test")).thenReturn(lock);
        when(lock.tryLock(1L, 10L, TimeUnit.SECONDS)).thenReturn(false);

        DistributedLockTemplate template = new DistributedLockTemplate(redissonClient);

        assertThatThrownBy(() -> template.execute("idempotent:test", 1L, 10L, () -> "ok"))
                .isInstanceOf(ServiceException.class)
                .extracting(ex -> ((ServiceException) ex).getResultCode())
                .isEqualTo(ResultCode.IDEMPOTENT_CONFLICT);
    }

    @Test
    @DisplayName("leaseTime=-1 时启用 watchdog 参数")
    void execute_leaseTimeMinusOne_shouldUseWatchdog() throws Exception {
        TenantContext.set(1002);
        when(redissonClient.getLock("accounting:1002:lock:posting:trx:V001")).thenReturn(lock);
        when(lock.tryLock(2L, -1L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLockTemplate template = new DistributedLockTemplate(redissonClient);
        String result = template.execute("posting:trx:V001", 2L, -1L, () -> "done");

        assertThat(result).isEqualTo("done");
        verify(lock).tryLock(2L, -1L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("业务执行后 finally 释放锁")
    void execute_shouldUnlockInFinally() throws Exception {
        TenantContext.set(1003);
        when(redissonClient.getLock("accounting:1003:lock:idempotent:trace:001")).thenReturn(lock);
        when(lock.tryLock(1L, 5L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        DistributedLockTemplate template = new DistributedLockTemplate(redissonClient);

        assertThatThrownBy(() -> template.execute("idempotent:trace:001", 1L, 5L, () -> {
            throw new RuntimeException("biz failed");
        })).isInstanceOf(RuntimeException.class);

        verify(lock).unlock();
    }
}



