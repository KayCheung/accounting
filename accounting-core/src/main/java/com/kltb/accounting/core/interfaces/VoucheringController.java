package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.request.VoucherPageQueryRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.VoucherFullDetailResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
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
 * 凭证生成与管理 Controller
 */
@RestController
@RequestMapping("/accounting/voucher")
@RequiredArgsConstructor
@Validated
@Tag(name = "凭证管理", description = "凭证生成与全景检索管理接口")
public class VoucheringController {

    private final VoucheringApplicationService voucheringApplicationService;

    /**
     * GET /accounting/voucher/page — 凭证综合分页查询
     * 注意：排在 /{voucherNo} 通配符之前，避免路径冲突
     */
    @GetMapping("/page")
    @Operation(summary = "凭证综合分页查询", description = "支持按凭证号、跟踪号、事务号、状态、类型、业务线、日期区间多维检索")
    public ApiResponse<PageResponse<VoucherPageItemResponse>> queryVoucherPage(@Valid VoucherPageQueryRequest request) {
        PageResponse<VoucherPageItemResponse> response = voucheringApplicationService.queryVoucherPage(request);
        return ApiResponse.ok(response);
    }

    /**
     * GET /accounting/voucher/detail/{voucherNo} — 查询凭证全景档案
     */
    @GetMapping("/detail/{voucherNo}")
    @Operation(summary = "查询凭证全景档案", description = "包含凭证头、借贷分录、辅助核算项、附件及红冲状态")
    @Parameter(name = "voucherNo", description = "凭证号")
    public ApiResponse<VoucherFullDetailResponse> getVoucherDetail(@PathVariable String voucherNo) {
        VoucherFullDetailResponse response = voucheringApplicationService.getVoucherDetail(voucherNo);
        if (response == null) {
            return ApiResponse.fail(com.kltb.accounting.api.constant.ResultCode.VOUCHER_NOT_FOUND,
                    "凭证不存在: " + voucherNo);
        }
        return ApiResponse.ok(response);
    }

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
     * GET /accounting/voucher/trace/{traceNo} — 按流水号查询凭证
     */
    @GetMapping("/trace/{traceNo}")
    @Operation(summary = "按流水号查询凭证")
    @Parameter(name = "traceNo", description = "系统跟踪号")
    public ApiResponse<List<VoucherGenerateResponse>> getByTraceNo(@PathVariable String traceNo) {
        List<VoucherGenerateResponse> responses = voucheringApplicationService.getVouchersByTraceNo(traceNo);
        return ApiResponse.ok(responses);
    }

    /**
     * GET /accounting/voucher/{voucherNo} — 查询凭证概要
     * 增加正则排除关键字防路由劫持
     */
    @GetMapping({"/info/{voucherNo}", "/{voucherNo:^(?!page$|generate$|trace$|detail$).+$}"})
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
}
