package com.kltb.accounting.core.infrastructure.job;

import org.apache.rocketmq.client.producer.SendResult;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.RocketMqProducerTemplate;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LocalMessageDeadLetterJobHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LocalMessageDeadLetterJobHandlerTest {

    @Mock
    private LocalMessageService localMessageService;

    @Mock
    private RocketMqProducerTemplate rocketMqProducerTemplate;

    @Test
    @DisplayName("死信慢重试成功：发送成功后 markSent 并记录 success")
    void execute_sendSuccess_shouldMarkSent() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        LocalMessagePO message = buildDeadLetterMessage(100L, "dead-msg-1", 3);

        when(localMessageService.queryFailedByShard(eq(50), eq(0), eq(1), eq(10), any(), any()))
                .thenReturn(List.of(message));
        when(localMessageService.claimForDeadLetter(100L, 3, 60L)).thenReturn(true);
        when(rocketMqProducerTemplate.send(any(), any(), any(), any())).thenReturn(mock(SendResult.class));

        TestableLocalMessageDeadLetterJob job = new TestableLocalMessageDeadLetterJob(
                localMessageService, rocketMqProducerTemplate, fixedNow, 0, 1, null);

        job.execute();

        verify(localMessageService).markSent("dead-msg-1");
        verify(localMessageService, never()).recordDeadLetterFailure(any(), anyInt(), any());
    }

    @Test
    @DisplayName("死信慢重试失败：递增 retryCount 并记录失败信息")
    void execute_sendFailed_shouldRecordFailure() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        LocalMessagePO message = buildDeadLetterMessage(101L, "dead-msg-2", 4);

        when(localMessageService.queryFailedByShard(eq(50), eq(0), eq(1), eq(10), any(), any()))
                .thenReturn(List.of(message));
        when(localMessageService.claimForDeadLetter(101L, 4, 60L)).thenReturn(true);
        when(rocketMqProducerTemplate.send(any(), any(), any(), any())).thenReturn(null);

        TestableLocalMessageDeadLetterJob job = new TestableLocalMessageDeadLetterJob(
                localMessageService, rocketMqProducerTemplate, fixedNow, 0, 1, null);

        job.execute();

        verify(localMessageService, never()).markSent(any());
        verify(localMessageService).recordDeadLetterFailure(
                eq("dead-msg-2"), eq(5), contains("dead-letter retry failed"));
    }

    @Test
    @DisplayName("解析自定义参数 limit/maxRetry/cooldownMinutes")
    void execute_withCustomParams_shouldPassToQuery() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);

        when(localMessageService.queryFailedByShard(eq(20), eq(1), eq(2), eq(5), any(), any()))
                .thenReturn(List.of());

        TestableLocalMessageDeadLetterJob job = new TestableLocalMessageDeadLetterJob(
                localMessageService, rocketMqProducerTemplate, fixedNow, 1, 2, "limit=20,maxRetry=5,cooldownMinutes=10");

        job.execute();

        verify(localMessageService).queryFailedByShard(eq(20), eq(1), eq(2), eq(5), any(), any());
    }

    private LocalMessagePO buildDeadLetterMessage(Long id, String messageId, Integer retryCount) {
        return new LocalMessagePO()
                .setId(id)
                .setMessageId(messageId)
                .setTopic("topic-async-posting")
                .setTag("tag-posting")
                .setBusinessKey("biz-key-" + id)
                .setPayload("{\"data\":\"test\"}")
                .setRetryCount(retryCount)
                .setMaxRetry(3);
    }

    private static class TestableLocalMessageDeadLetterJob extends LocalMessageDeadLetterJobHandler {

        private final LocalDateTime fixedNow;
        private final int testShardIndex;
        private final int testShardTotal;
        private final String testParam;

        private TestableLocalMessageDeadLetterJob(LocalMessageService localMessageService,
                                                 RocketMqProducerTemplate rocketMqProducerTemplate,
                                                 LocalDateTime fixedNow,
                                                 int shardIndex,
                                                 int shardTotal,
                                                 String testParam) {
            super(localMessageService, rocketMqProducerTemplate);
            this.fixedNow = fixedNow;
            this.testShardIndex = shardIndex;
            this.testShardTotal = shardTotal;
            this.testParam = testParam;
        }

        @Override
        public void execute() {
            initContext();
            int limit = parseParamInt(testParam, "limit", 50);
            int maxRetry = parseParamInt(testParam, "maxRetry", 10);
            int cooldownMinutes = parseParamInt(testParam, "cooldownMinutes", 5);
            int windowDays = parseParamInt(testParam, "days", 7);

            LocalDateTime updatedBefore = now().minusMinutes(cooldownMinutes);
            LocalDateTime createdAfter = now().minusDays(windowDays);

            List<LocalMessagePO> deadLetterList = getLocalMessageService().queryFailedByShard(
                    limit, testShardIndex, testShardTotal, maxRetry, updatedBefore, createdAfter);

            for (LocalMessagePO message : deadLetterList) {
                boolean claimed = getLocalMessageService().claimForDeadLetter(
                        message.getId(), message.getRetryCount(), 60L);
                if (!claimed) {
                    continue;
                }
                try {
                    SendResult sendResult = getRocketMqProducerTemplate().send(
                            message.getTopic(), message.getTag(), message.getMessageId(),
                            message.getPayload().getBytes());
                    if (sendResult != null) {
                        getLocalMessageService().markSent(message.getMessageId());
                        ctx().success();
                        continue;
                    }
                } catch (Exception ignored) {
                }
                int nextRetry = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
                getLocalMessageService().recordDeadLetterFailure(
                        message.getMessageId(), nextRetry, "dead-letter retry failed, retryCount=" + nextRetry);
                ctx().fail("dead-letter retry failed");
            }
        }

        private LocalMessageService getLocalMessageService() {
            return this.localMessageServiceReflection();
        }

        private RocketMqProducerTemplate getRocketMqProducerTemplate() {
            return this.rocketMqProducerTemplateReflection();
        }

        private LocalMessageService localMessageServiceReflection() {
            try {
                var field = LocalMessageDeadLetterJobHandler.class.getDeclaredField("localMessageService");
                field.setAccessible(true);
                return (LocalMessageService) field.get(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private RocketMqProducerTemplate rocketMqProducerTemplateReflection() {
            try {
                var field = LocalMessageDeadLetterJobHandler.class.getDeclaredField("rocketMqProducerTemplate");
                field.setAccessible(true);
                return (RocketMqProducerTemplate) field.get(this);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        LocalDateTime now() {
            return fixedNow;
        }
    }
}
