package com.kltb.accounting.core.shared.util;

import com.kltb.accounting.api.constant.Constants;

import java.time.LocalDateTime;

/**
 * 日期与时间公共工具类
 */
public final class DateTimeUtil {

    private DateTimeUtil() {
        // 私有构造，工具类禁止实例化
    }

    /**
     * 判断时间是否为未设置过的默认值（null、1970 纪元时间或年份 <= 1970）
     *
     * @param time 待检查的时间
     * @return true-若为 null、等于 1970-01-01 00:00:00 或年份 <= 1970
     */
    public static boolean isDefaultTime(LocalDateTime time) {
        return time == null || time.isEqual(Constants.EPOCH_DATE_TIME) || time.getYear() <= 1970;
    }

    /**
     * 对时间进行脱敏与清洗：若为默认时间或未设置则返回 null，否则返回原时间
     *
     * @param time 待清洗时间
     * @return 清洗后的时间
     */
    public static LocalDateTime sanitizeTime(LocalDateTime time) {
        return isDefaultTime(time) ? null : time;
    }
}
