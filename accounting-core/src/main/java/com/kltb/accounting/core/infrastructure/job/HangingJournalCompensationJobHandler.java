package com.kltb.accounting.core.infrastructure.job;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import com.kltb.accounting.core.domain.service.AsyncPostingDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 掉单与挂起流水补偿巡检定时任务（XXL-JOB）
 * <p>
 * 调度配置：每 2~5 分钟执行一次
 * 路由策略：FIRST（单实例执行）
 * <p>
 * 执行逻辑：
 * 1. 查询状态为 PROCESSING 且创建时间超过阈值（默认 5 分钟）的流水记录，逐笔推进
 * 2. 查询状态为 POSTING 且超时的凭证记录，兜底触发终态对齐（如所有非缓冲分录均已过账）
 * 3. 单笔失败不中断，记录成功/失败统计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HangingJournalCompensationJobHandler extends AbstractXxlJobHandler {

    private final BusinessRecordRepository businessRecordRepository;
    private final JournalingApplicationService journalingApplicationService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AsyncPostingDomainService asyncPostingDomainService;

    private static final int DEFAULT_TIMEOUT_MINUTES = 5;
    private static final int BATCH_LIMIT = 50;

    @Override
    protected String jobName() {
        return "HANGING-JOURNAL-COMPENSATION-JOB";
    }

    @XxlJob("hangingJournalCompensationJob")
    public void execute() {
        initContext();
        logStart("");

        int timeoutMinutes = DEFAULT_TIMEOUT_MINUTES;
        String param = ctx().param;
        if (StrUtil.isNotBlank(param)) {
            try {
                timeoutMinutes = Integer.parseInt(param.trim());
            } catch (NumberFormatException e) {
                log.warn("[{}] 解析超时参数失败，使用默认值: param={}", jobName(), param);
            }
        }

        LocalDateTime beforeTime = LocalDateTime.now().minusMinutes(timeoutMinutes);

        // 阶段 1：扫描超时 PROCESSING 状态的流水记录
        List<BusinessRecordPO> hangingRecords = businessRecordRepository
                .selectTimeoutProcessingRecords(beforeTime, BATCH_LIMIT);

        if (!hangingRecords.isEmpty()) {
            ctx().totalCount(hangingRecords.size());
            log.info("[{}] 发现超时挂起流水: count={}, threshold={}分钟",
                    jobName(), hangingRecords.size(), timeoutMinutes);

            for (BusinessRecordPO record : hangingRecords) {
                try {
                    journalingApplicationService.reconcileHangingJournal(record.getTraceNo());
                    ctx().success();
                    log.info("[{}] 掉单流水补偿处理完成: traceNo={}", jobName(), record.getTraceNo());
                } catch (Exception e) {
                    ctx().fail(e.getMessage());
                    log.error("[{}] 掉单流水补偿处理异常: traceNo={}, error={}",
                            jobName(), record.getTraceNo(), e.getMessage(), e);
                }
            }
        }

        // 阶段 2：巡检超时处于 POSTING 状态的凭证记录，兜底触发终态对齐
        List<AccountingVoucherPO> hangingVouchers = accountingVoucherRepository
                .selectTimeoutPostingVouchers(beforeTime, BATCH_LIMIT);
        if (!hangingVouchers.isEmpty()) {
            log.info("[{}] 发现超时过账中凭证: count={}, threshold={}分钟",
                    jobName(), hangingVouchers.size(), timeoutMinutes);
            for (AccountingVoucherPO v : hangingVouchers) {
                try {
                    asyncPostingDomainService.syncVoucherStatusIfAllPosted(v.getVoucherNo());
                    log.info("[{}] 挂起凭证终态对齐完成: voucherNo={}", jobName(), v.getVoucherNo());
                } catch (Exception e) {
                    log.error("[{}] 挂起凭证终态对齐异常: voucherNo={}, error={}",
                            jobName(), v.getVoucherNo(), e.getMessage(), e);
                }
            }
        }

        logComplete("总数=" + ctx().totalCount + ", 成功=" + ctx().successCount + ", 失败=" + ctx().failedCount);
    }
}
