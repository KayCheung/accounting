package com.kltb.accounting.core.infrastructure.messaging;

import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.domain.service.RollbackDomainService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RocketMQ 消费端：异步过账消息监听器
 * <p>
 * 职责：接收异步过账消息，委托 AsyncPostingDomainService 执行。
 * 继承 AbstractMqConsumer，具备内置消费幂等校验（t_message_receipt）与消费回执追踪能力。
 * 不可重试异常或重试超限直接调用单边回滚，结束重试并提交消息；
 * 可重试异常向上抛出由基类捕获记录失败回执，并触发 RocketMQ 重试（RECONSUME_LATER）。
 * <p>
 * 是否记账：是（消费端执行余额变更）
 * 事务处理：每次消费通过 TransactionTemplate 开启独立事务
 */
@Slf4j
@Component
public class PostingMessageConsumer extends AbstractMqConsumer<PostingMessagePayload> {

    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;
    private final TransactionTemplate transactionTemplate;

    @Value("${accounting.messaging.rocketmq.consumer.posting-group:GID_ACCOUNTING_POSTING}")
    private String consumerGroup;

    private static final int MAX_RETRY = 3;

    public PostingMessageConsumer(MessageReceiptRepository receiptRepository,
                                  AsyncPostingDomainService asyncPostingDomainService,
                                  RollbackDomainService rollbackDomainService,
                                  TransactionTemplate transactionTemplate) {
        super(receiptRepository);
        this.asyncPostingDomainService = asyncPostingDomainService;
        this.rollbackDomainService = rollbackDomainService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    protected String getConsumerGroup() {
        return consumerGroup != null ? consumerGroup : "GID_ACCOUNTING_POSTING";
    }

    @Override
    protected void doConsume(PostingMessagePayload payload, MessageExt message) throws Exception {
        int retryCount = message.getReconsumeTimes();
        if (retryCount >= MAX_RETRY) {
            log.error("[POSTING-CONSUMER] 重试超限，执行单边回滚: entryId={} reconsumeTimes={}",
                payload.getEntryId(), retryCount);
            try {
                asyncPostingDomainService.executeRollbackOnAsyncFailure(
                    payload.getVoucherNo(), payload.getEntryId(), "重试超过最大次数 " + MAX_RETRY);
            } catch (Exception e) {
                log.error("[POSTING-CONSUMER] 单边回滚异常: entryId={}", payload.getEntryId(), e);
            }
            // 单边回滚已完成，无需再次重试，直接正常返回（由基类写入成功回执并提交消息）
            return;
        }

        try {
            transactionTemplate.execute(status -> {
                asyncPostingDomainService.consumeAsyncPostingMessage(payload);
                return null;
            });
        } catch (Exception e) {
            if (!rollbackDomainService.isRetryable(e)) {
                log.error("[POSTING-CONSUMER] 不可重试异常，执行单边回滚: entryId={}", payload.getEntryId(), e);
                asyncPostingDomainService.executeRollbackOnAsyncFailure(
                    payload.getVoucherNo(), payload.getEntryId(), e.getMessage());
                // 不可重试异常已单边回滚，无需 RocketMQ 重试，直接正常返回
                return;
            }
            log.warn("[POSTING-CONSUMER] 消费失败，等待重试: attempt={}/{} entryId={}",
                retryCount + 1, MAX_RETRY, payload.getEntryId());
            // 可重试异常：抛出给基类记录 status=2 失败回执并返回 RECONSUME_LATER 触发重试
            throw e;
        }
    }
}
