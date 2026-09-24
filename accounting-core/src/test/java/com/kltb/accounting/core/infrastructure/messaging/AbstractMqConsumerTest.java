// accounting-core/src/test/java/com/kltb/accounting/core/infrastructure/messaging/AbstractMqConsumerTest.java
package com.kltb.accounting.core.infrastructure.messaging;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AbstractMqConsumer 单元测试
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>相同 message_id 重复消费 → 幂等跳过，返回 CommitMessage</li>
 *   <li>首次消费成功 → 写入 status=1 回执，返回 CommitMessage</li>
 *   <li>消费失败（业务异常）→ 写入 status=2 回执（含 errorCode），返回 ReconsumeLater</li>
 *   <li>消费失败（非业务异常）→ 写入 status=2 回执（errorCode 为类名），返回 ReconsumeLater</li>
 *   <li>回执写入失败 → 不影响消费结果，仍返回正确 Action</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractMqConsumer 单元测试")
class AbstractMqConsumerTest {

    @Mock
    private MessageReceiptRepository receiptRepository;

    @Mock
    private ConsumeContext consumeContext;

    /** 被测消费者实例，通过匿名子类实例化 */
    private AbstractMqConsumer consumer;

    /** 控制 doConsume 行为的标志 */
    private boolean shouldThrow = false;
    private Exception exceptionToThrow;

    private static final String CONSUMER_GROUP = "GID_TEST_GROUP";
    private static final String MESSAGE_ID = "ons-msg-id-001";
    private static final String BUSINESS_KEY = "biz-key-001";
    private static final String TOPIC = "test-topic";

    @BeforeEach
    void setUp() {
        shouldThrow = false;
        exceptionToThrow = null;

        // 通过匿名子类实例化抽象类，consumer_group 来自构造参数，不硬编码
        consumer = new AbstractMqConsumer(receiptRepository) {
            @Override
            protected void doConsume(Message message) throws Exception {
                if (shouldThrow) {
                    throw exceptionToThrow;
                }
                // 正常消费，无操作
            }

            @Override
            protected String getConsumerGroup() {
                return CONSUMER_GROUP;
            }
        };
    }

    // ==================== 幂等跳过 ====================

    @Test
    @DisplayName("相同 message_id 重复消费 - 幂等跳过，返回 CommitMessage，不执行业务逻辑")
    void consume_duplicateMessageId_idempotentSkip_returnsCommitMessage() {
        // 模拟 t_message_receipt 中已存在该消息的消费记录
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(true);

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        // 验证：返回 CommitMessage（幂等跳过）
        assertThat(action).isEqualTo(Action.CommitMessage);
        // 验证：不写入新的回执记录
        verify(receiptRepository, never()).save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ==================== 消费成功 ====================

    @Test
    @DisplayName("首次消费成功 - 写入 status=1 回执，返回 CommitMessage")
    void consume_firstTime_success_savesReceiptStatus1_returnsCommitMessage() {
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(false);

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.CommitMessage);

        // 验证写入成功回执，status=1
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(receiptRepository).save(
                eq(MESSAGE_ID), eq(BUSINESS_KEY), eq(CONSUMER_GROUP), eq(TOPIC),
                statusCaptor.capture(),
                eq(""), eq(""),
                any(), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        assertThat(statusCaptor.getValue()).isEqualTo(1);
    }

    // ==================== 消费失败 - 业务异常 ====================

    @Test
    @DisplayName("消费失败（ServiceException）- 写入 status=2 回执，errorCode 为 ResultCode，返回 ReconsumeLater")
    void consume_serviceException_savesReceiptStatus2WithResultCode_returnsReconsumeLater() {
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(false);

        shouldThrow = true;
        exceptionToThrow = new ServiceException(ResultCode.INSUFFICIENT_BALANCE);

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.ReconsumeLater);

        // 验证写入失败回执，status=2，errorCode 为 ResultCode.INSUFFICIENT_BALANCE 的 code
        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(receiptRepository).save(
                eq(MESSAGE_ID), eq(BUSINESS_KEY), eq(CONSUMER_GROUP), eq(TOPIC),
                statusCaptor.capture(),
                errorCodeCaptor.capture(),
                any(), any(), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        assertThat(statusCaptor.getValue()).isEqualTo(2);
        assertThat(errorCodeCaptor.getValue()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE.getCode());
    }

    @Test
    @DisplayName("消费失败（非业务异常）- 写入 status=2 回执，errorCode 为异常类名，返回 ReconsumeLater")
    void consume_runtimeException_savesReceiptStatus2WithClassName_returnsReconsumeLater() {
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(false);

        shouldThrow = true;
        exceptionToThrow = new RuntimeException("数据库连接失败");

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.ReconsumeLater);

        ArgumentCaptor<Integer> statusCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<String> errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        verify(receiptRepository).save(
                eq(MESSAGE_ID), eq(BUSINESS_KEY), eq(CONSUMER_GROUP), eq(TOPIC),
                statusCaptor.capture(),
                errorCodeCaptor.capture(),
                any(), any(), any(LocalDateTime.class), any(LocalDateTime.class)
        );
        assertThat(statusCaptor.getValue()).isEqualTo(2);
        // 非业务异常，errorCode 为类名简称
        assertThat(errorCodeCaptor.getValue()).isEqualTo("RuntimeException");
    }

    // ==================== 回执写入失败容错 ====================

    @Test
    @DisplayName("消费成功但回执写入失败 - 不影响返回 CommitMessage")
    void consume_success_receiptSaveFails_stillReturnsCommitMessage() {
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(false);
        // 模拟回执写入抛异常
        doThrow(new RuntimeException("DB 写入失败"))
                .when(receiptRepository).save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        // 回执写入失败不影响消费结果
        assertThat(action).isEqualTo(Action.CommitMessage);
    }

    @Test
    @DisplayName("消费失败且回执写入也失败 - 仍返回 ReconsumeLater")
    void consume_failure_receiptSaveFails_stillReturnsReconsumeLater() {
        when(receiptRepository.existsByMessageIdAndConsumerGroup(MESSAGE_ID, CONSUMER_GROUP))
                .thenReturn(false);
        shouldThrow = true;
        exceptionToThrow = new RuntimeException("业务处理失败");
        doThrow(new RuntimeException("DB 写入失败"))
                .when(receiptRepository).save(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        Message message = buildMessage(MESSAGE_ID, BUSINESS_KEY, TOPIC);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.ReconsumeLater);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建测试用 ONS Message
     */
    private Message buildMessage(String msgId, String key, String topic) {
        Message message = new Message(topic, "tag", key, "test-body".getBytes(StandardCharsets.UTF_8));
        // ONS MsgID 通过反射或 mock 设置；此处使用 mock 绕过 ONS 内部构造
        Message spyMessage = spy(message);
        doReturn(msgId).when(spyMessage).getMsgID();
        return spyMessage;
    }
}
