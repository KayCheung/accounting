package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.DateSwitchRequest;
import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.DateSwitchResponse;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.api.response.EodStatusResponse;
import com.kltb.accounting.api.response.TrialBalanceResponse;
import com.kltb.accounting.core.application.service.EodApplicationService;
import com.kltb.accounting.core.domain.service.AccountingDateSwitchDomainService;
import com.kltb.accounting.core.domain.service.AccountingDateSwitchDomainService.DateSwitchResult;
import com.kltb.accounting.core.domain.service.EodStatusDomainService;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/accounting/eod")
@RequiredArgsConstructor
@Validated
@Tag(name = "日切管理", description = "日切与试算平衡接口")
public class EodController {

    private final EodApplicationService eodApplicationService;
    private final EodStatusDomainService eodStatusDomainService;
    private final AccountingDateSwitchDomainService accountingDateSwitchDomainService;
    private final AccountingDateCache accountingDateCache;

    @PostMapping("/execute")
    @Operation(summary = "手动触发日切", description = "执行日切前置检查、日余额计算、试算平衡、期末结转、快照生成")
    public ApiResponse<EodExecuteResponse> executeEod(@Valid @RequestBody EodExecuteRequest request) {
        EodExecuteResponse response = eodApplicationService.executeEod(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/precheck")
    @Operation(summary = "查询日切前置检查结果", description = "检查当日所有过账是否完成、无处理中事务、无未过账凭证")
    public ApiResponse<EodPreCheckResponse> getPreCheckResult(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate accountingDate) {
        return ApiResponse.ok(eodApplicationService.getPreCheckResult(accountingDate));
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "查询试算平衡结果", description = "按科目汇总当日已过账分录的借贷方发生额，验证借贷平衡")
    public ApiResponse<TrialBalanceResponse> getTrialBalance(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate accountingDate) {
        return ApiResponse.ok(eodApplicationService.getTrialBalance(accountingDate));
    }

    @GetMapping("/status")
    @Operation(summary = "查询日切状态", description = "查询指定会计日的日切执行状态、各阶段完成时间、失败原因等（选填，默认当前会计日）")
    public ApiResponse<EodStatusResponse> getEodStatus(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd（选填，默认当前会计日）")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate accountingDate) {
        LocalDate queryDate = accountingDate != null ? accountingDate : accountingDateCache.getCurrentDate();
        EodStatusPO po = eodStatusDomainService.findByDate(queryDate);
        if (po == null) {
            // 当日尚未发起日切时，返回默认未开始（正常营业）状态，避免报 404/500
            return ApiResponse.ok(EodStatusResponse.from(
                    queryDate.toString(),
                    1, // 1: 未开始
                    null,
                    null,
                    null,
                    null,
                    0L));
        }
        return ApiResponse.ok(EodStatusResponse.from(
                po.getAccountingDate().toString(),
                po.getEodStatus(),
                po.getSwitchDateTime(),
                po.getArchiveDateTime(),
                po.getFailedStage(),
                po.getFailReason(),
                po.getTotalDurationMs()));
    }

    @PostMapping("/switch-date")
    @Operation(summary = "手动触发切日", description = "将全局会计日期切换到下一天或指定目标日期")
    public ApiResponse<DateSwitchResponse> switchDate(@Valid @RequestBody DateSwitchRequest request) {
        DateSwitchResult result = accountingDateSwitchDomainService.switchDate(request.getTargetDate());
        DateSwitchResponse response = DateSwitchResponse.builder()
                .previousDate(result.getPreviousDate())
                .newDate(result.getNewDate())
                .alreadySwitched(result.isAlreadySwitched())
                .switchedAt(result.getSwitchedAt())
                .build();
        return ApiResponse.ok(response);
    }
}
