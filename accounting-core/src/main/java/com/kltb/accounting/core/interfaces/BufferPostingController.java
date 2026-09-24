package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.BufferExecuteRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.service.BufferPostingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 缓冲记账 Controller
 * <p>
 * 路径前缀：/accounting/buffer
 */
@RestController
@RequestMapping("/accounting/buffer")
@RequiredArgsConstructor
@Validated
@Tag(name = "缓冲记账", description = "手动触发缓冲记账、待入账统计、缓冲监控")
public class BufferPostingController {

    private final BufferPostingApplicationService bufferPostingApplicationService;

    /**
     * POST /accounting/buffer/execute — 手动触发缓冲记账
     */
    @PostMapping("/execute")
    @Operation(summary = "手动触发缓冲记账", description = "按会计日期和缓冲模式手动触发缓冲记账，单笔失败不中断")
    public ApiResponse<BufferExecuteResponse> executePosting(@Valid @RequestBody BufferExecuteRequest request) {
        return ApiResponse.ok(bufferPostingApplicationService.executePosting(request));
    }

    /**
     * GET /accounting/buffer/pending-stats — 查询待入账统计
     */
    @GetMapping("/pending-stats")
    @Operation(summary = "查询待入账统计", description = "查询指定会计日期的待入账缓冲明细统计信息（按模式分组）")
    public ApiResponse<BufferPendingStatsResponse> getPendingStats(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam(value = "accountingDate") @NotNull LocalDate accountingDate) {
        return ApiResponse.ok(bufferPostingApplicationService.getPendingStats(accountingDate));
    }

    /**
     * GET /accounting/buffer/monitor — 缓冲监控
     */
    @GetMapping("/monitor")
    @Operation(summary = "缓冲监控", description = "查询缓冲明细监控数据（按状态分组、失败账户 Top、Running Balance 告警）")
    public ApiResponse<BufferMonitorResponse> getMonitor(
            @Parameter(name = "accountingDate", description = "会计日期 yyyy-MM-dd")
            @RequestParam(value = "accountingDate", required = false) LocalDate accountingDate,
            @Parameter(name = "status", description = "状态：1-待入账, 2-处理中, 3-成功, 4-失败")
            @RequestParam(value = "status", required = false) Integer status) {
        if (accountingDate == null) {
            accountingDate = LocalDate.now();
        }
        return ApiResponse.ok(bufferPostingApplicationService.getMonitorData(accountingDate, status));
    }
}
