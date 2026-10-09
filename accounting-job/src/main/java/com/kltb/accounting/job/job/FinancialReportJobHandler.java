package com.kltb.accounting.job.job;

import com.kltb.accounting.core.application.service.FinancialReportApplicationService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 财务报表定期生成与预热调度任务（XXL-JOB）
 * <p>
 * 调度配置：
 * <ul>
 *   <li>日终调度：每日日切后或凌晨（如 01:00）定期预热生成全套财务报表</li>
 *   <li>月末调度：每月最后一日结转完成后预热生成资产负债表与利润表</li>
 * </ul>
 * 路由策略：FIRST（单节点执行）
 * 任务参数：可选传入会计日期（格式 yyyy-MM-dd），不传默认系统当前日期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinancialReportJobHandler extends AbstractXxlJobHandler {

    private final FinancialReportApplicationService reportApplicationService;

    @Override
    protected String jobName() {
        return "FINANCIAL-REPORT-JOB";
    }

    @XxlJob("financialReportJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now()));

        logStart("date=" + ctx().accountingDate);

        try {
            LocalDate date = ctx().accountingDate;
            ctx().totalCount(2); // 资产负债表 + 利润表 核心套表

            reportApplicationService.generateAndArchiveReports(date);

            ctx().success();
            ctx().success();

            log.info("[{}] 财务报表生成与预热完成: date={}", jobName(), date);
            markSuccess();
            logComplete("total=" + ctx().totalCount + ", success=" + ctx().successCount);
        } catch (Exception e) {
            log.error("[{}] 财务报表定期生成失败: date={}, err={}", jobName(), ctx().accountingDate, e.getMessage(), e);
            markFailed("财务报表生成异常: " + e.getMessage());
        }
    }
}
