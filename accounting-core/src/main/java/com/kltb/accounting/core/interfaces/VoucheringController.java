package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.application.service.VoucheringApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 凭证生成 Controller
 */
@RestController
@RequestMapping("/accounting/voucher")
@RequiredArgsConstructor
@Validated
@Tag(name = "凭证生成", description = "凭证生成引擎接口")
public class VoucheringController {

    private final VoucheringApplicationService voucheringApplicationService;

    /**
     * POST /accounting/voucher/generate — 生成凭证
     */
    @PostMapping("/generate")
    @Operation(summary = "生成凭证")
    public ApiResponse<VoucherGenerateResponse> generate(@Valid @RequestBody VoucherGenerateRequest request) {
        VoucherGenerateResponse response = voucheringApplicationService.generateVoucher(request);
        return ApiResponse.ok(response);
    }

    /**
     * GET /accounting/voucher/{voucherNo} — 查询凭证详情
     * P3-1 修复：通过 Application Service 查询，返回完整 DTO
     */
    @GetMapping("/{voucherNo}")
    @Operation(summary = "查询凭证详情")
    @Parameter(name = "voucherNo", description = "凭证号")
    public ApiResponse<VoucherGenerateResponse> getVoucher(@PathVariable String voucherNo) {
        VoucherGenerateResponse response = voucheringApplicationService.getVoucherByNo(voucherNo);
        if (response == null) {
            return ApiResponse.fail(com.kltb.accounting.api.constant.ResultCode.VOUCHER_NOT_FOUND,
                    "凭证不存在: " + voucherNo);
        }
        return ApiResponse.ok(response);
    }

    /**
     * GET /accounting/voucher/trace/{traceNo} — 按流水号查询凭证
     * P3-1 修复：通过 Application Service 查询
     */
    @GetMapping("/trace/{traceNo}")
    @Operation(summary = "按流水号查询凭证")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<List<VoucherGenerateResponse>> getByTraceNo(@PathVariable String traceNo) {
        List<VoucherGenerateResponse> responses = voucheringApplicationService.getVouchersByTraceNo(traceNo);
        return ApiResponse.ok(responses);
    }
}
