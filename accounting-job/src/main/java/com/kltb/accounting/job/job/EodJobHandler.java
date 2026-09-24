package com.kltb.accounting.job.job;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.core.application.service.EodApplicationService;
import com.kltb.accounting.core.domain.service.AccountingDateSwitchDomainService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 日切（EOD）定时任务
 * <p>
 * 调度配置：每日日终执行
 * 路由策略：FIRST（单实例执行）
 * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），不传则默认昨日。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EodJobHandler extends AbstractXxlJobHandler {

    private final EodApplicationService eodApplicationService;
    private final AccountingDateSwitchDomainService accountingDateSwitchDomainService;

    @Override
    protected String jobName() {
        return "EOD-JOB";
    }

    @XxlJob("eodJob")
    public void execute() {
        initContext().accountingDate(parseAccountingDate(ctx().param, LocalDate.now().minusDays(1)));

        logStart("date=" + ctx().accountingDate);

        try {
            EodExecuteRequest request = new EodExecuteRequest();
            request.setAccountingDate(ctx().accountingDate);
            request.setSkipPreCheck(false);
            request.setExecuteTransfer(true);

            // Step 0: 瞬间切日（将全局会计日期 T → T+1）
            // 注意：切日更新的是全局会计日期缓存，executeEod 处理的仍是 request 中的 T 日账务
            accountingDateSwitchDomainService.switchDate(null);

            EodExecuteResponse response = eodApplicationService.executeEod(request);

            log.info("[{}] 执行完成: preCheck={}, balanceCount={}, trialBalance={}, "
                            + "transferCount={}, snapshotCount={}, duration={}ms",
                    jobName(), response.isPreCheckPassed(), response.getBalanceCount(),
                    response.isTrialBalancePassed(),
                    response.getTransferResults() != null ? response.getTransferResults().size() : 0,
                    response.getSnapshotCount(), response.getTotalDurationMs());

            if (!response.isPreCheckPassed()) {
                log.error("[{}] 前置检查失败: {}", jobName(), response.getPreCheckDetails());
                markFailed("日切前置检查失败");
                return;
            }

            if (!response.isTrialBalancePassed()) {
                log.error("[{}] 试算平衡失败: {}", jobName(), response.getTrialBalanceDetails());
                markFailed("试算平衡失败");
                return;
            }

            markSuccess();

        } catch (Exception e) {
            log.error("[{}] 执行异常: {}", jobName(), e.getMessage(), e);
            markFailed("日切执行异常: " + e.getMessage());
        }
    }
}
