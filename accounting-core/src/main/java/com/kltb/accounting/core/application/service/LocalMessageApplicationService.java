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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 本地消息运维应用层服务
 *
 * <p>职责：负责本地消息（Outbox Pattern）的运维编排能力，包括失败死信消息的检索、手动/批量重试投递（Replay）与重置激活（Reset）。
 *
 * <p>是否记账：否（中间件可靠投递运维治理能力）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageApplicationService {

    private final LocalMessageService localMessageService;
    private final RocketMqProducerTemplate rocketMqProducerTemplate;

    /**
     * 分页查询本地消息
     *
     * <p>是否记账：否
     * <p>异常处理：
     * <ul>
     *   <li>参数为空或非法由内部归一化安全兜底</li>
     * </ul>
     *
     * @param request 分页查询请求
     * @return 分页消息列表
     */
    public PageResponse<LocalMessageResponse> pageQuery(LocalMessagePageQueryRequest request) {
        Page<LocalMessagePO> page = localMessageService.pageQuery(request);

        List<LocalMessageResponse> responseList = page.getRecords().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return PageResponse.<LocalMessageResponse>builder()
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .list(responseList)
                .build();
    }

    /**
     * 手动/批量重试投递本地消息（Replay）
     *
     * <p>是否记账：否
     * <p>异常处理：
     * <ul>
     *   <li>单笔消息重试失败不中断整体流程，汇总至失败列表</li>
     * </ul>
     *
     * @param request 重试重放请求
     * @return 批量操作统计结果
     */
    public LocalMessageBatchOpResponse replay(LocalMessageReplayRequest request) {
        if (request == null || request.getMessageIds() == null || request.getMessageIds().isEmpty()) {
            return LocalMessageBatchOpResponse.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .failedMessageIds(Collections.emptyList())
                    .build();
        }

        List<String> messageIds = request.getMessageIds();
        int total = messageIds.size();
        int successCount = 0;
        int failedCount = 0;
        List<String> failedMessageIds = new ArrayList<>();

        for (String messageId : messageIds) {
            boolean success = replaySingleMessage(messageId);
            if (success) {
                successCount++;
            } else {
                failedCount++;
                failedMessageIds.add(messageId);
            }
        }

        log.info("[LOCAL-MSG-OPS] 批量重放完成: total={}, success={}, failed={}",
                total, successCount, failedCount);

        return LocalMessageBatchOpResponse.builder()
                .totalCount(total)
                .successCount(successCount)
                .failedCount(failedCount)
                .failedMessageIds(failedMessageIds)
                .build();
    }

    /**
     * 重置失败消息为待发送（PENDING）
     *
     * <p>是否记账：否
     * <p>异常处理：
     * <ul>
     *   <li>无异常抛出，返回重置条数统计</li>
     * </ul>
     *
     * @param request 重置请求
     * @return 批量操作统计结果
     */
    public LocalMessageBatchOpResponse reset(LocalMessageResetRequest request) {
        if (request == null) {
            return LocalMessageBatchOpResponse.builder()
                    .totalCount(0)
                    .successCount(0)
                    .failedCount(0)
                    .failedMessageIds(Collections.emptyList())
                    .build();
        }

        int affected;
        if (request.getMessageIds() != null && !request.getMessageIds().isEmpty()) {
            affected = localMessageService.resetToPending(request.getMessageIds());
            log.info("[LOCAL-MSG-OPS] 按 messageIds 批量重置为待发送: count={}, ids={}",
                    affected, request.getMessageIds());
        } else {
            affected = localMessageService.resetFailedByTimeRange(request.getStartTime(), request.getEndTime());
            log.info("[LOCAL-MSG-OPS] 按时间范围批量重置为待发送: count={}, range=[{} ~ {}]",
                    affected, request.getStartTime(), request.getEndTime());
        }

        return LocalMessageBatchOpResponse.builder()
                .totalCount(affected)
                .successCount(affected)
                .failedCount(0)
                .failedMessageIds(Collections.emptyList())
                .build();
    }

    private boolean replaySingleMessage(String messageId) {
        if (messageId == null || messageId.trim().isEmpty()) {
            return false;
        }

        LocalMessagePO po = localMessageService.selectByMessageId(messageId);
        if (po == null) {
            log.warn("[LOCAL-MSG-OPS] 待重放消息不存在: messageId={}", messageId);
            return false;
        }

        // 若当前已经为已发送状态，无需重复投递
        if (MessageStatusEnum.SENT.equals(po.getStatus())) {
            log.info("[LOCAL-MSG-OPS] 消息已处于 SENT 状态，直接视为成功: messageId={}", messageId);
            return true;
        }

        try {
            byte[] body = po.getPayload() != null ? po.getPayload().getBytes(StandardCharsets.UTF_8) : new byte[0];
            SendResult sendResult = rocketMqProducerTemplate.send(
                    po.getTopic(),
                    po.getTag(),
                    po.getMessageId(),
                    body
            );
            if (sendResult != null) {
                localMessageService.markSent(po.getMessageId());
                log.info("[LOCAL-MSG-OPS] 手动重放投递成功: messageId={}, topic={}",
                        po.getMessageId(), po.getTopic());
                return true;
            }
        } catch (Exception e) {
            log.error("[LOCAL-MSG-OPS] 手动重放投递异常: messageId={}, error={}",
                    po.getMessageId(), e.getMessage(), e);
        }

        int nextRetry = (po.getRetryCount() == null ? 0 : po.getRetryCount()) + 1;
        localMessageService.recordDeadLetterFailure(po.getMessageId(), nextRetry, "manual replay failed");
        return false;
    }

    private LocalMessageResponse toResponse(LocalMessagePO po) {
        if (po == null) {
            return null;
        }
        Integer statusCode = po.getStatus() != null ? po.getStatus().getCode() : null;
        String statusDesc = po.getStatus() != null ? po.getStatus().getDesc() : null;

        return LocalMessageResponse.builder()
                .id(po.getId())
                .messageId(po.getMessageId())
                .topic(po.getTopic())
                .tag(po.getTag())
                .businessKey(po.getBusinessKey())
                .payload(po.getPayload())
                .status(statusCode)
                .statusDesc(statusDesc)
                .retryCount(po.getRetryCount())
                .maxRetry(po.getMaxRetry())
                .nextRetryTime(po.getNextRetryTime())
                .sendTime(po.getSendTime())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
