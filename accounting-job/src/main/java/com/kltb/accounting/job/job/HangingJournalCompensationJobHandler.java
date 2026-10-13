package com.kltb.accounting.job.job;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
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
 * 1. 查询状态为 PROCESSING 且创建时间超过阈值（默认 5 分钟）的流水记录
 * 2. 逐笔执行掉单自动对账与推进补偿（reconcileHangingJournal）
 * 3. 单笔失败不中断，记录成功/失败统计
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HangingJournalCompensationJobHandler extends AbstractXxlJobHandler {

    private final BusinessRecordRepository businessRecordRepository;
    private final JournalingApplicationService journalingApplicationService;

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
        List<BusinessRecordPO> hangingRecords = businessRecordRepository
                .selectTimeoutProcessingRecords(beforeTime, BATCH_LIMIT);

        if (hangingRecords.isEmpty()) {
            log.info("[{}] 无超时挂起流水记录 (阈值={}分钟)", jobName(), timeoutMinutes);
            return;
        }

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

        logComplete("总数=" + ctx().totalCount + ", 成功=" + ctx().successCount + ", 失败=" + ctx().failedCount);
    }
}
