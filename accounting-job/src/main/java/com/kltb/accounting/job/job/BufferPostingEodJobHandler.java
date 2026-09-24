package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.kltb.accounting.core.domain.service.RunningBalanceValidator;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缓冲记账定时任务 — 日终批量扫描（buffer_mode=3）
 * <p>
 * 调度配置：每日 23:50 执行
 * 路由策略：FIRST（单实例执行）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），不传则默认当日。
 * <p>
 * 执行完毕后自动触发 Running Balance 校验，校验失败记录告警日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BufferPostingEodJobHandler extends AbstractXxlJobHandler {

    private static final int DEFAULT_BATCH_SIZE = 500;

    private final BufferPostingEngineDomainService bufferPostingEngineDomainService;
    private final RunningBalanceValidator runningBalanceValidator;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;

    @Override
    protected String jobName() {
        return "BUFFER-POSTING-EOD-JOB";
    }

    @XxlJob("bufferPostingEodJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate);

        // 1. 执行日终批量过账（mode=3，P0-1 修复）
        BatchPostingResult result = bufferPostingEngineDomainService.executeEodPosting(
                ctx().accountingDate, DEFAULT_BATCH_SIZE);

        long postingDuration = System.currentTimeMillis() - ctx().startTime;
        log.info("[{}] 批量过账完成: total={}, success={}, failed={}, duration={}ms",
                jobName(), result.getTotalCount(), result.getSuccessCount(), result.getFailedCount(), postingDuration);

        // 2. 获取当日已入账的 mode=3 缓冲明细涉及账户（P0-4 修复）
        Set<String> accountNos = getAffectedAccountNos(ctx().accountingDate);

        // 3. 触发 Running Balance 校验
        int alertCount = 0;
        for (String accountNo : accountNos) {
            RunningBalanceValidator.ValidationResult vr = runningBalanceValidator.validateRunningBalance(accountNo, ctx().accountingDate);
            if (vr.isAlert()) {
                alertCount++;
            }
        }

        logComplete("total=" + result.getTotalCount() + ", success=" + result.getSuccessCount()
                + ", failed=" + result.getFailedCount() + ", runningBalanceAlerts=" + alertCount);

        if (alertCount > 0) {
            log.error("[{}] Running Balance 校验告警: count={}", jobName(), alertCount);
        }
    }

    /**
     * 获取当日有已入账缓冲明细的账户列表（仅 mode=3，P0-4 修复）
     */
    private Set<String> getAffectedAccountNos(LocalDate targetDate) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                targetDate, BufferModeEnum.EOD_BATCH.getCode(),
                com.kltb.accounting.core.domain.enums.BufferStatusEnum.SUCCESS.getCode(), 10000);

        return details.stream()
                .map(BufferPostingDetailPO::getAccountNo)
                .collect(Collectors.toSet());
    }
}
