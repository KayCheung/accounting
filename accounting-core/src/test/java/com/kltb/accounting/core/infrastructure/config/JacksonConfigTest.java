package com.kltb.accounting.core.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigTest {

    private final JacksonConfig config = new JacksonConfig();

    private ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jackson2ObjectMapperBuilderCustomizer();
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        customizer.customize(builder);
        return builder.build();
    }

    static class DateHolder {
        private LocalDateTime dateTime;
        private LocalDate date;
        private LocalTime time;

        public LocalDateTime getDateTime() {
            return dateTime;
        }

        public void setDateTime(LocalDateTime dateTime) {
            this.dateTime = dateTime;
        }

        public LocalDate getDate() {
            return date;
        }

        public void setDate(LocalDate date) {
            this.date = date;
        }

        public LocalTime getTime() {
            return time;
        }

        public void setTime(LocalTime time) {
            this.time = time;
        }
    }

    @Test
    @DisplayName("JacksonConfig: 验证 LocalDateTime/LocalDate 全局序列化为规范格式")
    void testSerialization() throws Exception {
        ObjectMapper mapper = createObjectMapper();

        DateHolder holder = new DateHolder();
        holder.setDateTime(LocalDateTime.of(2026, 10, 12, 16, 32, 19));
        holder.setDate(LocalDate.of(2026, 10, 12));
        holder.setTime(LocalTime.of(16, 32, 19));

        String json = mapper.writeValueAsString(holder);

        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"dateTime\":\"2026-10-12 16:32:19\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"date\":\"2026-10-12\""));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("\"time\":\"16:32:19\""));
    }

    @Test
    @DisplayName("JacksonConfig: 验证 LocalDateTime/LocalDate 全局反序列化（支持空格分隔日期时间）")
    void testDeserialization() throws Exception {
        ObjectMapper mapper = createObjectMapper();

        String json = "{\"dateTime\":\"2026-10-12 16:32:19\",\"date\":\"2026-10-12\",\"time\":\"16:32:19\"}";

        DateHolder holder = mapper.readValue(json, DateHolder.class);

        assertEquals(LocalDateTime.of(2026, 10, 12, 16, 32, 19), holder.getDateTime());
        assertEquals(LocalDate.of(2026, 10, 12), holder.getDate());
        assertEquals(LocalTime.of(16, 32, 19), holder.getTime());
    }
}
