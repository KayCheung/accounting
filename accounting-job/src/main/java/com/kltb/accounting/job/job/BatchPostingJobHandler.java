package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.service.PostingEngineDomainService;
import com.kltb.accounting.core.domain.service.PostingEngineDomainService.BatchPostingResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.stream.Collectors;

/**
 * 批量过账定时任务（XXL-JOB）
 * <p>
 * 调度配置：每 5 分钟执行一次
 * 路由策略：FIRST（单实例执行）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），
 *           不传则默认使用 LocalDate.now()。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchPostingJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_SIZE = 50;

    private final PostingEngineDomainService postingEngineDomainService;

    @Override
    protected String jobName() {
        return "BATCH-POSTING-JOB";
    }

    @XxlJob("batchPostingJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate);

        BatchPostingResult result = postingEngineDomainService.executeBatchPosting(
                ctx().accountingDate, ctx().accountingDate, null, DEFAULT_BATCH_SIZE);

        ctx().totalCount(result.getTotalCount())
             .successCount(result.getSuccessCount())
             .failedCount(result.getFailedCount());

        logComplete("total=" + result.getTotalCount() + ", success=" + result.getSuccessCount()
                + ", failed=" + result.getFailedCount());

        if (result.getFailedCount() > 0) {
            log.warn("[{}] 部分失败明细: {}", jobName(),
                    result.getFailedList().stream()
                            .map(f -> f.getVoucherNo() + "(" + f.getFailReason() + ")")
                            .collect(Collectors.joining(", ")));
        }
    }
}
