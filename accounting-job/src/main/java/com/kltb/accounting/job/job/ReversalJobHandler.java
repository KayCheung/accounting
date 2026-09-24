package com.kltb.accounting.job.job;

import com.kltb.accounting.api.request.ReversalRequest;
import com.kltb.accounting.api.response.ReversalResponse;
import com.kltb.accounting.core.application.service.ReversalApplicationService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量红冲 Job 处理器（Step 18 可选）
 * <p>
 * 场景：批量冲销某日期范围内指定业务线的凭证
 * <p>
 * 参数格式：origVoucherNo1,origVoucherNo2,...  （逗号分隔凭证号列表）
 * <p>
 * 执行策略：逐条执行红冲，单条失败不中断，汇总结果
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReversalJobHandler extends AbstractXxlJobHandler {

    private final ReversalApplicationService reversalApplicationService;

    @Override
    protected String jobName() {
        return "REVERSAL-JOB";
    }

    @XxlJob("reversalJob")
    public void execute() {
        initContext();

        String param = ctx().param;
        if (param == null || param.trim().isEmpty()) {
            XxlJobHelper.log("[{}] 参数为空，跳过执行", jobName());
            markFailed("参数为空");
            return;
        }

        log.info("[{}] 开始执行批量红冲: params={}", jobName(), param);

        // 过滤空字符串后再处理，确保 total 计数准确
        List<String> voucherNos = new ArrayList<>();
        for (String part : param.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                voucherNos.add(trimmed);
            }
        }

        if (voucherNos.isEmpty()) {
            XxlJobHelper.log("[{}] 参数中无有效凭证号", jobName());
            markFailed("无有效凭证号");
            return;
        }

        List<ReversalJobResult> results = new ArrayList<>();

        for (String voucherNo : voucherNos) {
            try {
                ReversalRequest request = new ReversalRequest();
                request.setOrigVoucherNo(voucherNo);
                request.setSummary("批量红冲:" + voucherNo);

                ReversalResponse response = reversalApplicationService.executeReversal(request);

                results.add(new ReversalJobResult(voucherNo, true, response.getReversalVoucherNo(), null));
                ctx().success();
                XxlJobHelper.log("[{}] 红冲成功 origVoucherNo={} reversalVoucherNo={}",
                        jobName(), voucherNo, response.getReversalVoucherNo());

            } catch (Exception e) {
                ctx().fail(voucherNo + "(" + e.getMessage() + ")");
                log.error("[{}] 红冲失败 origVoucherNo={} reason={}", jobName(), voucherNo, e.getMessage());
                XxlJobHelper.log("[{}] 红冲失败 origVoucherNo={} reason={}",
                        jobName(), voucherNo, e.getMessage());
            }
        }

        log.info("[{}] 批量红冲完成 total={} success={} fail={}",
                jobName(), voucherNos.size(), ctx().successCount, ctx().failedCount);

        if (ctx().failedCount > 0) {
            markFailed(String.format("批量红冲完成: total=%d, success=%d, fail=%d",
                    voucherNos.size(), ctx().successCount, ctx().failedCount));
        } else {
            XxlJobHelper.log("[{}] 全部红冲成功 total={}", jobName(), ctx().successCount);
            markSuccess();
        }
    }

    /**
     * 单条红冲结果
     */
    @lombok.Data
    private static class ReversalJobResult {
        private final String origVoucherNo;
        private final boolean success;
        private final String reversalVoucherNo;
        private final String errorMessage;

        ReversalJobResult(String origVoucherNo, boolean success, String reversalVoucherNo, String errorMessage) {
            this.origVoucherNo = origVoucherNo;
            this.success = success;
            this.reversalVoucherNo = reversalVoucherNo;
            this.errorMessage = errorMessage;
        }
    }
}
