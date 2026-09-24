package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 冻结编号生成器（Lua 脚本原子化初始化，消除 TOCTOU 竞态）
 * 格式：FRZ + yyyyMMdd + seq6，如 FRZ20260611000001
 * Redis key：frz:seq:{yyyyMMdd}
 * TTL：25 小时（每日自动重置）
 */
@Component
@RequiredArgsConstructor
public class FreezeIdGenerator {

    private final RedissonClient redissonClient;

    /**
     * 生成冻结编号（Lua 脚本原子化初始化）
     */
    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "frz:seq:" + date;

        String luaScript =
                "if redis.call('exists', KEYS[1]) == 0 then " +
                "  redis.call('set', KEYS[1], '0') " +
                "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
                "end " +
                "return redis.call('incr', KEYS[1])";

        long seq = redissonClient.getScript(LongCodec.INSTANCE)
                .eval(RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(TimeUnit.HOURS.toSeconds(25)));
        return "FRZ" + date + String.format("%06d", seq);
    }
}
