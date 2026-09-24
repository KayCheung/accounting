package com.kltb.accounting.job.job;

import com.aliyun.openservices.ons.api.SendResult;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.OnsProducerTemplate;
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
 *
 * <p>补偿策略：
 * <ul>
 *   <li>扫描条件：status=1 且 next_retry_time &lt;= NOW()</li>
 *   <li>单批上限：100 条</li>
 *   <li>重试退避：第 1 次 10s / 第 2 次 30s / 第 3 次 60s</li>
 *   <li>超限失败：status=3，并输出 ERROR 告警日志</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageRetryJob extends AbstractXxlJobHandler {

    private static final int BATCH_LIMIT = 100;

    private final LocalMessageService localMessageService;
    private final OnsProducerTemplate onsProducerTemplate;

    @Override
    protected String jobName() {
        return "LOCAL-MESSAGE-RETRY-JOB";
    }

    @XxlJob("localMessageRetryJob")
    public void execute() {
        initContext();
        retryPendingMessages(ctx().shardIndex, ctx().shardTotal);
    }

    /**
     * 执行当前分片的补偿扫描
     *
     * @param shardIndex 当前分片序号
     * @param shardTotal 分片总数
     */
    void retryPendingMessages(int shardIndex, int shardTotal) {
        List<LocalMessagePO> pendingList = localMessageService.queryPendingByShard(
                BATCH_LIMIT, shardIndex, shardTotal);

        for (LocalMessagePO message : pendingList) {
            processSingleMessage(message);
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
            SendResult sendResult = onsProducerTemplate.send(
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
        return 60L;
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
