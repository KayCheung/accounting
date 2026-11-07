package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.ReportItemMappingCreateRequest;
import com.kltb.accounting.api.request.ReportItemMappingQueryRequest;
import com.kltb.accounting.api.request.ReportItemMappingUpdateRequest;
import com.kltb.accounting.api.response.ApiResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.ReportItemMappingResponse;
import com.kltb.accounting.core.application.service.FinancialReportItemMappingApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 财务报表配置中心 Controller
 */
@RestController
@RequestMapping("/accounting/config/report-mapping")
@RequiredArgsConstructor
@Validated
@Tag(name = "财务报表行项目映射配置", description = "资产负债表、利润表等报表行项目与科目映射规则维护")
public class FinancialReportItemMappingController {

    private final FinancialReportItemMappingApplicationService mappingApplicationService;

    @GetMapping("/page")
    @Operation(summary = "分页查询报表行项目配置", description = "支持按报表类型、关键字、分类、层级、启用状态多维筛选")
    public ApiResponse<PageResponse<ReportItemMappingResponse>> pageMappings(@Valid ReportItemMappingQueryRequest request) {
        return ApiResponse.ok(mappingApplicationService.pageMappings(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询报表行项目详情", description = "根据主键ID查询单条配置详情")
    public ApiResponse<ReportItemMappingResponse> getById(
            @Parameter(description = "主键ID") @PathVariable Long id) {
        return ApiResponse.ok(mappingApplicationService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增报表行项目配置", description = "新增单条报表行项目与科目映射配置")
    public ApiResponse<Long> createMapping(@Valid @RequestBody ReportItemMappingCreateRequest request) {
        return ApiResponse.ok(mappingApplicationService.createMapping(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改报表行项目配置", description = "修改已有报表行项目与科目映射配置")
    public ApiResponse<Void> updateMapping(
            @Parameter(description = "主键ID") @PathVariable Long id,
            @Valid @RequestBody ReportItemMappingUpdateRequest request) {
        mappingApplicationService.updateMapping(id, request);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "切换启用状态", description = "修改报表行项目的启用/停用状态")
    public ApiResponse<Void> updateStatus(
            @Parameter(description = "主键ID") @PathVariable Long id,
            @Parameter(description = "状态: 1-启用, 2-停用") @RequestParam Integer status) {
        mappingApplicationService.updateStatus(id, status);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除报表行项目配置", description = "逻辑删除指定的报表行项目配置")
    public ApiResponse<Void> deleteMapping(
            @Parameter(description = "主键ID") @PathVariable Long id) {
        mappingApplicationService.deleteMapping(id);
        return ApiResponse.ok();
    }
}
