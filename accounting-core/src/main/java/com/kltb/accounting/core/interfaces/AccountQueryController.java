package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AccountPageQueryRequest;
import com.kltb.accounting.api.response.AccountPageResponse;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.AccountQueryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户综合查询 Controller
 */
@RestController
@RequestMapping("/accounting/account")
@RequiredArgsConstructor
@Validated
@Tag(name = "账户查询", description = "账户多维分页检索与列表查询接口")
public class AccountQueryController {

    private final AccountQueryApplicationService accountQueryApplicationService;

    @GetMapping("/page")
    @Operation(summary = "账户多维分页检索", description = "支持按账户编号/名称/客户ID/类型/科目/状态/日期范围等条件综合分页查询")
    public ApiResponse<PageResponse<AccountPageResponse>> page(@Valid AccountPageQueryRequest request) {
        PageResponse<AccountPageResponse> response = accountQueryApplicationService.page(request);
        return ApiResponse.ok(response);
    }
}
