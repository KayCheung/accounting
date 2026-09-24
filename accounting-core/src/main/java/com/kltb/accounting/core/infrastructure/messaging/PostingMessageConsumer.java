package com.kltb.accounting.core.infrastructure.messaging;

import com.aliyun.openservices.ons.api.Action;
import com.aliyun.openservices.ons.api.ConsumeContext;
import com.aliyun.openservices.ons.api.Message;
import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.domain.service.RollbackDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MQ 消费端：异步过账消息监听器
 * <p>
 * 职责：接收异步过账消息，委托 AsyncPostingDomainService 执行
 * 不可重试异常直接调用单边回滚，触发告警。
 * 重试交由 ONS 框架控制（ReconsumeLater）。
 * <p>
 * 是否记账：是（消费端执行余额变更）
 * 事务处理：每次消费通过 TransactionTemplate 开启独立事务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostingMessageConsumer extends AbstractMessageListener<PostingMessagePayload> {

    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;
    private final TransactionTemplate transactionTemplate;

    private static final int MAX_RETRY = 3;

    @Override
    protected Action handleMessage(PostingMessagePayload payload, Message message, ConsumeContext context) {
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
            return Action.CommitMessage;
        }

        try {
            transactionTemplate.execute(status -> {
                asyncPostingDomainService.consumeAsyncPostingMessage(payload);
                return null;
            });
            return Action.CommitMessage;
        } catch (Exception e) {
            if (!rollbackDomainService.isRetryable(e)) {
                log.error("[POSTING-CONSUMER] 不可重试异常，执行单边回滚: entryId={}", payload.getEntryId(), e);
                asyncPostingDomainService.executeRollbackOnAsyncFailure(
                    payload.getVoucherNo(), payload.getEntryId(), e.getMessage());
                return Action.CommitMessage;
            }
            log.warn("[POSTING-CONSUMER] 消费失败，等待重试: attempt={}/{} entryId={}",
                retryCount + 1, MAX_RETRY, payload.getEntryId());
            return Action.ReconsumeLater;
        }
    }
}
