package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.LocalMessagePageQueryRequest;
import com.kltb.accounting.api.request.LocalMessageReplayRequest;
import com.kltb.accounting.api.request.LocalMessageResetRequest;
import com.kltb.accounting.api.response.LocalMessageBatchOpResponse;
import com.kltb.accounting.api.response.LocalMessageResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.domain.enums.MessageStatusEnum;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.RocketMqProducerTemplate;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import org.apache.rocketmq.client.producer.SendResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * LocalMessageApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LocalMessageApplicationServiceTest {

    @Mock
    private LocalMessageService localMessageService;

    @Mock
    private RocketMqProducerTemplate rocketMqProducerTemplate;

    @InjectMocks
    private LocalMessageApplicationService localMessageApplicationService;

    @Test
    @DisplayName("分页查询：正常转换为 PageResponse")
    void pageQuery_shouldMapSuccessfully() {
        LocalMessagePageQueryRequest request = new LocalMessagePageQueryRequest();
        request.setStatus(3);
        request.setPageNo(1);
        request.setPageSize(10);

        Page<LocalMessagePO> mockPage = new Page<>(1, 10);
        LocalMessagePO po = new LocalMessagePO()
                .setId(1L)
                .setMessageId("MSG-001")
                .setTopic("TEST-TOPIC")
                .setTag("TEST-TAG")
                .setBusinessKey("BIZ-001")
                .setPayload("{\"k\":\"v\"}")
                .setStatus(MessageStatusEnum.FAILED)
                .setRetryCount(3)
                .setMaxRetry(3);
        mockPage.setRecords(List.of(po));
        mockPage.setTotal(1L);

        when(localMessageService.pageQuery(request)).thenReturn(mockPage);

        PageResponse<LocalMessageResponse> result = localMessageApplicationService.pageQuery(request);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getList()).hasSize(1);
        assertThat(result.getList().get(0).getMessageId()).isEqualTo("MSG-001");
        assertThat(result.getList().get(0).getStatus()).isEqualTo(3);
        assertThat(result.getList().get(0).getStatusDesc()).isEqualTo("发送失败");
    }

    @Test
    @DisplayName("手动重放：发送成功后 markSent 并统计成功")
    void replay_sendSuccess_shouldMarkSent() {
        LocalMessageReplayRequest request = new LocalMessageReplayRequest();
        request.setMessageIds(List.of("MSG-001", "MSG-002"));

        LocalMessagePO po1 = new LocalMessagePO()
                .setId(1L)
                .setMessageId("MSG-001")
                .setTopic("TOPIC-1")
                .setTag("TAG-1")
                .setPayload("{\"k\":1}")
                .setStatus(MessageStatusEnum.FAILED)
                .setRetryCount(3);

        LocalMessagePO po2 = new LocalMessagePO()
                .setId(2L)
                .setMessageId("MSG-002")
                .setTopic("TOPIC-2")
                .setTag("TAG-2")
                .setPayload("{\"k\":2}")
                .setStatus(MessageStatusEnum.FAILED)
                .setRetryCount(3);

        when(localMessageService.selectByMessageId("MSG-001")).thenReturn(po1);
        when(localMessageService.selectByMessageId("MSG-002")).thenReturn(po2);

        when(rocketMqProducerTemplate.send(eq("TOPIC-1"), eq("TAG-1"), eq("MSG-001"), any()))
                .thenReturn(mock(SendResult.class));
        when(rocketMqProducerTemplate.send(eq("TOPIC-2"), eq("TAG-2"), eq("MSG-002"), any()))
                .thenReturn(null);

        LocalMessageBatchOpResponse response = localMessageApplicationService.replay(request);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(1);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getFailedMessageIds()).containsExactly("MSG-002");

        verify(localMessageService).markSent("MSG-001");
        verify(localMessageService).recordDeadLetterFailure(eq("MSG-002"), eq(4), any());
    }

    @Test
    @DisplayName("批量重置：按 messageIds 重置为待发送")
    void reset_byMessageIds_shouldInvokeResetToPending() {
        LocalMessageResetRequest request = new LocalMessageResetRequest();
        request.setMessageIds(List.of("MSG-001", "MSG-002"));

        when(localMessageService.resetToPending(request.getMessageIds())).thenReturn(2);

        LocalMessageBatchOpResponse response = localMessageApplicationService.reset(request);

        assertThat(response.getTotalCount()).isEqualTo(2);
        assertThat(response.getSuccessCount()).isEqualTo(2);
        verify(localMessageService).resetToPending(request.getMessageIds());
        verify(localMessageService, never()).resetFailedByTimeRange(any(), any());
    }

    @Test
    @DisplayName("批量重置：按时间范围重置为待发送")
    void reset_byTimeRange_shouldInvokeResetFailedByTimeRange() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 9, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 11, 9, 23, 59, 59);

        LocalMessageResetRequest request = new LocalMessageResetRequest();
        request.setStartTime(start);
        request.setEndTime(end);

        when(localMessageService.resetFailedByTimeRange(start, end)).thenReturn(5);

        LocalMessageBatchOpResponse response = localMessageApplicationService.reset(request);

        assertThat(response.getTotalCount()).isEqualTo(5);
        assertThat(response.getSuccessCount()).isEqualTo(5);
        verify(localMessageService).resetFailedByTimeRange(start, end);
        verify(localMessageService, never()).resetToPending(any());
    }
}
