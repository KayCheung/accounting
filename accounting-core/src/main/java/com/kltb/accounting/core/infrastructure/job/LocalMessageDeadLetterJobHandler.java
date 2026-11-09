package com.kltb.accounting.core.infrastructure.job;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.RocketMqProducerTemplate;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地死信消息慢补偿定时任务（XXL-JOB）
 *
 * <p>职责：作为 Tier-2 慢重试巡检兜底，定时扫描因为 RocketMQ 中长时段故障或网络分区累积至 status=3 (FAILED) 的死信消息。
 *
 * <p>执行策略：
 * <ul>
 *   <li>建议调度周期：每 10~30 分钟执行一次</li>
 *   <li>支持 XXL-JOB 分片广播路由与单机路由</li>
 *   <li>过滤条件：status=3 (FAILED) 且 retry_count &lt; 10 且 update_time &lt;= NOW() - 5分钟（冷却防抖）且在窗口期内（默认近 7 天）</li>
 *   <li>单批上限：默认 50 条（可通过参数 limit=XX 覆盖）</li>
 *   <li>重试达上限处理：若 retry_count 达到 10 次，触发 CRITICAL 级别告警日志，提示人工介入处理</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageDeadLetterJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_LIMIT = 50;
    private static final int DEFAULT_MAX_DEAD_LETTER_RETRY = 10;
    private static final int DEFAULT_COOLDOWN_MINUTES = 5;
    private static final int DEFAULT_WINDOW_DAYS = 7;
    private static final long CLAIM_PROTECT_SECONDS = 60L;

    private final LocalMessageService localMessageService;
    private final RocketMqProducerTemplate rocketMqProducerTemplate;

    @Override
    protected String jobName() {
        return "LOCAL-MESSAGE-DEAD-LETTER-JOB";
    }

    @XxlJob("localMessageDeadLetterJob")
    public void execute() {
        initContext();
        logStart("");

        String param = ctx().param;
        int limit = parseParamInt(param, "limit", DEFAULT_BATCH_LIMIT);
        int maxRetry = parseParamInt(param, "maxRetry", DEFAULT_MAX_DEAD_LETTER_RETRY);
        int cooldownMinutes = parseParamInt(param, "cooldownMinutes", DEFAULT_COOLDOWN_MINUTES);
        int windowDays = parseParamInt(param, "days", DEFAULT_WINDOW_DAYS);

        LocalDateTime updatedBefore = now().minusMinutes(cooldownMinutes);
        LocalDateTime createdAfter = now().minusDays(windowDays);

        List<LocalMessagePO> deadLetterList = localMessageService.queryFailedByShard(
                limit, ctx().shardIndex, ctx().shardTotal, maxRetry, updatedBefore, createdAfter);

        if (deadLetterList.isEmpty()) {
            log.info("[{}] 当前分片未发现待慢补偿的死信消息: shardIndex={}/{}",
                    jobName(), ctx().shardIndex, ctx().shardTotal);
            return;
        }

        ctx().totalCount(deadLetterList.size());
        log.info("[{}] 扫描到待慢补偿死信消息: count={}, shardIndex={}/{}",
                jobName(), deadLetterList.size(), ctx().shardIndex, ctx().shardTotal);

        for (LocalMessagePO message : deadLetterList) {
            processSingleDeadLetter(message, maxRetry);
        }
    }

    private void processSingleDeadLetter(LocalMessagePO message, int maxRetry) {
        if (message == null || message.getId() == null) {
            return;
        }

        boolean claimed = localMessageService.claimForDeadLetter(
                message.getId(), message.getRetryCount(), CLAIM_PROTECT_SECONDS);
        if (!claimed) {
            log.debug("[{}] 死信消息已被其他任务节点抢占: messageId={}", jobName(), message.getMessageId());
            return;
        }

        try {
            SendResult sendResult = rocketMqProducerTemplate.send(
                    message.getTopic(),
                    message.getTag(),
                    message.getMessageId(),
                    toBody(message.getPayload())
            );
            if (sendResult != null) {
                localMessageService.markSent(message.getMessageId());
                ctx().success();
                log.info("[{}] 死信消息重放投递成功: messageId={}, businessKey={}",
                        jobName(), message.getMessageId(), message.getBusinessKey());
                return;
            }
        } catch (Exception e) {
            log.error("[{}] 死信消息投递异常: messageId={}, error={}",
                    jobName(), message.getMessageId(), e.getMessage(), e);
        }

        int nextRetry = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        String reason = "dead-letter retry failed, retryCount=" + nextRetry;
        localMessageService.recordDeadLetterFailure(message.getMessageId(), nextRetry, reason);
        ctx().fail(reason);

        if (nextRetry >= maxRetry) {
            log.error("[DEAD-LETTER-ALERT] 本地死信消息重试已达慢补偿上限，请人工核查！messageId={}, topic={}, businessKey={}",
                    message.getMessageId(), message.getTopic(), message.getBusinessKey());
        }
    }

    int parseParamInt(String param, String key, int defaultValue) {
        if (StrUtil.isBlank(param)) {
            return defaultValue;
        }
        try {
            for (String pair : param.split("[,;&]")) {
                String[] kv = pair.split("=");
                if (kv.length == 2 && key.equalsIgnoreCase(kv[0].trim())) {
                    return Integer.parseInt(kv[1].trim());
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 解析任务参数异常 key={}, param={}, 使用默认值={}", jobName(), key, param, defaultValue);
        }
        return defaultValue;
    }

    LocalDateTime now() {
        return LocalDateTime.now();
    }

    private byte[] toBody(String payload) {
        if (payload == null) {
            return new byte[0];
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
