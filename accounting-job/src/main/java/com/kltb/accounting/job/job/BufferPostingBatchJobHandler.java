package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 缓冲记账定时任务 — 日间批量扫描（buffer_mode=2）
 * <p>
 * 调度配置：每 30 分钟执行一次
 * 路由策略：分片广播（支持多实例水平扩展）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），不传则默认当日。
 *           分片参数由 XXL-JOB 自动注入 shardIndex/shardTotal。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BufferPostingBatchJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final long TOTAL_SHARD_RANGE = 128L;

    private final BufferPostingEngineDomainService bufferPostingEngineDomainService;

    @Override
    protected String jobName() {
        return "BUFFER-POSTING-BATCH-JOB";
    }

    @XxlJob("bufferPostingBatchJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate
                + ", shardIndex=" + ctx().shardIndex + ", shardTotal=" + ctx().shardTotal);

        if (ctx().shardTotal > 1) {
            // 分片模式
            long rangePerShard = Long.MAX_VALUE / TOTAL_SHARD_RANGE;
            long shardingStart = (long) ctx().shardIndex * (TOTAL_SHARD_RANGE / ctx().shardTotal) * rangePerShard;
            long shardingEnd = shardingStart + (TOTAL_SHARD_RANGE / ctx().shardTotal) * rangePerShard - 1;

            BatchPostingResult result = bufferPostingEngineDomainService.executeShardedPosting(
                    ctx().accountingDate, BufferModeEnum.DAILY_BATCH.getCode(),
                    shardingStart, shardingEnd, DEFAULT_BATCH_SIZE);

            logComplete("shard=" + ctx().shardIndex + ", total=" + result.getTotalCount()
                    + ", success=" + result.getSuccessCount() + ", failed=" + result.getFailedCount());
        } else {
            // 非分片模式：直接执行批量
            BatchPostingResult result = bufferPostingEngineDomainService.executeBatchPosting(
                    ctx().accountingDate, DEFAULT_BATCH_SIZE);

            logComplete("total=" + result.getTotalCount() + ", success=" + result.getSuccessCount()
                    + ", failed=" + result.getFailedCount());
        }
    }
}
