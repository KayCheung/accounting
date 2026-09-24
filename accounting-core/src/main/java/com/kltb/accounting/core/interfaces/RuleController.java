package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.RuleCreateRequest;
import com.kltb.accounting.api.request.RuleQueryRequest;
import com.kltb.accounting.api.request.RuleUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.RuleApplicationService;
import com.kltb.accounting.core.application.dto.RuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 记账规则管理 Controller
 */
@RestController
@RequestMapping("/accounting/config/rule")
@RequiredArgsConstructor
@Validated
@Tag(name = "记账规则管理", description = "记账规则维护接口")
public class RuleController {

    private final RuleApplicationService ruleApplicationService;

    @PostMapping
    @Operation(summary = "创建记账规则")
    public ApiResponse<Void> create(@Valid @RequestBody RuleCreateRequest request) {
        ruleApplicationService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "更新记账规则")
    public ApiResponse<Void> update(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId,
            @Valid @RequestBody RuleUpdateRequest request) {
        ruleApplicationService.update(ruleId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/{ruleId}/enable")
    @Operation(summary = "启用记账规则")
    public ApiResponse<Void> enable(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        ruleApplicationService.enable(ruleId);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "停用记账规则")
    public ApiResponse<Void> disable(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        ruleApplicationService.disable(ruleId);
        return ApiResponse.ok();
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "查询单个规则（含明细）")
    public ApiResponse<RuleResponse> getById(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        RuleResponse resp = ruleApplicationService.getById(ruleId);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询规则")
    public ApiResponse<PageResponse<RuleResponse>> page(RuleQueryRequest request) {
        PageResponse<RuleResponse> result = ruleApplicationService.page(request);
        return ApiResponse.ok(result);
    }
}
