package com.kltb.accounting.core.infrastructure.job;

import com.kltb.accounting.core.application.service.BufferPostingApplicationService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 缓冲记账失败自动补偿定时任务
 * <p>
 * 调度配置：建议每 5~10 分钟执行一次（低频巡检）
 * 路由策略：FIRST（单实例执行）
 * 功能描述：扫描处于 FAILED 状态的缓冲明细并尝试自动补偿重试，同时刷新 Prometheus 失败告警指标。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BufferPostingCompensationJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final BufferPostingApplicationService bufferPostingApplicationService;

    @Override
    protected String jobName() {
        return "BUFFER-POSTING-COMPENSATION-JOB";
    }

    @XxlJob("bufferPostingCompensationJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate);

        BatchPostingResult result = bufferPostingApplicationService.compensateFailedDetails(
                ctx().accountingDate, DEFAULT_BATCH_SIZE);

        ctx().totalCount(result.getTotalCount())
             .successCount(result.getSuccessCount())
             .failedCount(result.getFailedCount());

        logComplete("total=" + result.getTotalCount() + ", success=" + result.getSuccessCount()
                + ", failed=" + result.getFailedCount());

        if (result.getFailedCount() > 0) {
            log.warn("[{}] 自动补偿部分仍失败: {}", jobName(),
                    result.getFailedList().stream()
                            .map(f -> f.getAccountNo() + "(" + f.getFailReason() + ")")
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
        }
    }
}
