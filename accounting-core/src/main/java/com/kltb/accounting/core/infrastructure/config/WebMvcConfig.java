package com.kltb.accounting.core.infrastructure.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 全局请求参数格式化与跨域（CORS）配置
 * <p>
 * 支持 GET 请求 URL Query 参数与表单数据向 LocalDate / LocalDateTime / LocalTime 的自动转换，
 * 并提供工业级全局 CORS 支持。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        DateTimeFormatterRegistrar registrar = new DateTimeFormatterRegistrar();
        registrar.setDateFormatter(JacksonConfig.DATE_FORMATTER);
        registrar.setDateTimeFormatter(JacksonConfig.DATE_TIME_FORMATTER);
        registrar.setTimeFormatter(JacksonConfig.TIME_FORMATTER);
        registrar.registerFormatters(registry);
    }

    /**
     * 全局 CORS 过滤器，支持前后端分离独立部署、跨域访问及携带自定义请求头
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistrationBean() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许凭据（Cookie/Authorization）
        config.setAllowCredentials(true);
        // 允许任何域名跨域访问
        config.addAllowedOriginPattern("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 允许所有请求头，包含自定义的 X-Tenant-Id, X-Trace-No, Authorization 等
        config.addAllowedHeader("*");
        // 暴露响应头给前端 JavaScript 读取
        config.addExposedHeader("X-Trace-No");
        config.addExposedHeader("Content-Disposition");
        // 预检请求（OPTIONS）缓存时长（1小时），减少额外网络往返
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        // 最高优先级，确保在所有业务过滤器之前响应 OPTIONS 预检请求
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
