package com.kltb.accounting.job.job;

import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.OnsProducerTemplate;
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
 * LocalMessageRetryJob 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LocalMessageRetryJobTest {

    @Mock
    private LocalMessageService localMessageService;

    @Mock
    private OnsProducerTemplate onsProducerTemplate;

    @Test
    @DisplayName("第1次失败后 next_retry_time = NOW + 10s")
    void firstFailure_shouldBackoff10Seconds() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        LocalMessagePO message = buildMessage(1L, "msg-1", 0, 3);

        when(localMessageService.queryPendingByShard(100, 0, 1)).thenReturn(List.of(message));
        when(localMessageService.claimForSend(1L, 0)).thenReturn(true);
        when(onsProducerTemplate.send(any(), any(), any(), any())).thenReturn(null);

        TestableLocalMessageRetryJob job = new TestableLocalMessageRetryJob(
                localMessageService, onsProducerTemplate, fixedNow, 0, 1);

        job.execute();

        verify(localMessageService).scheduleRetry(
                eq("msg-1"), eq(1), eq(fixedNow.plusSeconds(10)));
        verify(localMessageService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("第2次失败后 next_retry_time = NOW + 30s")
    void secondFailure_shouldBackoff30Seconds() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        LocalMessagePO message = buildMessage(2L, "msg-2", 1, 3);

        when(localMessageService.queryPendingByShard(100, 0, 1)).thenReturn(List.of(message));
        when(localMessageService.claimForSend(2L, 1)).thenReturn(true);
        when(onsProducerTemplate.send(any(), any(), any(), any())).thenReturn(null);

        TestableLocalMessageRetryJob job = new TestableLocalMessageRetryJob(
                localMessageService, onsProducerTemplate, fixedNow, 0, 1);

        job.execute();

        verify(localMessageService).scheduleRetry(
                eq("msg-2"), eq(2), eq(fixedNow.plusSeconds(30)));
        verify(localMessageService, never()).markFailed(any(), any());
    }

    @Test
    @DisplayName("超过 max_retry 后标记失败")
    void exceedMaxRetry_shouldMarkFailed() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        LocalMessagePO message = buildMessage(3L, "msg-3", 3, 3);

        when(localMessageService.queryPendingByShard(100, 0, 1)).thenReturn(List.of(message));
        when(localMessageService.claimForSend(3L, 3)).thenReturn(true);
        when(onsProducerTemplate.send(any(), any(), any(), any())).thenReturn(null);

        TestableLocalMessageRetryJob job = new TestableLocalMessageRetryJob(
                localMessageService, onsProducerTemplate, fixedNow, 0, 1);

        job.execute();

        verify(localMessageService).markFailed(eq("msg-3"), contains("retry exhausted"));
        verify(localMessageService, never()).scheduleRetry(any(), anyInt(), any());
    }

    @Test
    @DisplayName("支持 XXL-JOB 分片参数")
    void shouldUseShardParamsFromContext() {
        LocalDateTime fixedNow = LocalDateTime.of(2026, 3, 20, 10, 0, 0);
        when(localMessageService.queryPendingByShard(100, 1, 4)).thenReturn(List.of());

        TestableLocalMessageRetryJob job = new TestableLocalMessageRetryJob(
                localMessageService, onsProducerTemplate, fixedNow, 1, 4);

        job.execute();

        verify(localMessageService).queryPendingByShard(100, 1, 4);
        verifyNoMoreInteractions(onsProducerTemplate);
    }

    private LocalMessagePO buildMessage(Long id, String messageId, Integer retryCount, Integer maxRetry) {
        return new LocalMessagePO()
                .setId(id)
                .setMessageId(messageId)
                .setTopic("test-topic")
                .setTag("test-tag")
                .setPayload("{\"hello\":\"world\"}")
                .setRetryCount(retryCount)
                .setMaxRetry(maxRetry);
    }

    private static class TestableLocalMessageRetryJob extends LocalMessageRetryJob {

        private final LocalDateTime fixedNow;

        private TestableLocalMessageRetryJob(LocalMessageService localMessageService,
                                             OnsProducerTemplate onsProducerTemplate,
                                             LocalDateTime fixedNow,
                                             int shardIndex,
                                             int shardTotal) {
            super(localMessageService, onsProducerTemplate);
            this.fixedNow = fixedNow;
        }

        @Override
        LocalDateTime now() {
            return fixedNow;
        }

    }
}
