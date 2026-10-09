package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.BalanceSheetQueryRequest;
import com.kltb.accounting.api.request.GeneralLedgerQueryRequest;
import com.kltb.accounting.api.request.IncomeStatementQueryRequest;
import com.kltb.accounting.api.request.SubsidiaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.service.FinancialReportApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 财务报表中心 Controller
 */
@RestController
@RequestMapping("/accounting/report")
@RequiredArgsConstructor
@Validated
@Tag(name = "财务报表中心", description = "资产负债表、利润表、科目总账、科目明细账等标准财务报表接口")
public class FinancialReportController {

    private final FinancialReportApplicationService reportApplicationService;

    @GetMapping("/balance-sheet")
    @Operation(summary = "查询资产负债表", description = "按指定会计日期生成资产负债表，左侧资产，右侧负债及所有者权益，并校验借贷平衡")
    public ApiResponse<BalanceSheetResponse> getBalanceSheet(@Valid BalanceSheetQueryRequest request) {
        return ApiResponse.ok(reportApplicationService.getBalanceSheet(request));
    }

    @GetMapping("/income-statement")
    @Operation(summary = "查询利润表", description = "按指定会计年度与月份生成多步式利润表，包含本月数、本年累计数与顶层财务 KPI")
    public ApiResponse<IncomeStatementResponse> getIncomeStatement(@Valid IncomeStatementQueryRequest request) {
        return ApiResponse.ok(reportApplicationService.getIncomeStatement(request));
    }

    @GetMapping("/general-ledger")
    @Operation(summary = "查询科目总账", description = "按会计期间与科目范围汇总期初余额、本期借贷发生额、期末余额，并校验借贷试算平衡")
    public ApiResponse<GeneralLedgerResponse> getGeneralLedger(@Valid GeneralLedgerQueryRequest request) {
        return ApiResponse.ok(reportApplicationService.getGeneralLedger(request));
    }

    @GetMapping("/subsidiary-ledger")
    @Operation(summary = "查询科目明细账", description = "按指定科目与会计期间展开已过账分录流水，包含期初余额行、逐笔明细与本期合计")
    public ApiResponse<SubsidiaryLedgerResponse> getSubsidiaryLedger(@Valid SubsidiaryLedgerQueryRequest request) {
        return ApiResponse.ok(reportApplicationService.getSubsidiaryLedger(request));
    }

    @PostMapping("/generate")
    @Operation(summary = "触发报表生成与预热", description = "支持管理端或定时任务按指定会计日期预热生成整套财务报表")
    public ApiResponse<Void> generateReports(
            @Parameter(description = "会计日期（默认当前系统日期）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate accountingDate) {
        LocalDate targetDate = accountingDate != null ? accountingDate : LocalDate.now();
        reportApplicationService.generateAndArchiveReports(targetDate);
        return ApiResponse.ok();
    }
}
