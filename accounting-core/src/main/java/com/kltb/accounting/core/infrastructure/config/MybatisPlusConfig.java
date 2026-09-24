// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/config/MybatisPlusConfig.java
package com.kltb.accounting.core.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 基础设施配置
 * <p>
 * 包含：
 * 1. 乐观锁插件（OptimisticLockerInnerInterceptor）- 支持 @Version 字段 CAS 更新
 * 2. 分页插件（PaginationInnerInterceptor）- 指定 MySQL 类型，防止全表扫描
 * 3. 公共字段自动填充（MetaObjectHandler）- createTime / updateTime 自动注入
 * 4. 含租户插件
 * <p>
 * 逻辑删除全局配置在 application.yml 中声明：
 *   logic-delete-value: 动态时间戳（由 @TableLogic 注解配合使用）
 *   logic-not-delete-value: 0
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    @Bean
    public TenantLineHandler tenantLineHandler() {
        return new TenantLineHandler() {

            /**
             * 获取当前租户 ID（核心方法）
             * 实际场景中：从 ThreadLocal/Token/Spring Security/Spring Session 中获取
             */
            @Override
            public Expression getTenantId() {
                // 示例：从 ThreadLocal 中获取租户 ID（需提前在请求拦截器中设置）
                Integer tenantId = TenantContext.get();

                // 非空校验：无租户 ID 时抛异常（根据业务调整，也可返回 null 跳过过滤）
                if (tenantId == null) {
                    throw new ServiceException(ResultCode.TENANT_ID_IS_NULL, "当前请求未获取到租户ID，拒绝访问");
                }

                // 返回租户 ID 对应的 SQL 表达式（LongValue 适配数值类型，StringValue 适配字符串类型）
                return new LongValue(tenantId);
            }
        };
    }

    /**
     * MyBatis-Plus 拦截器链
     * 注意：插件顺序极其重要！
     * 1. 租户插件（TenantLineInnerInterceptor）必须排在分页插件之前，以保证自动生成的 COUNT 语句也注入租户过滤条件；
     * 2. 乐观锁插件（OptimisticLockerInnerInterceptor）支持 @Version CAS 校验；
     * 3. 分页插件（PaginationInnerInterceptor）最后执行分页与 COUNT 包装。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor(TenantLineHandler tenantLineHandler) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 1. 租户插件（最前：确保 count 和 select 统一注入 tenant_id 条件）
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(tenantLineHandler));
        // 2. 乐观锁插件（缓冲路径 CAS 更新 version 字段）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 3. 分页插件（指定 MySQL，避免全表 COUNT）
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    // 公共字段自动填充由 LogicDeleteConfig（MetaObjectHandler 实现）统一处理
}
