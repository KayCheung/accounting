package com.kltb.accounting.core.infrastructure.messaging;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kltb.accounting.core.domain.enums.MessageStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.LocalMessagePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 本地消息表服务（Outbox Pattern）
 *
 * <p>职责：封装 t_local_message 的写入与查询能力，供业务事务内写入和补偿 Job 读取。
 *
 * <p>是否记账：否（中间件可靠投递支撑能力）。
 *
 * <p>事务处理：
 * <ul>
 *   <li>本类不创建新事务，不使用 @Transactional</li>
 *   <li>save() 必须由调用方在 TransactionTemplate 内调用，确保与业务数据同事务提交</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalMessageService {

    private static final int MAX_SCAN_LIMIT = 100;
    private static final int DEFAULT_MAX_RETRY = 3;
    private static final long CLAIM_PROTECT_SECONDS = 5L;

    private final LocalMessageMapper localMessageMapper;

    /**
     * 写入本地消息
     *
     * <p>是否记账：否。
     *
     * <p>异常处理：
     * <ul>
     *   <li>参数为空或关键字段为空：抛出 IllegalArgumentException，阻断调用方事务提交</li>
     * </ul>
     *
     * @param message 本地消息实体（必须包含 messageId/topic/businessKey）
     */
    public void save(LocalMessagePO message) {
        if (message == null) {
            throw new IllegalArgumentException("本地消息对象不能为空");
        }
        if (isBlank(message.getMessageId()) || isBlank(message.getTopic()) || isBlank(message.getBusinessKey())) {
            throw new IllegalArgumentException("messageId / topic / businessKey 不能为空");
        }

        // 防御性拷贝，防止调用方传入的对象被意外修改后泄漏回调用方
        LocalMessagePO po = new LocalMessagePO();
        po.setMessageId(message.getMessageId())
                .setTopic(message.getTopic())
                .setTag(message.getTag())
                .setBusinessKey(message.getBusinessKey())
                .setPayload(message.getPayload())
                .setStatus(message.getStatus() != null ? message.getStatus() : MessageStatusEnum.PENDING)
                .setRetryCount(message.getRetryCount() != null ? message.getRetryCount() : 0)
                .setMaxRetry((message.getMaxRetry() != null && message.getMaxRetry() > 0) ? message.getMaxRetry() : DEFAULT_MAX_RETRY)
                .setNextRetryTime(message.getNextRetryTime() != null ? message.getNextRetryTime() : LocalDateTime.now());
        localMessageMapper.insert(po);
    }

    /**
     * 标记消息已发送
     *
     * <p>是否记账：否。
     *
     * <p>异常处理：
     * <ul>
     *   <li>messageId 为空：抛出 IllegalArgumentException</li>
     * </ul>
     *
     * @param messageId 业务消息 ID
     */
    public void markSent(String messageId) {
        if (isBlank(messageId)) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        localMessageMapper.update(
                null,
                new LambdaUpdateWrapper<LocalMessagePO>()
                        .eq(LocalMessagePO::getMessageId, messageId)
                        .set(LocalMessagePO::getStatus, MessageStatusEnum.SENT)
                        .set(LocalMessagePO::getSendTime, now)
                        .set(LocalMessagePO::getUpdateTime, now)
        );
    }

    /**
     * 标记消息失败
     *
     * <p>是否记账：否。
     *
     * <p>异常处理：
     * <ul>
     *   <li>messageId 为空：抛出 IllegalArgumentException</li>
     * </ul>
     *
     * @param messageId 业务消息 ID
     * @param reason 失败原因（用于日志）
     */
    public void markFailed(String messageId, String reason) {
        if (isBlank(messageId)) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        log.error("[LOCAL-MSG] 标记消息失败 messageId={} reason={}", messageId, reason);
        localMessageMapper.update(
                null,
                new LambdaUpdateWrapper<LocalMessagePO>()
                        .eq(LocalMessagePO::getMessageId, messageId)
                        .set(LocalMessagePO::getStatus, MessageStatusEnum.FAILED)
                        .set(LocalMessagePO::getUpdateTime, LocalDateTime.now())
        );
    }

    /**
     * 查询待发送消息
     *
     * <p>是否记账：否。
     *
     * <p>异常处理：
     * <ul>
     *   <li>limit 非法值：自动归一化为 [1,100] 范围</li>
     * </ul>
     *
     * @param limit 扫描数量上限（最大 100）
     * @return 待发送消息列表
     */
    public List<LocalMessagePO> queryPending(int limit) {
        return queryPendingByShard(limit, 0, 1);
    }

    /**
     * 查询待发送的本地消息（Step 12 P0-6）
     * 复用 queryPending 逻辑，返回待发送消息列表。
     *
     * @param limit 扫描数量上限（最大 100）
     * @return 待发送消息列表
     */
    public List<LocalMessagePO> selectPendingMessages(int limit) {
        return queryPending(limit);
    }

    /**
     * 分片查询待发送消息（XXL-JOB 分片）
     *
     * @param limit 扫描数量上限（最大 100）
     * @param shardIndex 分片序号（从 0 开始）
     * @param shardTotal 分片总数
     * @return 当前分片待发送消息
     */
    public List<LocalMessagePO> queryPendingByShard(int limit, int shardIndex, int shardTotal) {
        if (shardTotal <= 0 || shardIndex < 0 || shardIndex >= shardTotal) {
            return Collections.emptyList();
        }
        int actualLimit = Math.min(Math.max(limit, 1), MAX_SCAN_LIMIT);
        LocalDateTime now = LocalDateTime.now();

        LambdaQueryWrapper<LocalMessagePO> wrapper = new LambdaQueryWrapper<LocalMessagePO>()
                .eq(LocalMessagePO::getStatus, MessageStatusEnum.PENDING)
                .le(LocalMessagePO::getNextRetryTime, now)
                .orderByAsc(LocalMessagePO::getId);

        if (shardTotal > 1) {
            wrapper.apply("MOD(id, {0}) = {1}", shardTotal, shardIndex);
        }
        // actualLimit 为内部 clamp 后的安全值（1~100），不存在 SQL 注入风险
        wrapper.last("LIMIT " + actualLimit);
        return localMessageMapper.selectList(wrapper);
    }

    /**
     * 抢占消息处理权（防并发重复处理）
     *
     * <p>通过 CAS 更新 next_retry_time 为短暂未来时间，防止同一消息被并发执行器重复处理。
     *
     * @param id 主键 ID
     * @param expectedRetryCount 期望的当前重试次数
     * @return true-抢占成功；false-已被其他执行器抢占
     */
    public boolean claimForSend(Long id, Integer expectedRetryCount) {
        if (id == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        int affected = localMessageMapper.update(
                null,
                new LambdaUpdateWrapper<LocalMessagePO>()
                        .eq(LocalMessagePO::getId, id)
                        .eq(LocalMessagePO::getStatus, MessageStatusEnum.PENDING)
                        .eq(Objects.nonNull(expectedRetryCount), LocalMessagePO::getRetryCount, expectedRetryCount)
                        .le(LocalMessagePO::getNextRetryTime, now)
                        .set(LocalMessagePO::getNextRetryTime, now.plusSeconds(CLAIM_PROTECT_SECONDS))
                        .set(LocalMessagePO::getUpdateTime, now)
        );
        return affected > 0;
    }

    /**
     * 发送失败后回写重试信息
     *
     * @param messageId 消息 ID
     * @param nextRetryCount 新重试次数
     * @param nextRetryTime 下次重试时间
     */
    public void scheduleRetry(String messageId, int nextRetryCount, LocalDateTime nextRetryTime) {
        if (isBlank(messageId) || nextRetryTime == null) {
            throw new IllegalArgumentException("messageId / nextRetryTime 不能为空");
        }
        localMessageMapper.update(
                null,
                new LambdaUpdateWrapper<LocalMessagePO>()
                        .eq(LocalMessagePO::getMessageId, messageId)
                        .set(LocalMessagePO::getStatus, MessageStatusEnum.PENDING)
                        .set(LocalMessagePO::getRetryCount, nextRetryCount)
                        .set(LocalMessagePO::getNextRetryTime, nextRetryTime)
                        .set(LocalMessagePO::getUpdateTime, LocalDateTime.now())
        );
    }

    /**
     * 更新消息重试信息（Step 12 P0-6）
     *
     * @param messageId 消息 ID
     * @param retryCount 新重试次数
     * @param nextRetryTime 下次重试时间
     * @param errorCode 错误原因
     */
    public void updateRetryInfo(String messageId, int retryCount, LocalDateTime nextRetryTime, String errorCode) {
        if (isBlank(messageId)) {
            throw new IllegalArgumentException("messageId 不能为空");
        }
        localMessageMapper.update(
                null,
                new LambdaUpdateWrapper<LocalMessagePO>()
                        .eq(LocalMessagePO::getMessageId, messageId)
                        .set(LocalMessagePO::getRetryCount, retryCount)
                        .set(LocalMessagePO::getNextRetryTime, nextRetryTime)
        );
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
