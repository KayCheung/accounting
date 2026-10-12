package com.kltb.accounting.core.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.format.datetime.standard.DateTimeFormatterRegistrar;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 全局请求参数格式化配置
 * <p>
 * 支持 GET 请求 URL Query 参数与表单数据向 LocalDate / LocalDateTime / LocalTime 的自动转换。
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
}
