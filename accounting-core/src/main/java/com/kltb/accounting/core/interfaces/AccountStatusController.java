package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AccountCancelRequest;
import com.kltb.accounting.api.request.AccountFreezeRequest;
import com.kltb.accounting.api.request.AccountRiskStatusRequest;
import com.kltb.accounting.api.request.AccountUnfreezeRequest;
import com.kltb.accounting.api.response.AccountStatusChangeResponse;
import com.kltb.accounting.api.response.AccountStatusResponse;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.core.application.AccountStatusApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 账户状态管理 Controller
 */
@RestController
@RequestMapping("/accounting/account/status")
@RequiredArgsConstructor
@Validated
@Tag(name = "账户状态管理", description = "账户冻结/解冻/注销/风控状态变更接口")
public class AccountStatusController {

    private final AccountStatusApplicationService accountStatusApplicationService;

    @PostMapping("/freeze")
    @Operation(summary = "冻结账户")
    public ApiResponse<AccountStatusChangeResponse> freeze(@Valid @RequestBody AccountFreezeRequest request) {
        AccountStatusChangeResponse response = accountStatusApplicationService.freeze(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/unfreeze")
    @Operation(summary = "解冻账户")
    public ApiResponse<AccountStatusChangeResponse> unfreeze(@Valid @RequestBody AccountUnfreezeRequest request) {
        AccountStatusChangeResponse response = accountStatusApplicationService.unfreeze(request);
        return ApiResponse.ok(response);
    }

    @PostMapping("/cancel")
    @Operation(summary = "注销账户")
    public ApiResponse<AccountStatusChangeResponse> cancel(@Valid @RequestBody AccountCancelRequest request) {
        AccountStatusChangeResponse response = accountStatusApplicationService.cancel(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/{accountNo}")
    @Operation(summary = "查询账户状态")
    public ApiResponse<AccountStatusResponse> queryStatus(
            @Parameter(name = "accountNo", description = "账户编号") @PathVariable String accountNo) {
        AccountStatusResponse response = accountStatusApplicationService.queryStatus(accountNo);
        return ApiResponse.ok(response);
    }

    @PostMapping("/risk")
    @Operation(summary = "变更风控状态")
    public ApiResponse<AccountStatusChangeResponse> changeRiskStatus(
            @Valid @RequestBody AccountRiskStatusRequest request) {
        AccountStatusChangeResponse response = accountStatusApplicationService.changeRiskStatus(request);
        return ApiResponse.ok(response);
    }
}
