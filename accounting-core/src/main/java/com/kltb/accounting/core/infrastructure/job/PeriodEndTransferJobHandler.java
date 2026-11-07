package com.kltb.accounting.core.infrastructure.job;

import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
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
 *   <li>日终全自动调度：每日日终（如 23:30）执行</li>
 *   <li>失败重试次数：建议配置 3 次</li>
 * </ul>
 * 路由策略：FIRST（单实例执行）
 * 任务参数：可选传入会计日期（格式 yyyy-MM-dd），不传默认优先获取 AccountingDateCache 全局权威会计日期，兜底当前日期。
 * 业务逻辑：自动执行历史期末漏结/失败自动补偿（Catch-Up）与当期自动结转（日结/月末/季末/年末）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PeriodEndTransferJobHandler extends AbstractXxlJobHandler {

    private final PeriodEndTransferDomainService transferDomainService;
    private final AccountingDateCache accountingDateCache;

    @Override
    protected String jobName() {
        return "PERIOD-END-TRANSFER-JOB";
    }

    @XxlJob("periodEndTransferJob")
    public void execute() {
        LocalDate defaultDate = resolveDefaultAccountingDate();
        initContext().accountingDate(parseAccountingDate(ctx().param, defaultDate));

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

    /**
     * 解析默认会计日期：优先使用系统权威会计日期缓存，未配置或异常时回退到服务器系统日期
     */
    private LocalDate resolveDefaultAccountingDate() {
        try {
            if (accountingDateCache != null) {
                LocalDate currentDate = accountingDateCache.getCurrentDate();
                if (currentDate != null) {
                    return currentDate;
                }
            }
        } catch (Exception e) {
            log.warn("[{}] 从 AccountingDateCache 获取会计日期失败，回退系统当前日期: error={}", jobName(), e.getMessage());
        }
        return LocalDate.now();
    }
}
