package com.kltb.accounting.job.job;

import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 期末结转独立定时调度任务（XXL-JOB）
 * <p>
 * 调度配置：
 * <ul>
 *   <li>日结调度：每日日终（如 23:30）执行</li>
 *   <li>月末调度：每月最后一日（如 23:45）执行</li>
 * </ul>
 * 路由策略：FIRST（单实例执行）
 * 任务参数：可选传入会计日期（格式 yyyy-MM-dd），不传默认系统前一日/当前日。
 * 业务逻辑：仅扫描并自动执行支持自动结转（autoTransfer=true）且当前会计日期符合周期定义（每日/月末/季末/年末）的有效规则。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeriodEndTransferJobHandler extends AbstractXxlJobHandler {

    private final PeriodEndTransferDomainService transferDomainService;

    @Override
    protected String jobName() {
        return "PERIOD-END-TRANSFER-JOB";
    }

    @XxlJob("periodEndTransferJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now().minusDays(1)));

        logStart("date=" + ctx().accountingDate);

        try {
            LocalDate date = ctx().accountingDate;
            List<TransferRuleResult> results = transferDomainService.executeAutoTransfer(date);


            ctx().totalCount(results.size());

            for (TransferRuleResult res : results) {
                if (res.getStatus() == TransferRecordStatusEnum.SUCCESS) {
                    ctx().success();
                } else {
                    ctx().fail(res.getRuleCode() + ": " + res.getFailReason());
                }
            }

            log.info("[{}] 自动结转完成: date={}, total={}, success={}, failed={}",
                    jobName(), ctx().totalCount, ctx().successCount, ctx().failedCount);

            if (ctx().failedCount > 0) {
                markFailed("期末结转部分规则执行失败，失败笔数: " + ctx().failedCount);
            } else {
                markSuccess();
            }

            logComplete("total=" + ctx().totalCount + ", success=" + ctx().successCount
                    + ", failed=" + ctx().failedCount);
            logFailedDetails();

        } catch (Exception e) {
            log.error("[{}] 自动结转异常: date={}, error={}",
                    jobName(), ctx().accountingDate, e.getMessage(), e);
            markFailed("期末结转系统异常: " + e.getMessage());
        }
    }
}
