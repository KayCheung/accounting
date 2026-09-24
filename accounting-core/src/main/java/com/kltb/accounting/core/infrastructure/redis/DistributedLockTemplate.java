package com.kltb.accounting.core.infrastructure.redis;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Redisson 分布式锁模板
 *
 * <p>职责：统一封装分布式锁获取、释放和失败处理，避免业务方手工拼接锁 Key。
 *
 * <p>Key 规范：
 * <ul>
 *   <li>内部统一格式：accounting:{tenantId}:lock:{lockKey}</li>
 *   <li>tenantId 自动从 TenantContext 获取，调用方仅传业务 lockKey</li>
 * </ul>
 *
 * <p>是否记账：否（并发控制基础设施）。
 *
 * <p>异常处理：
 * <ul>
 *   <li>加锁失败/超时：抛 ServiceException(ResultCode.IDEMPOTENT_CONFLICT)</li>
 *   <li>线程中断：恢复中断标记后抛 ServiceException(ResultCode.IDEMPOTENT_CONFLICT)</li>
 * </ul>
 *
 * <p>续期策略：
 * <ul>
 *   <li>leaseTime = -1：启用 Redisson watchdog 自动续期（默认 30s）。
 *       在此期间锁会不断自动续期，直到 {@code execute()} 方法正常返回或抛出异常，
 *       最终由 finally 块中的 {@link #unlockSafely} 显式释放锁。
 *       注意：若业务在此场景下发生进程宕机，watchdog 无法续期，锁会在 30s 后自动过期释放。</li>
 *   <li>leaseTime &gt;= 0：使用固定持锁时长，到期自动释放</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockTemplate {

    private static final String LOCK_KEY_FORMAT = "accounting:%s:lock:%s";

    private final RedissonClient redissonClient;

    /**
     * 加锁执行，失败抛 IDEMPOTENT_CONFLICT
     *
     * @param lockKey 业务锁 Key（不含前缀）
     * @param waitTime 等待加锁超时时间（秒）
     * @param leaseTime 持锁最大时间（秒），-1 表示启用 watchdog 自动续期
     * @param supplier 加锁后执行的业务逻辑
     * @param <T> 返回值类型
     * @return supplier 执行结果
     */
    public <T> T execute(String lockKey, long waitTime, long leaseTime, Supplier<T> supplier) {
        if (isBlank(lockKey)) {
            throw new IllegalArgumentException("lockKey 不能为空");
        }
        if (supplier == null) {
            throw new IllegalArgumentException("supplier 不能为空");
        }

        String fullLockKey = buildLockKey(lockKey);
        RLock lock = redissonClient.getLock(fullLockKey);

        boolean locked;
        try {
            locked = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("[LOCK] 线程中断 lockKey={} tenantId={} error={}",
                    fullLockKey, TenantContext.get(), ex.getMessage(), ex);
            throw new ServiceException(ResultCode.IDEMPOTENT_CONFLICT, "获取分布式锁被中断", ex);
        } catch (Exception ex) {
            log.error("[LOCK] 获取锁异常 lockKey={} tenantId={} error={}",
                    fullLockKey, TenantContext.get(), ex.getMessage(), ex);
            throw new ServiceException(ResultCode.IDEMPOTENT_CONFLICT, "获取分布式锁失败", ex);
        }

        if (!locked) {
            log.error("[LOCK] 获取锁超时 lockKey={} waitTime={} leaseTime={} tenantId={}",
                    fullLockKey, waitTime, leaseTime, TenantContext.get());
            throw new ServiceException(ResultCode.IDEMPOTENT_CONFLICT, "幂等冲突，请求已处理");
        }

        try {
            return supplier.get();
        } finally {
            unlockSafely(lock, fullLockKey);
        }
    }

    String buildLockKey(String lockKey) {
        return String.format(LOCK_KEY_FORMAT, TenantContext.get(), lockKey);
    }

    private void unlockSafely(RLock lock, String fullLockKey) {
        try {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        } catch (Exception ex) {
            // 解锁失败通常由网络瞬断/节点切换导致，记录 error 便于排查。
            log.error("[LOCK] 释放锁异常 lockKey={} tenantId={} error={}",
                    fullLockKey, TenantContext.get(), ex.getMessage(), ex);
        }
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
