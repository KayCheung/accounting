// accounting-core/src/main/java/com/kltb/accounting/core/interfaces/JournalingController.java
package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.JournalFreezeRequest;
import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.request.JournalUnfreezeRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 记账流水入库与交易 Controller
 */
@RestController
@RequestMapping("/accounting/journal")
@RequiredArgsConstructor
@Validated
@Tag(name = "记账流水与交易", description = "业务流水入库、预冻结/撤销及全链路总览接口")
public class JournalingController {

    private final JournalingApplicationService journalingApplicationService;

    @PostMapping("/submit")
    @Operation(summary = "提交记账流水", description = "支持直接入账与基于预冻结单号(origFreezeNo)核销记账")
    public ApiResponse<JournalSubmitResponse> submit(
            @Valid @RequestBody JournalSubmitRequest request) {
        JournalSubmitResponse response = journalingApplicationService.submitJournal(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/freeze")
    @Operation(summary = "业务预冻结", description = "入参与流水提交类似，通过业务参数匹配规则自动识别出金方并锁定资金")
    public ApiResponse<JournalFreezeResponse> freeze(
            @Valid @RequestBody JournalFreezeRequest request) {
        JournalFreezeResponse response = journalingApplicationService.freezeJournal(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/unfreeze")
    @Operation(summary = "业务预冻结解冻(撤销)", description = "根据原流水号全额解冻撤销，释放预冻结资金")
    public ApiResponse<JournalUnfreezeResponse> unfreeze(
            @Valid @RequestBody JournalUnfreezeRequest request) {
        JournalUnfreezeResponse response = journalingApplicationService.unfreezeJournal(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{traceNo}")
    @Operation(summary = "查询记账流水处理结果", description = "面向业务调用方，返回记账流水的最终处理状态与结果")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<JournalQueryResponse> getJournal(@PathVariable String traceNo) {
        JournalQueryResponse response = journalingApplicationService.getJournal(traceNo);
        if (response == null) {
            return ApiResponse.fail(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        return ApiResponse.ok(response);
    }

    @GetMapping("/{traceNo}/overview")
    @Operation(summary = "查询流水全流程总览", description = "面向管理后台看板，聚合展示流水、关联事务、记账凭证、分录及资金冻结全链路明细")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<JournalOverviewResponse> getJournalOverview(@PathVariable String traceNo) {
        JournalOverviewResponse response = journalingApplicationService.getJournalOverview(traceNo);
        if (response == null) {
            return ApiResponse.fail(ResultCode.JOURNAL_NOT_FOUND, "未找到流水记录: " + traceNo);
        }
        return ApiResponse.ok(response);
    }

    @GetMapping("/trace/{traceNo}/transaction")
    @Operation(summary = "查询关联事务")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<TransactionStatusResponse> getTransaction(@PathVariable String traceNo) {
        TransactionStatusResponse response = journalingApplicationService.getTransactionStatus(traceNo);
        if (response == null) {
            return ApiResponse.fail(ResultCode.DATA_NOT_FOUND, "未找到关联事务: " + traceNo);
        }
        return ApiResponse.ok(response);
    }

    @PostMapping("/{traceNo}/retry")
    @Operation(summary = "记账失败重试", description = "对处于 FAILED 失败状态的流水重新触发凭证生成与过账执行")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<JournalOverviewResponse> retry(@PathVariable String traceNo) {
        JournalOverviewResponse response = journalingApplicationService.retryJournal(traceNo);
        return ApiResponse.ok(response);
    }

    @PostMapping("/{traceNo}/rollback")
    @Operation(summary = "流水回滚与冲账", description = "对异常或需撤销流水触发回滚，生成反向冲账凭证并恢复账户余额")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<JournalOverviewResponse> rollback(
            @PathVariable String traceNo,
            @RequestParam(required = false, defaultValue = "MANUAL_ROLLBACK") String reason) {
        JournalOverviewResponse response = journalingApplicationService.rollbackJournal(traceNo, reason);
        return ApiResponse.ok(response);
    }
}
