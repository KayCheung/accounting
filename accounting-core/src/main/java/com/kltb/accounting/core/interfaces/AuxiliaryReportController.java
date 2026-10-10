package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.AuxiliaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.dto.DictResponse;
import com.kltb.accounting.core.application.service.AuxiliaryLedgerApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 辅助核算账簿报表 Controller
 */
@RestController
@RequestMapping("/accounting/report/auxiliary")
@RequiredArgsConstructor
@Validated
@Tag(name = "辅助核算账簿", description = "多维辅助核算类别、项目透视、交叉对比汇总与凭证流水接口")
public class AuxiliaryReportController {

    private final AuxiliaryLedgerApplicationService auxiliaryService;

    @GetMapping("/types")
    @Operation(summary = "查询辅助核算类别列表", description = "获取系统已配置的辅助核算类型（部门、项目、客户、供应商、员工等字典列表）")
    public ApiResponse<List<DictResponse>> getAuxiliaryTypes() {
        return ApiResponse.ok(auxiliaryService.getAuxiliaryTypes());
    }

    @GetMapping("/items")
    @Operation(summary = "查询指定类别的辅助核算项目列表", description = "从凭证辅助核算记录表中去重获取已发生的核算项目列表及发生统计")
    public ApiResponse<List<AuxiliaryItemResponse>> getAuxiliaryItems(
            @Parameter(description = "辅助核算类别编码，如 DEPARTMENT/PROJECT/CUSTOMER", required = true)
            @RequestParam String auxType,
            @Parameter(description = "会计起始日期")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "会计截止日期")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(auxiliaryService.getAuxiliaryItems(auxType, startDate, endDate));
    }

    @GetMapping("/summary")
    @Operation(summary = "查询辅助核算账簿统计 KPI 概览指标", description = "统计当前筛选条件下的累计发生额、借贷发生额、涉及科目数、凭证数及 Top5 核算项占比")
    public ApiResponse<AuxiliarySummaryResponse> getSummary(@Valid AuxiliaryLedgerQueryRequest request) {
        return ApiResponse.ok(auxiliaryService.getAuxiliarySummary(request));
    }

    @GetMapping("/matrix")
    @Operation(summary = "查询辅助核算交叉汇总对比矩阵（透视表）", description = "科目 × 核算项目的二维交叉发生额矩阵透视表，包含行合计、行占比与列合计")
    public ApiResponse<AuxiliaryMatrixResponse> getMatrix(@Valid AuxiliaryLedgerQueryRequest request) {
        return ApiResponse.ok(auxiliaryService.getAuxiliaryMatrix(request));
    }

    @GetMapping("/subjects")
    @Operation(summary = "查询辅助核算科目分布明细", description = "指定核算项或全核算项在各会计科目上的发生额、借贷金额、笔数与占比分布")
    public ApiResponse<List<AuxiliarySubjectDetailResponse>> getSubjectBreakdown(@Valid AuxiliaryLedgerQueryRequest request) {
        return ApiResponse.ok(auxiliaryService.getAuxiliarySubjectBreakdown(request));
    }

    @GetMapping("/entries")
    @Operation(summary = "分页查询辅助核算凭证分录记录", description = "分页展开逐笔辅助核算凭证分录流水，包含会计日期、凭证字号、摘要、借贷发生额，支持下钻凭证")
    public ApiResponse<PageResponse<AuxiliaryEntryRecordResponse>> getEntries(@Valid AuxiliaryLedgerQueryRequest request) {
        return ApiResponse.ok(auxiliaryService.getAuxiliaryEntries(request));
    }
}
