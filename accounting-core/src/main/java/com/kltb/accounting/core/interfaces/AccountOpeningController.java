package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AccountOpenRequest;
import com.kltb.accounting.api.request.AccountTemplateBatchOpenRequest;
import com.kltb.accounting.api.request.InternalAccountOpenRequest;
import com.kltb.accounting.api.response.AccountOpenResponse;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.BatchOpenResultResponse;
import com.kltb.accounting.core.application.AccountOpeningApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 开户管理 Controller
 */
@RestController
@RequestMapping("/accounting/account/opening")
@RequiredArgsConstructor
@Validated
@Tag(name = "开户管理", description = "自动化开户引擎接口")
public class AccountOpeningController {

    private final AccountOpeningApplicationService accountOpeningApplicationService;

    @PostMapping("/external")
    @Operation(summary = "外部客户开户（单账户）")
    public ApiResponse<AccountOpenResponse> openExternal(@Valid @RequestBody AccountOpenRequest request) {
        AccountOpenResponse response = accountOpeningApplicationService.openExternalAccount(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/external/batch")
    @Operation(summary = "按模板批量开立客户账户（单模板多科目账户批量生成）")
    public ApiResponse<List<AccountOpenResponse>> openExternalBatch(
            @Valid @RequestBody AccountTemplateBatchOpenRequest request) {
        List<AccountOpenResponse> responses = accountOpeningApplicationService.openExternalAccounts(request);
        return ApiResponse.ok(responses);
    }

    @PostMapping("/internal")
    @Operation(summary = "内部账户开户")
    public ApiResponse<AccountOpenResponse> openInternal(@Valid @RequestBody InternalAccountOpenRequest request) {
        AccountOpenResponse response = accountOpeningApplicationService.openInternalAccount(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/internal/batch")
    @Operation(summary = "批量扫描内部账户")
    public ApiResponse<BatchOpenResultResponse> batchOpenInternal() {
        BatchOpenResultResponse response = accountOpeningApplicationService.batchOpenInternalAccounts();
        return ApiResponse.ok(response);
    }

    @GetMapping("/{accountNo}")
    @Operation(summary = "查询账户信息")
    public ApiResponse<AccountOpenResponse> getAccountInfo(
            @Parameter(name = "accountNo", description = "账户编号") @PathVariable String accountNo) {
        AccountOpenResponse response = accountOpeningApplicationService.getAccountInfo(accountNo);
        return ApiResponse.ok(response);
    }
}
