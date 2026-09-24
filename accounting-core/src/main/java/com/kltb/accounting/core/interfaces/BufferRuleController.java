package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.BufferRuleCreateRequest;
import com.kltb.accounting.api.request.BufferRuleQueryRequest;
import com.kltb.accounting.api.request.BufferRuleUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.BufferRuleApplicationService;
import com.kltb.accounting.core.application.dto.BufferRuleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 缓冲入账规则管理 Controller
 */
@RestController
@RequestMapping("/accounting/config/buffer-rule")
@RequiredArgsConstructor
@Validated
@Tag(name = "缓冲入账规则管理", description = "缓冲入账规则维护接口")
public class BufferRuleController {

    private final BufferRuleApplicationService bufferRuleApplicationService;

    @PostMapping
    @Operation(summary = "创建缓冲入账规则")
    public ApiResponse<Void> create(@Valid @RequestBody BufferRuleCreateRequest request) {
        bufferRuleApplicationService.create(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "更新缓冲入账规则")
    public ApiResponse<Void> update(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId,
            @Valid @RequestBody BufferRuleUpdateRequest request) {
        bufferRuleApplicationService.update(ruleId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "停用缓冲入账规则")
    public ApiResponse<Void> disable(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        bufferRuleApplicationService.disable(ruleId);
        return ApiResponse.ok();
    }

    @PostMapping("/{ruleId}/enable")
    @Operation(summary = "启用缓冲入账规则")
    public ApiResponse<Void> enable(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        bufferRuleApplicationService.enable(ruleId);
        return ApiResponse.ok();
    }

    @GetMapping("/{ruleId}")
    @Operation(summary = "查询单个缓冲规则")
    public ApiResponse<BufferRuleResponse> getById(
            @Parameter(name = "ruleId", description = "规则ID") @PathVariable Long ruleId) {
        BufferRuleResponse resp = bufferRuleApplicationService.getById(ruleId);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询缓冲规则")
    public ApiResponse<PageResponse<BufferRuleResponse>> page(BufferRuleQueryRequest request) {
        PageResponse<BufferRuleResponse> result = bufferRuleApplicationService.page(request);
        return ApiResponse.ok(result);
    }
}
