package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.ReversalRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.ReversalRecordResponse;
import com.kltb.accounting.api.response.ReversalResponse;
import com.kltb.accounting.core.application.service.ReversalApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 红冲管理 REST 接口
 */
@RestController
@RequestMapping("/accounting/reversal")
@RequiredArgsConstructor
@Validated
@Tag(name = "红冲管理", description = "凭证红冲（冲账）接口")
public class ReversalController {

    private final ReversalApplicationService reversalApplicationService;

    /**
     * 执行凭证红冲
     */
    @PostMapping("/execute")
    @Operation(summary = "执行凭证红冲", description = "根据原凭证号生成红冲凭证并执行过账")
    public ApiResponse<ReversalResponse> executeReversal(@Valid @RequestBody ReversalRequest request) {
        ReversalResponse response = reversalApplicationService.executeReversal(request);
        return ApiResponse.ok(response);
    }

    /**
     * 查询某凭证的红冲记录
     */
    @GetMapping("/records")
    @Operation(summary = "查询红冲记录", description = "查询指定凭证的所有红冲凭证")
    @Parameter(name = "origVoucherNo", description = "原凭证号")
    public ApiResponse<List<ReversalRecordResponse>> queryReversalRecords(
            @RequestParam String origVoucherNo) {
        List<ReversalRecordResponse> responses = reversalApplicationService.queryReversalRecords(origVoucherNo);
        return ApiResponse.ok(responses);
    }

    /**
     * 判断凭证是否可红冲
     */
    @GetMapping("/check")
    @Operation(summary = "红冲可行性检查", description = "判断指定凭证是否可以被红冲")
    @Parameter(name = "voucherNo", description = "凭证号")
    public ApiResponse<Boolean> isReversable(@RequestParam String voucherNo) {
        boolean reversable = reversalApplicationService.isReversable(voucherNo);
        return ApiResponse.ok(reversable);
    }
}
