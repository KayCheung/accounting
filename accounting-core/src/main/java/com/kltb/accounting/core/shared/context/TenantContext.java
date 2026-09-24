// accounting-core/src/main/java/com/kltb/accounting/core/shared/context/TenantContext.java
package com.kltb.accounting.core.shared.context;

/**
 * 租户上下文持有器
 *
 * <p>基于 ThreadLocal 实现，在请求入口（Controller 拦截器 / MQ 消费入口）写入 tenantId，
 * 在请求结束时必须调用 {@link #clear()} 清理，防止线程池复用导致租户串号。
 *
 * <p>使用方式：
 * <pre>{@code
 *   // 请求入口写入
 *   TenantContext.set("tenant-123");
 *   try {
 *       // 业务处理
 *   } finally {
 *       TenantContext.clear();
 *   }
 *
 *   // 任意位置读取
 *   String tenantId = TenantContext.get(); // 未设置时返回 "SYSTEM"
 * }</pre>
 */
public final class TenantContext {

    /** 默认租户ID */
    public static final Integer SYSTEM_TENANT = -1;

    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();

    private TenantContext() {
        // 工具类，禁止实例化
    }

    /**
     * 设置当前线程的租户 ID
     *
     * @param tenantId 租户 ID，不可为 null
     */
    public static void set(Integer tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId 不可为 null");
        }
        HOLDER.set(tenantId);
    }

    /**
     * 获取当前线程的租户 ID
     * <p>未设置时返回 {@link #SYSTEM_TENANT}，不返回 null
     *
     * @return 租户 ID
     */
    public static Integer get() {
        Integer tenantId = HOLDER.get();
        return tenantId != null ? tenantId : SYSTEM_TENANT;
    }

    /**
     * 清理当前线程的租户上下文
     * <p>必须在请求结束时调用，防止线程池复用导致租户串号
     */
    public static void clear() {
        HOLDER.remove();
    }
}
