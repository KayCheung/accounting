package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.request.*;
import com.kltb.accounting.core.application.service.FinancialReportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 财务报表统一导出 Controller
 * <p>
 * 提供科目总账、科目明细账、资产负债表、利润表及辅助核算账簿的标准 Excel 文件流导出。
 * </p>
 */
@RestController
@RequestMapping("/accounting/report/export")
@RequiredArgsConstructor
@Validated
@Tag(name = "财务报表中心-导出", description = "总账、明细账、资产负债表、利润表、辅助核算账簿 Excel 导出接口")
public class FinancialReportExportController {

    private final FinancialReportExportService exportService;

    @GetMapping("/general-ledger")
    @Operation(summary = "导出科目总分类账 Excel", description = "按会计期间与科目范围生成标准三栏式总账 Excel 文件并流式下载")
    public void exportGeneralLedger(@Valid GeneralLedgerQueryRequest request, HttpServletResponse response) {
        exportService.exportGeneralLedger(request, response);
    }

    @GetMapping("/subsidiary-ledger")
    @Operation(summary = "导出科目明细账 Excel", description = "按指定科目与会计期间导出分录流水与期初期末余额 Excel 文件")
    public void exportSubsidiaryLedger(@Valid SubsidiaryLedgerQueryRequest request, HttpServletResponse response) {
        exportService.exportSubsidiaryLedger(request, response);
    }

    @GetMapping("/balance-sheet")
    @Operation(summary = "导出资产负债表 Excel", description = "按指定会计日期导出左右双栏标准资产负债表 Excel 文件")
    public void exportBalanceSheet(@Valid BalanceSheetQueryRequest request, HttpServletResponse response) {
        exportService.exportBalanceSheet(request, response);
    }

    @GetMapping("/income-statement")
    @Operation(summary = "导出利润表 Excel", description = "按会计年度与月份导出多步式利润表及 KPI 概览 Excel 文件")
    public void exportIncomeStatement(@Valid IncomeStatementQueryRequest request, HttpServletResponse response) {
        exportService.exportIncomeStatement(request, response);
    }

    @GetMapping("/auxiliary")
    @Operation(summary = "导出辅助核算账簿 Excel", description = "导出多维辅助核算透视矩阵及明细流水双 Sheet Excel 文件")
    public void exportAuxiliaryLedger(@Valid AuxiliaryLedgerQueryRequest request, HttpServletResponse response) {
        exportService.exportAuxiliaryLedger(request, response);
    }
}
