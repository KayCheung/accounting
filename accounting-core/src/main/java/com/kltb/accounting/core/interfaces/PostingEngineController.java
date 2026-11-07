package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.BatchPostingRequest;
import com.kltb.accounting.api.request.PostingRetryRequest;
import com.kltb.accounting.api.request.PostingSkipRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.service.PostingEngineApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 过账引擎 Controller
 * <p>
 * 路径前缀：/accounting/posting-engine
 */
@RestController
@RequestMapping("/accounting/posting-engine")
@RequiredArgsConstructor
@Validated
@Tag(name = "过账引擎", description = "批量过账、进度监控、异常治理")
public class PostingEngineController {

    private final PostingEngineApplicationService postingEngineApplicationService;

    /**
     * POST /accounting/posting-engine/batch — 批量过账
     */
    @PostMapping("/batch")
    @Operation(summary = "批量过账", description = "按会计日期范围批量执行过账，单笔失败不中断")
    public ApiResponse<BatchPostingResponse> executeBatchPosting(@Valid @RequestBody BatchPostingRequest request) {
        return ApiResponse.ok(postingEngineApplicationService.executeBatchPosting(request));
    }

    /**
     * GET /accounting/posting-engine/monitor/voucher/{voucherNo} — 凭证过账进度
     */
    @GetMapping("/monitor/voucher/{voucherNo}")
    @Operation(summary = "凭证过账进度", description = "查询单个凭证的过账进度（实时/异步/缓冲分录分组统计）")
    @Parameter(name = "voucherNo", description = "凭证号")
    public ApiResponse<PostingMonitorResponse> getVoucherProgress(@PathVariable String voucherNo) {
        return ApiResponse.ok(postingEngineApplicationService.getVoucherProgress(voucherNo));
    }

    /**
     * GET /accounting/posting-engine/monitor/transaction/{txnNo} — 事务过账进度
     */
    @GetMapping("/monitor/transaction/{txnNo}")
    @Operation(summary = "事务过账进度", description = "查询事务关联凭证的过账进度汇总")
    @Parameter(name = "txnNo", description = "事务编号")
    public ApiResponse<PostingMonitorResponse> getTransactionProgress(@PathVariable String txnNo) {
        return ApiResponse.ok(postingEngineApplicationService.getTransactionProgress(txnNo));
    }

    /**
     * GET /accounting/posting-engine/monitor/stats — 过账统计报表
     */
    @GetMapping("/monitor/stats")
    @Operation(summary = "过账统计报表", description = "按日期/业务线/交易码/渠道维度统计过账数据")
    public ApiResponse<PostingStatsResponse> getPostingStats(
        @Parameter(name = "startDate", description = "统计日期起始（含）")
        @RequestParam(value = "startDate") @NotNull LocalDate startDate,
        @Parameter(name = "endDate", description = "统计日期截止（含）")
        @RequestParam(value = "endDate") @NotNull LocalDate endDate,
        @Parameter(name = "dimension", description = "统计维度：date-按日期 / business-按业务线 / trade-按交易码 / channel-按渠道")
        @RequestParam(value = "dimension", defaultValue = "date") String dimension) {
        return ApiResponse.ok(postingEngineApplicationService.getPostingStats(startDate, endDate, dimension));
    }

    /**
     * POST /accounting/posting-engine/abnormal/retry — 异常凭证重试
     */
    @PostMapping("/abnormal/retry")
    @Operation(summary = "异常凭证重试", description = "手动重试过账失败凭证（最多 5 次）")
    public ApiResponse<PostingRetryResponse> retryAbnormalVoucher(@Valid @RequestBody PostingRetryRequest request) {
        return ApiResponse.ok(postingEngineApplicationService.retryAbnormalVoucher(request));
    }

    /**
     * POST /accounting/posting-engine/abnormal/skip — 异常凭证跳过
     */
    @PostMapping("/abnormal/skip")
    @Operation(summary = "异常凭证跳过", description = "将凭证标记为已跳过（需二级确认）")
    public ApiResponse<Void> skipAbnormalVoucher(@Valid @RequestBody PostingSkipRequest request) {
        postingEngineApplicationService.skipAbnormalVoucher(request);
        return ApiResponse.ok();
    }

    /**
     * GET /accounting/posting-engine/abnormal/list — 异常凭证列表
     */
    @GetMapping("/abnormal/list")
    @Operation(summary = "异常凭证列表", description = "查询过账失败或过账中的凭证")
    public ApiResponse<List<AbnormalVoucherResponse>> getAbnormalVouchers(
        @Parameter(name = "status", description = "凭证状态：1-待过账，2-过账中，3-已过账，4-失败，5-已跳过")
        @RequestParam(value = "status", required = false) Integer status,
        @Parameter(name = "startDate", description = "会计日期起始（含）")
        @RequestParam(value = "startDate", required = false) LocalDate startDate,
        @Parameter(name = "endDate", description = "会计日期截止（含）")
        @RequestParam(value = "endDate", required = false) LocalDate endDate) {
        return ApiResponse.ok(postingEngineApplicationService.getAbnormalVouchers(status, startDate, endDate));
    }
}
