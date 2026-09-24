// accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/account/TransactionNoGenerator.java
package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 事务编号生成器（M2 修复）
 * 格式：TXN + yyyyMMdd + seq6，如 TXN20260512000001
 * Redis key：txn:seq:{yyyyMMdd}
 * TTL：25 小时（每日自动重置）
 */
@Component
@RequiredArgsConstructor
public class TransactionNoGenerator {

    private final RedissonClient redissonClient;

    /**
     * 生成事务编号（P0-1 修复：Lua 脚本原子化初始化，消除 isExists→set→increment 竞态）
     * 序号从 1 开始，格式化为 6 位数字
     */
    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "txn:seq:" + date;

        // Lua 脚本：原子化执行 "不存在则初始化 0 + 设置 TTL + 递增"
        String luaScript =
                "if redis.call('exists', KEYS[1]) == 0 then " +
                "  redis.call('set', KEYS[1], '0') " +
                "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
                "end " +
                "return redis.call('incr', KEYS[1])";

        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
                .evalAsync(RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(TimeUnit.HOURS.toSeconds(25)));

        long seq;
        try {
            seq = (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成事务编号失败: " + key, e);
        }
        return "TXN" + date + String.format("%06d", seq);
    }
}
