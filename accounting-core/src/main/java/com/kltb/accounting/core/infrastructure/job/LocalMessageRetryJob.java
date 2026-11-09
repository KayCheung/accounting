package com.kltb.accounting.core.infrastructure.job;

import org.apache.rocketmq.client.producer.SendResult;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.RocketMqProducerTemplate;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息补偿扫描任务（XXL-JOB）
 *
 * <p>职责：扫描 t_local_message 中待发送消息，执行补偿发送与重试控制。
 * 支持通过 XXL-JOB 参数动态指定重试模式（默认 PENDING 待发送；支持参数 retryFailed=true 重放已失败消息）。
 *
 * <p>补偿策略：
 * <ul>
 *   <li>扫描条件：status=1 且 next_retry_time &lt;= NOW()</li>
 *   <li>单批上限：100 条（可通过参数 limit=XX 动态覆盖）</li>
 *   <li>重试退避：第 1 次 10s / 第 2 次 30s / 第 3 次 60s / 第 4 次 120s / 第 5 次及以上 300s</li>
 *   <li>超限失败：status=3，并输出 ERROR 告警日志</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageRetryJob extends AbstractXxlJobHandler {

    private static final int BATCH_LIMIT = 100;

    private final LocalMessageService localMessageService;
    private final RocketMqProducerTemplate rocketMqProducerTemplate;

    @Override
    protected String jobName() {
        return "LOCAL-MESSAGE-RETRY-JOB";
    }

    @XxlJob("localMessageRetryJob")
    public void execute() {
        initContext();
        String param = ctx().param;
        int batchLimit = parseBatchLimit(param);

        if (isRetryFailedOnly(param)) {
            log.info("[{}] 触发定向重试失败消息模式: param={}", jobName(), param);
            retryFailedMessages(ctx().shardIndex, ctx().shardTotal, batchLimit);
        } else if (isRetryAll(param)) {
            log.info("[{}] 触发全量（待发送+失败）重试模式: param={}", jobName(), param);
            retryPendingMessages(ctx().shardIndex, ctx().shardTotal, batchLimit);
            retryFailedMessages(ctx().shardIndex, ctx().shardTotal, batchLimit);
        } else {
            retryPendingMessages(ctx().shardIndex, ctx().shardTotal, batchLimit);
        }
    }

    /**
     * 执行当前分片的补偿扫描（使用默认批次上限）
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     */
    void retryPendingMessages(int shardIndex, int shardTotal) {
        retryPendingMessages(shardIndex, shardTotal, BATCH_LIMIT);
    }

    /**
     * 执行当前分片的待发送消息补偿扫描
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @param limit 批次上限
     */
    void retryPendingMessages(int shardIndex, int shardTotal, int limit) {
        List<LocalMessagePO> pendingList = localMessageService.queryPendingByShard(
                limit, shardIndex, shardTotal);

        for (LocalMessagePO message : pendingList) {
            processSingleMessage(message);
        }
    }

    /**
     * 执行当前分片的失败死信消息重试
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     * @param limit 批次上限
     */
    void retryFailedMessages(int shardIndex, int shardTotal, int limit) {
        LocalDateTime createdAfter = now().minusDays(7);
        LocalDateTime updatedBefore = now().minusSeconds(10);
        List<LocalMessagePO> failedList = localMessageService.queryFailedByShard(
                limit, shardIndex, shardTotal, 10, updatedBefore, createdAfter);

        for (LocalMessagePO message : failedList) {
            processSingleFailedMessage(message);
        }
    }

    private void processSingleMessage(LocalMessagePO message) {
        if (message == null || message.getId() == null) {
            return;
        }

        boolean claimed = localMessageService.claimForSend(message.getId(), message.getRetryCount());
        if (!claimed) {
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
                return;
            }
        } catch (Exception e) {
            log.error("[OUTBOX] 补偿发送异常 messageId={} error={}",
                    message.getMessageId(), e.getMessage(), e);
        }

        handleSendFailed(message);
    }

    private void processSingleFailedMessage(LocalMessagePO message) {
        if (message == null || message.getId() == null) {
            return;
        }

        boolean claimed = localMessageService.claimForDeadLetter(message.getId(), message.getRetryCount(), 30L);
        if (!claimed) {
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
                log.info("[OUTBOX] 失败消息重试发送成功 messageId={}", message.getMessageId());
                return;
            }
        } catch (Exception e) {
            log.error("[OUTBOX] 失败消息重试发送异常 messageId={} error={}",
                    message.getMessageId(), e.getMessage(), e);
        }

        int nextRetry = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        String reason = "param retry failed, retryCount=" + nextRetry;
        localMessageService.recordDeadLetterFailure(message.getMessageId(), nextRetry, reason);
        ctx().fail(reason);
    }

    private void handleSendFailed(LocalMessagePO message) {
        int retryCount = message.getRetryCount() == null ? 0 : message.getRetryCount();
        int maxRetry = message.getMaxRetry() == null || message.getMaxRetry() <= 0 ? 3 : message.getMaxRetry();

        if (retryCount < maxRetry) {
            int nextRetryCount = retryCount + 1;
            long backoffSeconds = calculateBackoffSeconds(nextRetryCount);
            LocalDateTime nextRetryTime = now().plusSeconds(backoffSeconds);
            localMessageService.scheduleRetry(message.getMessageId(), nextRetryCount, nextRetryTime);
            return;
        }

        String reason = "retry exhausted, retryCount=" + retryCount + ", maxRetry=" + maxRetry;
        localMessageService.markFailed(message.getMessageId(), reason);
        ctx().fail(reason);
        log.error("[OUTBOX] 本地消息补偿失败并超重试上限 messageId={} reason={}",
                message.getMessageId(), reason);
    }

    long calculateBackoffSeconds(int retryCount) {
        if (retryCount <= 1) {
            return 10L;
        }
        if (retryCount == 2) {
            return 30L;
        }
        if (retryCount == 3) {
            return 60L;
        }
        if (retryCount == 4) {
            return 120L;
        }
        return 300L;
    }

    boolean isRetryFailedOnly(String param) {
        if (param == null || param.trim().isEmpty()) {
            return false;
        }
        String p = param.toLowerCase();
        return p.contains("retryfailed=true") || p.contains("mode=failed") || p.contains("status=failed") || p.contains("status=3");
    }

    boolean isRetryAll(String param) {
        if (param == null || param.trim().isEmpty()) {
            return false;
        }
        String p = param.toLowerCase();
        return p.contains("mode=all") || p.contains("retryall=true");
    }

    int parseBatchLimit(String param) {
        if (param == null || param.trim().isEmpty()) {
            return BATCH_LIMIT;
        }
        try {
            for (String pair : param.split("[,;&]")) {
                String[] kv = pair.split("=");
                if (kv.length == 2 && ("limit".equalsIgnoreCase(kv[0].trim()) || "batchsize".equalsIgnoreCase(kv[0].trim()))) {
                    int limit = Integer.parseInt(kv[1].trim());
                    return Math.min(Math.max(limit, 1), BATCH_LIMIT);
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 解析批次大小参数异常，使用默认值: param={}", jobName(), param);
        }
        return BATCH_LIMIT;
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
