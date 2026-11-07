package com.kltb.accounting.core.shared.util;

import com.kltb.accounting.api.constant.Constants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DateTimeUtil 单元测试
 */
class DateTimeUtilTest {

    @Test
    @DisplayName("isDefaultTime: null、1970 纪元时间或年份 <= 1970 判定为默认时间")
    void isDefaultTime_tests() {
        assertThat(DateTimeUtil.isDefaultTime(null)).isTrue();
        assertThat(DateTimeUtil.isDefaultTime(Constants.EPOCH_DATE_TIME)).isTrue();
        assertThat(DateTimeUtil.isDefaultTime(LocalDateTime.of(1970, 1, 1, 0, 0, 0))).isTrue();
        assertThat(DateTimeUtil.isDefaultTime(LocalDateTime.of(1969, 12, 31, 23, 59, 59))).isTrue();

        assertThat(DateTimeUtil.isDefaultTime(LocalDateTime.of(2026, 10, 21, 17, 0, 0))).isFalse();
    }

    @Test
    @DisplayName("sanitizeTime: 默认时间清洗为 null，正常时间原样返回")
    void sanitizeTime_tests() {
        assertThat(DateTimeUtil.sanitizeTime(null)).isNull();
        assertThat(DateTimeUtil.sanitizeTime(Constants.EPOCH_DATE_TIME)).isNull();
        assertThat(DateTimeUtil.sanitizeTime(LocalDateTime.of(1970, 1, 1, 8, 0, 0))).isNull();

        LocalDateTime normal = LocalDateTime.of(2026, 10, 21, 17, 30, 0);
        assertThat(DateTimeUtil.sanitizeTime(normal)).isEqualTo(normal);
    }
}
