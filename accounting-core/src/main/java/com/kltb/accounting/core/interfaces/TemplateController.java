package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.TemplateCreateRequest;
import com.kltb.accounting.api.request.TemplateGroupSaveRequest;
import com.kltb.accounting.api.request.TemplateQueryRequest;
import com.kltb.accounting.api.request.TemplateUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.TemplateApplicationService;
import com.kltb.accounting.core.application.dto.TemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 开户模板管理 Controller
 */
@RestController
@RequestMapping("/accounting/config/template")
@RequiredArgsConstructor
@Validated
@Tag(name = "开户模板管理", description = "开户模板维护接口")
public class TemplateController {

    private final TemplateApplicationService templateApplicationService;

    @PostMapping
    @Operation(summary = "创建开户模板")
    public ApiResponse<Void> create(@Valid @RequestBody TemplateCreateRequest request) {
        templateApplicationService.create(request);
        return ApiResponse.ok();
    }

    @PostMapping("/group")
    @Operation(summary = "批量保存开户模板（多科目账户）")
    public ApiResponse<Void> saveGroup(@Valid @RequestBody TemplateGroupSaveRequest request) {
        templateApplicationService.saveGroup(request);
        return ApiResponse.ok();
    }

    @PutMapping("/{templateId}")
    @Operation(summary = "更新开户模板")
    public ApiResponse<Void> update(
            @Parameter(name = "templateId", description = "模板ID") @PathVariable Long templateId,
            @Valid @RequestBody TemplateUpdateRequest request) {
        templateApplicationService.update(templateId, request);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{templateId}")
    @Operation(summary = "停用开户模板")
    public ApiResponse<Void> disable(
            @Parameter(name = "templateId", description = "模板ID") @PathVariable Long templateId) {
        templateApplicationService.disable(templateId);
        return ApiResponse.ok();
    }

    @GetMapping("/{templateId}")
    @Operation(summary = "查询单个模板")
    public ApiResponse<TemplateResponse> getById(
            @Parameter(name = "templateId", description = "模板ID") @PathVariable Long templateId) {
        TemplateResponse resp = templateApplicationService.getById(templateId);
        return ApiResponse.ok(resp);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询模板")
    public ApiResponse<PageResponse<TemplateResponse>> page(TemplateQueryRequest request) {
        PageResponse<TemplateResponse> result = templateApplicationService.page(request);
        return ApiResponse.ok(result);
    }
}
