package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.PostingExecuteRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.api.response.TransactionStatusResponse;
import com.kltb.accounting.core.application.service.PostingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 凭证过账 Controller
 */
@RestController
@RequestMapping("/accounting/posting")
@RequiredArgsConstructor
@Validated
@Tag(name = "凭证过账", description = "过账执行与状态查询接口")
public class PostingController {

    private final PostingApplicationService postingApplicationService;

    /**
     * POST /accounting/posting/execute — 执行过账
     */
    @PostMapping("/execute")
    @Operation(summary = "执行过账")
    public ApiResponse<PostingExecuteResponse> executePosting(@Valid @RequestBody PostingExecuteRequest request) {
        return ApiResponse.ok(postingApplicationService.executePosting(request));
    }

    /**
     * GET /accounting/posting/voucher/{voucherNo} — 查询过账状态
     */
    @GetMapping("/voucher/{voucherNo}")
    @Operation(summary = "查询过账状态")
    @Parameter(name = "voucherNo", description = "凭证号")
    public ApiResponse<PostingExecuteResponse> getPostingStatus(@PathVariable String voucherNo) {
        return ApiResponse.ok(postingApplicationService.getPostingStatus(voucherNo));
    }

    /**
     * GET /accounting/posting/transaction/{txnNo} — 查询事务状态
     */
    @GetMapping("/transaction/{txnNo}")
    @Operation(summary = "查询事务状态")
    @Parameter(name = "txnNo", description = "事务编号")
    public ApiResponse<TransactionStatusResponse> getTransactionStatus(@PathVariable String txnNo) {
        return ApiResponse.ok(postingApplicationService.getTransactionStatus(txnNo));
    }
}
