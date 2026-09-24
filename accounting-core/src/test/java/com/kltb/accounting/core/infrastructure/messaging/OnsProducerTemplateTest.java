package com.kltb.accounting.core.infrastructure.messaging;

import com.aliyun.openservices.ons.api.Message;
import com.aliyun.openservices.ons.api.OnExceptionContext;
import com.aliyun.openservices.ons.api.SendCallback;
import com.aliyun.openservices.ons.api.SendResult;
import com.aliyun.openservices.ons.api.bean.ProducerBean;
import com.aliyun.openservices.ons.api.exception.ONSClientException;
import com.kltb.accounting.core.shared.context.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * OnsProducerTemplate 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>同步发送成功</li>
 *   <li>同步发送失败 → 只打日志，不抛异常，返回 null</li>
 *   <li>异步发送成功</li>
 *   <li>异步发送失败（提交失败）→ 只打日志，不抛异常</li>
 *   <li>异步发送回调 onException → 只打日志，不抛异常</li>
 *   <li>延迟发送成功，startDeliverTime 已正确设置</li>
 *   <li>延迟发送失败 → 只打日志，不抛异常，返回 null</li>
 *   <li>消息 UserProperties 包含 tenantId（来自 TenantContext）</li>
 *   <li>TenantContext 未设置时 tenantId 降级为 SYSTEM_TENANT(-1)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OnsProducerTemplate 单元测试")
class OnsProducerTemplateTest {

    @Mock
    private ObjectProvider<ProducerBean> producerProvider;

    @Mock
    private ProducerBean producer;

    private OnsProducerTemplate onsProducerTemplate;

    private static final String TOPIC = "test-topic";
    private static final String TAG = "test-tag";
    private static final String BUSINESS_KEY = "msg-001";
    private static final byte[] BODY = "hello".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        onsProducerTemplate = new OnsProducerTemplate(producerProvider);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 同步发送 ====================

    @Test
    @DisplayName("同步发送成功 - 返回 SendResult")
    void send_success_returnsSendResult() {
        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMessageId()).thenReturn("ons-msg-id-001");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        SendResult result = onsProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        assertThat(result).isNotNull();
        assertThat(result.getMessageId()).isEqualTo("ons-msg-id-001");
        verify(producer, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("同步发送失败 - 只打日志，不抛异常，返回 null")
    void send_failure_doesNotThrow_returnsNull() {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenThrow(new RuntimeException("ONS 连接超时"));

        assertThatCode(() -> {
            SendResult result = onsProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);
            assertThat(result).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("同步发送 - 消息 UserProperties 包含 tenantId（来自 TenantContext）")
    void send_messageContainsTenantIdFromTenantContext() {
        TenantContext.set(123);

        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMessageId()).thenReturn("ons-msg-id-002");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        onsProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        verify(producer).send(captor.capture());
        Message sentMessage = captor.getValue();
        assertThat(sentMessage.getUserProperties("tenantId")).isEqualTo("123");
    }

    @Test
    @DisplayName("同步发送 - TenantContext 未设置时 tenantId 降级为 SYSTEM_TENANT(-1)")
    void send_noTenantContext_tenantIdFallsBackToSystemTenant() {
        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMessageId()).thenReturn("ons-msg-id-003");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        onsProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        verify(producer).send(captor.capture());
        assertThat(captor.getValue().getUserProperties("tenantId")).isEqualTo("-1");
    }

    // ==================== 异步发送 ====================

    @Test
    @DisplayName("异步发送 - 提交成功，不抛异常")
    void sendAsync_success_doesNotThrow() {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doNothing().when(producer).sendAsync(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> onsProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();

        verify(producer, times(1)).sendAsync(any(Message.class), any(SendCallback.class));
    }

    @Test
    @DisplayName("异步发送 - 提交失败，只打日志，不抛异常")
    void sendAsync_submitFailure_doesNotThrow() {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doThrow(new RuntimeException("ONS 异步提交失败"))
                .when(producer).sendAsync(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> onsProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("异步发送 - 回调 onException 触发，只打日志，不抛异常")
    void sendAsync_callbackOnException_doesNotThrow() {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doAnswer(invocation -> {
            SendCallback callback = invocation.getArgument(1);
            OnExceptionContext ctx = mock(OnExceptionContext.class);
            when(ctx.getException()).thenReturn(new ONSClientException("broker 拒绝"));
            callback.onException(ctx);
            return null;
        }).when(producer).sendAsync(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> onsProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();
    }

    // ==================== 延迟发送 ====================

    @Test
    @DisplayName("延迟发送成功 - startDeliverTime 已设置且大于当前时间")
    void sendDelay_success_startDeliverTimeIsSet() {
        long delayMillis = 30_000L;
        long beforeSend = System.currentTimeMillis();

        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMessageId()).thenReturn("ons-msg-id-delay-001");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        SendResult result = onsProducerTemplate.sendDelay(TOPIC, TAG, BUSINESS_KEY, BODY, delayMillis);

        assertThat(result).isNotNull();
        verify(producer).send(captor.capture());
        long startDeliverTime = captor.getValue().getStartDeliverTime();
        assertThat(startDeliverTime).isGreaterThanOrEqualTo(beforeSend + delayMillis);
        assertThat(startDeliverTime).isLessThan(beforeSend + delayMillis + 1_000L);
    }

    @Test
    @DisplayName("延迟发送失败 - 只打日志，不抛异常，返回 null")
    void sendDelay_failure_doesNotThrow_returnsNull() {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenThrow(new RuntimeException("broker 不可用"));

        assertThatCode(() -> {
            SendResult result = onsProducerTemplate.sendDelay(TOPIC, TAG, BUSINESS_KEY, BODY, 5_000L);
            assertThat(result).isNull();
        }).doesNotThrowAnyException();
    }
}
