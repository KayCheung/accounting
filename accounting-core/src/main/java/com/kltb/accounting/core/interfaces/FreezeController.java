package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.FreezeDeductRequest;
import com.kltb.accounting.api.request.FundFreezeRequest;
import com.kltb.accounting.api.request.FundUnfreezeRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.FreezeDetailResponse;
import com.kltb.accounting.core.application.FreezeApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资金冻结管理 Controller
 */
@RestController
@RequestMapping("/accounting/account/freeze")
@RequiredArgsConstructor
@Validated
@Tag(name = "资金冻结管理", description = "资金冻结/解冻/扣款/查询接口")
public class FreezeController {

    private final FreezeApplicationService freezeApplicationService;

    @PostMapping("/fund")
    @Operation(summary = "资金冻结", description = "从可用子账户转移指定金额到冻结子账户")
    public ApiResponse<FreezeDetailResponse> freezeFund(@Valid @RequestBody FundFreezeRequest request) {
        FreezeDetailResponse response = freezeApplicationService.freezeFund(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/unfreeze")
    @Operation(summary = "资金解冻", description = "基于原冻结记录将冻结余额转回可用余额")
    public ApiResponse<Void> unfreezeFund(@Valid @RequestBody FundUnfreezeRequest request) {
        freezeApplicationService.unfreezeFund(request);
        return ApiResponse.ok();
    }

    @PostMapping("/deduct")
    @Operation(summary = "冻结扣款", description = "在冻结额度内直接扣款")
    public ApiResponse<Void> deductFromFreeze(@Valid @RequestBody FreezeDeductRequest request) {
        freezeApplicationService.deductFromFreeze(request);
        return ApiResponse.ok();
    }

    @GetMapping("/{freezeId}")
    @Operation(summary = "查询冻结记录", description = "按冻结编号查询冻结记录详情")
    public ApiResponse<FreezeDetailResponse> queryFreezeRecord(
            @Parameter(name = "freezeId", description = "冻结编号") @PathVariable String freezeId) {
        FreezeDetailResponse response = freezeApplicationService.queryFreezeRecord(freezeId);
        return ApiResponse.ok(response);
    }

    @GetMapping("/list")
    @Operation(summary = "冻结记录列表", description = "查询指定账户的冻结记录（可按状态过滤）")
    public ApiResponse<List<FreezeDetailResponse>> queryFreezeRecords(
            @Parameter(name = "accountNo", description = "账户编号") @RequestParam String accountNo,
            @Parameter(name = "status", description = "状态：1-冻结, 2-已解冻") @RequestParam(required = false) Integer status) {
        List<FreezeDetailResponse> responses = freezeApplicationService.queryFreezeRecords(accountNo, status);
        return ApiResponse.ok(responses);
    }
}
