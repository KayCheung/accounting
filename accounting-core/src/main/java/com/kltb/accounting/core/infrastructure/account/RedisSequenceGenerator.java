package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 通用 Redis 序列号生成器（Lua 脚本原子化初始化，消除 TOCTOU 竞态）
 * <p>
 * 基于 Redis INCR 保证并发安全，支持按日或按毫秒级时间窗口重置序列。
 * 所有编号生成共享同一个 Lua 脚本和 Redisson 调用逻辑，仅通过参数区分前缀、
 * 时间格式和 TTL。
 *
 * <h3>使用示例</h3>
 * <pre>
 * // 凭证号：VOU + yyyyMMdd + 6位序号，每日重置，TTL 25h
 * seqGen.generate("VOU", LocalDate.now(), "yyyyMMdd", 6, 25);
 *
 * // 分录号：ENT + yyyyMMddHHmmssSSS + 4位序号，按毫秒窗口重置，TTL 2h
 * seqGen.generate("ENT", LocalDateTime.now(), "yyyyMMddHHmmssSSS", 4, 2);
 *
 * // 结转号：EODTR + yyyyMMdd + 4位序号，每日重置，TTL 25h
 * seqGen.generate("EODTR", date, "yyyyMMdd", 4, 25);
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class RedisSequenceGenerator {

    private static final String LUA_SCRIPT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "  redis.call('set', KEYS[1], '0') " +
            "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
            "end " +
            "return redis.call('incr', KEYS[1])";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RedissonClient redissonClient;

    /**
     * 生成序列号（按日期重置）
     *
     * @param prefix       编号前缀，如 "VOU"、"EODTR"
     * @param date         用于生成 Redis key 的日期（yyyyMMdd）
     * @param seqWidth     序号宽度（补零位数），如 4 → "0001"，6 → "000001"
     * @param ttlHours     Redis key 的 TTL（小时）
     * @return 生成的序列号
     */
    public String generate(String prefix, LocalDate date, int seqWidth, int ttlHours) {
        String dateStr = date.format(DATE_FMT);
        String key = prefix.toLowerCase() + ":seq:" + dateStr;

        long seq = incr(key, ttlHours);
        return prefix + dateStr + String.format("%0" + seqWidth + "d", seq);
    }

    /**
     * 生成序列号（按自定义时间窗口重置）
     *
     * @param prefix       编号前缀，如 "ENT"
     * @param dateTime     用于生成 Redis key 的时间
     * @param timePattern  时间格式化模式，如 "yyyyMMddHHmmssSSS"
     * @param seqWidth     序号宽度（补零位数）
     * @param ttlHours     Redis key 的 TTL（小时）
     * @return 生成的序列号
     */
    public String generate(String prefix, LocalDateTime dateTime, String timePattern,
                           int seqWidth, int ttlHours) {
        String timeStr = dateTime.format(DateTimeFormatter.ofPattern(timePattern));
        String key = prefix.toLowerCase() + ":seq:" + timeStr;

        long seq = incr(key, ttlHours);
        return prefix + timeStr + String.format("%0" + seqWidth + "d", seq);
    }

    private long incr(String key, int ttlHours) {
        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
                .evalAsync(RScript.Mode.READ_WRITE,
                        LUA_SCRIPT,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(TimeUnit.HOURS.toSeconds(ttlHours)));

        try {
            return (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成序列号失败: " + key, e);
        }
    }
}
