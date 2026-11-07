package com.kltb.accounting.core.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.ServiceException;
import net.sf.jsqlparser.expression.Expression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MyBatis-Plus 多租户配置测试
 */
class MybatisPlusTenantConfigTest {

    private final MybatisPlusConfig config = new MybatisPlusConfig();
    private final TenantLineHandler handler = config.tenantLineHandler();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("基础设施表忽略租户过滤（防止 SQL 注入 tenant_id 报错）")
    void ignoreTable_infrastructureTables_returnsTrue() {
        assertThat(handler.ignoreTable("t_local_message")).isTrue();
        assertThat(handler.ignoreTable("t_message_receipt")).isTrue();
        // 大小写与反引号容错
        assertThat(handler.ignoreTable("`T_LOCAL_MESSAGE`")).isTrue();
        assertThat(handler.ignoreTable("`t_message_receipt`")).isTrue();
        assertThat(handler.ignoreTable("accounting.t_local_message")).isTrue();
    }

    @Test
    @DisplayName("业务表必须注入租户过滤，不可忽略")
    void ignoreTable_businessTables_returnsFalse() {
        assertThat(handler.ignoreTable("t_account")).isFalse();
        assertThat(handler.ignoreTable("t_account_balance")).isFalse();
        assertThat(handler.ignoreTable("t_business_record")).isFalse();
        assertThat(handler.ignoreTable("t_accounting_voucher")).isFalse();
        assertThat(handler.ignoreTable("t_eod_status")).isFalse();
    }

    @Test
    @DisplayName("租户上下文有效时成功获取 TenantId 表达式")
    void getTenantId_withContext_returnsExpression() {
        TenantContext.set(1001);
        Expression expr = handler.getTenantId();
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("1001");
    }

    @Test
    @DisplayName("租户上下文未设置时默认使用 SYSTEM_TENANT (-1)")
    void getTenantId_defaultContext_returnsSystemTenant() {
        TenantContext.clear();
        Expression expr = handler.getTenantId();
        assertThat(expr).isNotNull();
        assertThat(expr.toString()).isEqualTo("-1");
    }
}
