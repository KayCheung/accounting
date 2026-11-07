package com.kltb.accounting.core.infrastructure.messaging;

import com.kltb.accounting.core.shared.context.TenantContext;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
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
 * RocketMqProducerTemplate 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>同步发送成功</li>
 *   <li>同步发送失败 → 只打日志，不抛异常，返回 null</li>
 *   <li>异步发送成功</li>
 *   <li>异步发送失败（提交失败）→ 只打日志，不抛异常</li>
 *   <li>异步发送回调 onException → 只打日志，不抛异常</li>
 *   <li>延迟发送成功，deliverTimeMs 已正确设置</li>
 *   <li>延迟发送失败 → 只打日志，不抛异常，返回 null</li>
 *   <li>消息 UserProperty 包含 tenantId（来自 TenantContext）</li>
 *   <li>TenantContext 未设置时 tenantId 降级为 SYSTEM_TENANT(-1)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RocketMqProducerTemplate 单元测试")
class RocketMqProducerTemplateTest {

    @Mock
    private ObjectProvider<DefaultMQProducer> producerProvider;

    @Mock
    private DefaultMQProducer producer;

    private RocketMqProducerTemplate rocketMqProducerTemplate;

    private static final String TOPIC = "test-topic";
    private static final String TAG = "test-tag";
    private static final String BUSINESS_KEY = "msg-001";
    private static final byte[] BODY = "hello".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        rocketMqProducerTemplate = new RocketMqProducerTemplate(producerProvider);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 同步发送 ====================

    @Test
    @DisplayName("同步发送成功 - 返回 SendResult")
    void send_success_returnsSendResult() throws Exception {
        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMsgId()).thenReturn("rocketmq-msg-id-001");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        SendResult result = rocketMqProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        assertThat(result).isNotNull();
        assertThat(result.getMsgId()).isEqualTo("rocketmq-msg-id-001");
        verify(producer, times(1)).send(any(Message.class));
    }

    @Test
    @DisplayName("同步发送失败 - 只打日志，不抛异常，返回 null")
    void send_failure_doesNotThrow_returnsNull() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenThrow(new RuntimeException("RocketMQ 连接超时"));

        assertThatCode(() -> {
            SendResult result = rocketMqProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);
            assertThat(result).isNull();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("同步发送 - 消息 UserProperty 包含 tenantId（来自 TenantContext）")
    void send_messageContainsTenantIdFromTenantContext() throws Exception {
        TenantContext.set(123);

        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMsgId()).thenReturn("rocketmq-msg-id-002");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        rocketMqProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        verify(producer).send(captor.capture());
        Message sentMessage = captor.getValue();
        assertThat(sentMessage.getUserProperty("tenantId")).isEqualTo("123");
    }

    @Test
    @DisplayName("同步发送 - TenantContext 未设置时 tenantId 降级为 SYSTEM_TENANT(-1)")
    void send_noTenantContext_tenantIdFallsBackToSystemTenant() throws Exception {
        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMsgId()).thenReturn("rocketmq-msg-id-003");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        rocketMqProducerTemplate.send(TOPIC, TAG, BUSINESS_KEY, BODY);

        verify(producer).send(captor.capture());
        assertThat(captor.getValue().getUserProperty("tenantId")).isEqualTo("-1");
    }

    // ==================== 异步发送 ====================

    @Test
    @DisplayName("异步发送 - 提交成功，不抛异常")
    void sendAsync_success_doesNotThrow() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doNothing().when(producer).send(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> rocketMqProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();

        verify(producer, times(1)).send(any(Message.class), any(SendCallback.class));
    }

    @Test
    @DisplayName("异步发送 - 提交失败，只打日志，不抛异常")
    void sendAsync_submitFailure_doesNotThrow() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doThrow(new RuntimeException("RocketMQ 异步提交失败"))
                .when(producer).send(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> rocketMqProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("异步发送 - 回调 onException 触发，只打日志，不抛异常")
    void sendAsync_callbackOnException_doesNotThrow() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        doAnswer(invocation -> {
            SendCallback callback = invocation.getArgument(1);
            callback.onException(new MQClientException("broker 拒绝", null));
            return null;
        }).when(producer).send(any(Message.class), any(SendCallback.class));

        assertThatCode(() -> rocketMqProducerTemplate.sendAsync(TOPIC, TAG, BUSINESS_KEY, BODY))
                .doesNotThrowAnyException();
    }

    // ==================== 延迟发送 ====================

    @Test
    @DisplayName("延迟发送成功 - deliverTimeMs 已设置且大于当前时间")
    void sendDelay_success_startDeliverTimeIsSet() throws Exception {
        long delayMillis = 30_000L;
        long beforeSend = System.currentTimeMillis();

        SendResult mockResult = mock(SendResult.class);
        when(mockResult.getMsgId()).thenReturn("rocketmq-msg-id-delay-001");
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenReturn(mockResult);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        SendResult result = rocketMqProducerTemplate.sendDelay(TOPIC, TAG, BUSINESS_KEY, BODY, delayMillis);

        assertThat(result).isNotNull();
        verify(producer).send(captor.capture());
        long deliverTimeMs = captor.getValue().getDeliverTimeMs();
        assertThat(deliverTimeMs).isGreaterThanOrEqualTo(beforeSend + delayMillis);
        assertThat(deliverTimeMs).isLessThan(beforeSend + delayMillis + 1_000L);
    }

    @Test
    @DisplayName("延迟发送失败 - 只打日志，不抛异常，返回 null")
    void sendDelay_failure_doesNotThrow_returnsNull() throws Exception {
        when(producerProvider.getIfAvailable()).thenReturn(producer);
        when(producer.send(any(Message.class))).thenThrow(new RuntimeException("broker 不可用"));

        assertThatCode(() -> {
            SendResult result = rocketMqProducerTemplate.sendDelay(TOPIC, TAG, BUSINESS_KEY, BODY, 5_000L);
            assertThat(result).isNull();
        }).doesNotThrowAnyException();
    }
}
