package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 缓冲记账定时任务 — 异步逐条扫描（buffer_mode=1）
 * <p>
 * 调度配置：每 1 分钟执行一次
 * 路由策略：FIRST（单实例执行）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），不传则默认当日。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BufferPostingAsyncJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final BufferPostingEngineDomainService bufferPostingEngineDomainService;

    @Override
    protected String jobName() {
        return "BUFFER-POSTING-ASYNC-JOB";
    }

    @XxlJob("bufferPostingAsyncJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate);

        BatchPostingResult result = bufferPostingEngineDomainService.executeSinglePosting(
                ctx().accountingDate, DEFAULT_BATCH_SIZE);

        ctx().totalCount(result.getTotalCount())
             .successCount(result.getSuccessCount())
             .failedCount(result.getFailedCount());

        logComplete("total=" + result.getTotalCount() + ", success=" + result.getSuccessCount()
                + ", failed=" + result.getFailedCount());

        if (result.getFailedCount() > 0) {
            log.warn("[{}] 部分失败: {}", jobName(),
                    result.getFailedList().stream()
                            .map(f -> f.getAccountNo() + "(" + f.getFailReason() + ")")
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
        }
    }
}
