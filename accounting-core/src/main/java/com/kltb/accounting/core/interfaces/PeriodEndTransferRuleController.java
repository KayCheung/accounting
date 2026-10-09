package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.TransferRuleCreateRequest;
import com.kltb.accounting.api.request.TransferRuleQueryRequest;
import com.kltb.accounting.api.request.TransferRuleStatusRequest;
import com.kltb.accounting.api.request.TransferRuleUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.TransferRuleResponse;
import com.kltb.accounting.core.application.service.PeriodEndTransferApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 期末结转规则管理 Controller
 */
@RestController
@RequestMapping("/accounting/transfer/rule")
@RequiredArgsConstructor
@Validated
@Tag(name = "期末结转规则管理", description = "期末损益结转、成本结转等规则的配置与维护接口")
public class PeriodEndTransferRuleController {

    private final PeriodEndTransferApplicationService transferApplicationService;

    @GetMapping("/page")
    @Operation(summary = "分页查询结转规则", description = "支持按编码、名称、结转类型、启用状态多维筛选")
    public ApiResponse<PageResponse<TransferRuleResponse>> pageRules(@Valid TransferRuleQueryRequest request) {
        return ApiResponse.ok(transferApplicationService.pageRules(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询结转规则详情", description = "根据规则ID查询详情")
    public ApiResponse<TransferRuleResponse> getRuleById(
            @Parameter(description = "规则ID") @PathVariable Long id) {
        return ApiResponse.ok(transferApplicationService.getRuleById(id));
    }

    @PostMapping
    @Operation(summary = "新增结转规则", description = "创建新的期末结转规则（校验编码唯一与末级目标科目）")
    public ApiResponse<Long> createRule(@Valid @RequestBody TransferRuleCreateRequest request) {
        return ApiResponse.ok(transferApplicationService.createRule(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改结转规则", description = "修改已有期末结转规则内容")
    public ApiResponse<Void> updateRule(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @Valid @RequestBody TransferRuleUpdateRequest request) {
        transferApplicationService.updateRule(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换规则状态", description = "启用或停用指定的期末结转规则")
    public ApiResponse<Void> updateRuleStatus(
            @Parameter(description = "规则ID") @PathVariable Long id,
            @Valid @RequestBody TransferRuleStatusRequest request) {
        transferApplicationService.updateRuleStatus(id, request.getStatus());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除结转规则", description = "逻辑删除指定的期末结转规则")
    public ApiResponse<Void> deleteRule(
            @Parameter(description = "规则ID") @PathVariable Long id) {
        transferApplicationService.deleteRule(id);
        return ApiResponse.ok();
    }
}
