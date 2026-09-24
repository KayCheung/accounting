package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AccountDetailQueryRequest;
import com.kltb.accounting.api.request.BalanceAggregateRequest;
import com.kltb.accounting.api.request.FreezeRecordQueryRequest;
import com.kltb.accounting.api.response.AccountDetailResponse;
import com.kltb.accounting.api.response.AggregateBalanceResponse;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.FreezeListResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.BalanceQueryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 余额查询 Controller
 */
@RestController
@RequestMapping("/accounting/account/balance")
@RequiredArgsConstructor
@Validated
@Tag(name = "余额查询", description = "聚合余额/账户明细/冻结记录查询接口")
public class BalanceQueryController {

    private final BalanceQueryApplicationService balanceQueryApplicationService;

    @GetMapping("/aggregate")
    @Operation(summary = "聚合余额查询", description = "查询指定账户的完整余额信息（主账户 + 可用子账户 + 冻结子账户 + 缓冲预估）")
    public ApiResponse<AggregateBalanceResponse> queryAggregateBalance(
            @Valid BalanceAggregateRequest request) {
        AggregateBalanceResponse response = balanceQueryApplicationService.queryAggregateBalance(
                request.getAccountNo());
        return ApiResponse.ok(response);
    }

    @GetMapping("/details")
    @Operation(summary = "账户明细分页查询", description = "按日期范围、交易类型、借贷方向等条件分页查询账户变动明细")
    public ApiResponse<PageResponse<AccountDetailResponse>> queryAccountDetails(
            @Valid AccountDetailQueryRequest request) {
        PageResponse<AccountDetailResponse> response = balanceQueryApplicationService.queryAccountDetails(request);
        return ApiResponse.ok(response);
    }

    @GetMapping("/freeze-records")
    @Operation(summary = "冻结记录分页查询", description = "按账户查询冻结记录列表（可按状态过滤）")
    public ApiResponse<PageResponse<FreezeListResponse>> queryFreezeRecords(
            @Valid FreezeRecordQueryRequest request) {
        PageResponse<FreezeListResponse> response = balanceQueryApplicationService.queryFreezeRecords(request);
        return ApiResponse.ok(response);
    }
}
