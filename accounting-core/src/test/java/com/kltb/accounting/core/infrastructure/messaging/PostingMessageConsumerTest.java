package com.kltb.accounting.core.infrastructure.messaging;

import com.alibaba.fastjson2.JSON;
import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.domain.service.RollbackDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostingMessageConsumer 单元测试")
class PostingMessageConsumerTest {

    @Mock
    private MessageReceiptRepository receiptRepository;

    @Mock
    private AsyncPostingDomainService asyncPostingDomainService;

    @Mock
    private RollbackDomainService rollbackDomainService;

    @Mock
    private ConsumeContext consumeContext;

    private TransactionTemplate transactionTemplate;
    private PostingMessageConsumer consumer;

    private static final String MESSAGE_ID = "ons-posting-msg-001";
    private static final String VOUCHER_NO = "V202610130001";
    private static final String ENTRY_ID = "E-001";

    @BeforeEach
    void setUp() {
        // 创建直接同步执行回调的 TransactionTemplate
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        transactionTemplate = new TransactionTemplate(transactionManager) {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                TransactionStatus status = new SimpleTransactionStatus();
                return action.doInTransaction(status);
            }
        };

        consumer = new PostingMessageConsumer(
                receiptRepository,
                asyncPostingDomainService,
                rollbackDomainService,
                transactionTemplate
        );
    }

    private Message buildPostingMessage(int reconsumeTimes) {
        PostingMessagePayload payload = new PostingMessagePayload();
        payload.setVoucherNo(VOUCHER_NO);
        payload.setEntryId(ENTRY_ID);

        String json = JSON.toJSONString(payload);
        Message message = new Message("TOPIC_POSTING", "TAG_POSTING", VOUCHER_NO, json.getBytes(StandardCharsets.UTF_8));
        Message spyMessage = spy(message);
        doReturn(MESSAGE_ID).when(spyMessage).getMsgID();
        doReturn(reconsumeTimes).when(spyMessage).getReconsumeTimes();
        return spyMessage;
    }

    @Test
    @DisplayName("正常消费 - 成功过账，返回 CommitMessage")
    void consume_success() {
        when(receiptRepository.isSuccessConsumed(anyString(), anyString())).thenReturn(false);

        Message message = buildPostingMessage(0);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.CommitMessage);
        verify(asyncPostingDomainService).consumeAsyncPostingMessage(argThat(p ->
                VOUCHER_NO.equals(p.getVoucherNo()) && ENTRY_ID.equals(p.getEntryId())));
        verify(receiptRepository).save(eq(MESSAGE_ID), eq(VOUCHER_NO), anyString(), anyString(),
                eq(1), eq(""), eq(""), anyString(), any(), any());
    }

    @Test
    @DisplayName("重试超限 - 执行单边回滚，返回 CommitMessage")
    void consume_retryLimitExceeded_executesRollback_returnsCommitMessage() {
        when(receiptRepository.isSuccessConsumed(anyString(), anyString())).thenReturn(false);

        Message message = buildPostingMessage(3);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.CommitMessage);
        verify(asyncPostingDomainService).executeRollbackOnAsyncFailure(eq(VOUCHER_NO), eq(ENTRY_ID), contains("重试超过最大次数"));
        verify(asyncPostingDomainService, never()).consumeAsyncPostingMessage(any());
    }

    @Test
    @DisplayName("不可重试异常 - 执行单边回滚，返回 CommitMessage")
    void consume_nonRetryableException_executesRollback_returnsCommitMessage() {
        when(receiptRepository.isSuccessConsumed(anyString(), anyString())).thenReturn(false);

        RuntimeException nonRetryableEx = new RuntimeException("非法账户状态");
        doThrow(nonRetryableEx).when(asyncPostingDomainService).consumeAsyncPostingMessage(any());
        when(rollbackDomainService.isRetryable(nonRetryableEx)).thenReturn(false);

        Message message = buildPostingMessage(1);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.CommitMessage);
        verify(asyncPostingDomainService).executeRollbackOnAsyncFailure(eq(VOUCHER_NO), eq(ENTRY_ID), eq("非法账户状态"));
    }

    @Test
    @DisplayName("可重试异常 - 抛出并返回 ReconsumeLater")
    void consume_retryableException_returnsReconsumeLater() {
        when(receiptRepository.isSuccessConsumed(anyString(), anyString())).thenReturn(false);

        RuntimeException retryableEx = new RuntimeException("乐观锁冲突");
        doThrow(retryableEx).when(asyncPostingDomainService).consumeAsyncPostingMessage(any());
        when(rollbackDomainService.isRetryable(retryableEx)).thenReturn(true);

        Message message = buildPostingMessage(1);
        Action action = consumer.consume(message, consumeContext);

        assertThat(action).isEqualTo(Action.ReconsumeLater);
        verify(asyncPostingDomainService, never()).executeRollbackOnAsyncFailure(any(), any(), any());
        verify(receiptRepository).save(eq(MESSAGE_ID), eq(VOUCHER_NO), anyString(), anyString(),
                eq(2), anyString(), eq("乐观锁冲突"), anyString(), any(), any());
    }
}
