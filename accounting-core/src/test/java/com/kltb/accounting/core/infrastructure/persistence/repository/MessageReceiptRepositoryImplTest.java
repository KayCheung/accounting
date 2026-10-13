package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.core.domain.enums.ReceiptStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.MessageReceiptPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.MessageReceiptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageReceiptRepositoryImpl 单元测试")
class MessageReceiptRepositoryImplTest {

    @Mock
    private MessageReceiptMapper messageReceiptMapper;

    private MessageReceiptRepositoryImpl repository;

    private static final String MESSAGE_ID = "msg-001";
    private static final String CONSUMER_GROUP = "GID_TEST";
    private static final String BUSINESS_KEY = "biz-key-001";
    private static final String TOPIC = "topic-test";

    @BeforeEach
    void setUp() {
        repository = new MessageReceiptRepositoryImpl(messageReceiptMapper);
    }

    @Test
    @DisplayName("isSuccessConsumed - 存在 status=1 记录时返回 true")
    void isSuccessConsumed_existsSuccess_returnsTrue() {
        when(messageReceiptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        boolean result = repository.isSuccessConsumed(MESSAGE_ID, CONSUMER_GROUP);

        assertThat(result).isTrue();
        verify(messageReceiptMapper).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("isSuccessConsumed - 不存在记录时返回 false")
    void isSuccessConsumed_notFound_returnsFalse() {
        when(messageReceiptMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        boolean result = repository.isSuccessConsumed(MESSAGE_ID, CONSUMER_GROUP);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSuccessConsumed - 参数为空时返回 false 且不查库")
    void isSuccessConsumed_nullParam_returnsFalse() {
        assertThat(repository.isSuccessConsumed(null, CONSUMER_GROUP)).isFalse();
        assertThat(repository.isSuccessConsumed(MESSAGE_ID, null)).isFalse();
        verifyNoInteractions(messageReceiptMapper);
    }

    @Test
    @DisplayName("save - 首次消费记录不存在时执行 insert")
    void save_firstTime_insertsNewRecord() {
        when(messageReceiptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        LocalDateTime now = LocalDateTime.now();
        repository.save(MESSAGE_ID, BUSINESS_KEY, CONSUMER_GROUP, TOPIC,
                1, "", "", "{}", now, now);

        ArgumentCaptor<MessageReceiptPO> captor = ArgumentCaptor.forClass(MessageReceiptPO.class);
        verify(messageReceiptMapper).insert(captor.capture());
        MessageReceiptPO inserted = captor.getValue();

        assertThat(inserted.getMessageId()).isEqualTo(MESSAGE_ID);
        assertThat(inserted.getBusinessKey()).isEqualTo(BUSINESS_KEY);
        assertThat(inserted.getConsumerGroup()).isEqualTo(CONSUMER_GROUP);
        assertThat(inserted.getTopic()).isEqualTo(TOPIC);
        assertThat(inserted.getStatus()).isEqualTo(ReceiptStatusEnum.SUCCESS);
        assertThat(inserted.getPayload()).isEqualTo("{}");
    }

    @Test
    @DisplayName("save - 重试消费记录已存在时执行 updateById")
    void save_recordExists_updatesExistingRecord() {
        MessageReceiptPO existing = new MessageReceiptPO()
                .setId(100L)
                .setMessageId(MESSAGE_ID)
                .setConsumerGroup(CONSUMER_GROUP)
                .setStatus(ReceiptStatusEnum.FAILED)
                .setErrorCode("TIMEOUT")
                .setErrorMessage("连接超时");

        when(messageReceiptMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        LocalDateTime now = LocalDateTime.now();
        repository.save(MESSAGE_ID, BUSINESS_KEY, CONSUMER_GROUP, TOPIC,
                1, "", "", "{}", now, now);

        verify(messageReceiptMapper, never()).insert(any(MessageReceiptPO.class));
        verify(messageReceiptMapper).updateById(existing);
        assertThat(existing.getStatus()).isEqualTo(ReceiptStatusEnum.SUCCESS);
        assertThat(existing.getErrorCode()).isEqualTo("");
        assertThat(existing.getErrorMessage()).isEqualTo("");
    }

    @Test
    @DisplayName("save - 参数为空时不操作数据库")
    void save_nullParams_doesNothing() {
        repository.save(null, BUSINESS_KEY, CONSUMER_GROUP, TOPIC, 1, "", "", "{}", null, null);
        repository.save(MESSAGE_ID, BUSINESS_KEY, null, TOPIC, 1, "", "", "{}", null, null);

        verifyNoInteractions(messageReceiptMapper);
    }
}
