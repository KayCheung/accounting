package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 财务报表统一导出服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class FinancialReportExportServiceTest {

    @Mock
    private FinancialReportApplicationService reportApplicationService;

    @Mock
    private AuxiliaryLedgerApplicationService auxiliaryLedgerApplicationService;

    @InjectMocks
    private FinancialReportExportService exportService;

    @Test
    @DisplayName("测试科目总分类账导出 Excel 正常生成并写入响应流")
    void testExportGeneralLedger() throws Exception {
        GeneralLedgerResponse responseData = GeneralLedgerResponse.builder()
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 3, 31))
                .isBalanced(true)
                .totalSubjectCount(1)
                .totalPeriodDebit(new BigDecimal("1000.00"))
                .totalPeriodCredit(new BigDecimal("1000.00"))
                .items(List.of(
                        GeneralLedgerItemResponse.builder()
                                .subjectCode("1002")
                                .subjectName("银行存款")
                                .subjectLevel(1)
                                .balanceDirectionDesc("借")
                                .beginBalance(new BigDecimal("5000.00"))
                                .debitAmount(new BigDecimal("1000.00"))
                                .creditAmount(new BigDecimal("0.00"))
                                .endBalance(new BigDecimal("6000.00"))
                                .build()
                ))
                .build();

        when(reportApplicationService.getGeneralLedger(any())).thenReturn(responseData);

        MockHttpServletResponse response = new MockHttpServletResponse();
        GeneralLedgerQueryRequest req = new GeneralLedgerQueryRequest();
        req.setStartDate(LocalDate.of(2026, 1, 1));
        req.setEndDate(LocalDate.of(2026, 3, 31));

        exportService.exportGeneralLedger(req, response);

        assertThat(response.getContentType()).startsWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(response.getHeader("Content-Disposition")).contains("filename=");

        byte[] content = response.getContentAsByteArray();
        assertThat(content).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("科目总分类账");
        }
    }

    @Test
    @DisplayName("测试科目明细分类账导出 Excel 正常生成")
    void testExportSubsidiaryLedger() throws Exception {
        SubsidiaryLedgerResponse responseData = SubsidiaryLedgerResponse.builder()
                .subjectCode("1002")
                .subjectName("银行存款")
                .subjectLevel(1)
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 3, 31))
                .balanceDirectionDesc("借")
                .endBalance(new BigDecimal("6000.00"))
                .items(List.of(
                        SubsidiaryLedgerItemResponse.builder()
                                .accountingDate(LocalDate.of(2026, 1, 15))
                                .voucherNo("V202601150001")
                                .summary("收到客户转账款")
                                .debitAmount(new BigDecimal("1000.00"))
                                .creditAmount(BigDecimal.ZERO)
                                .balanceDirectionDesc("借")
                                .balance(new BigDecimal("6000.00"))
                                .build()
                ))
                .build();

        when(reportApplicationService.getSubsidiaryLedger(any())).thenReturn(responseData);

        MockHttpServletResponse response = new MockHttpServletResponse();
        SubsidiaryLedgerQueryRequest req = new SubsidiaryLedgerQueryRequest();
        req.setSubjectCode("1002");
        req.setStartDate(LocalDate.of(2026, 1, 1));
        req.setEndDate(LocalDate.of(2026, 3, 31));

        exportService.exportSubsidiaryLedger(req, response);

        byte[] content = response.getContentAsByteArray();
        assertThat(content).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("科目明细账");
        }
    }

    @Test
    @DisplayName("测试资产负债表导出 Excel 正常生成（双栏结构）")
    void testExportBalanceSheet() throws Exception {
        BalanceSheetResponse responseData = BalanceSheetResponse.builder()
                .accountingDate(LocalDate.of(2026, 3, 31))
                .balanced(true)
                .unitName("测试账套")
                .totalAssetEnd(new BigDecimal("10000.00"))
                .totalAssetBegin(new BigDecimal("8000.00"))
                .totalLiabilityAndEquityEnd(new BigDecimal("10000.00"))
                .totalLiabilityAndEquityBegin(new BigDecimal("8000.00"))
                .assetItems(List.of(
                        BalanceSheetItemResponse.builder()
                                .itemName("货币资金")
                                .rowNo(1)
                                .itemLevel(2)
                                .endAmount(new BigDecimal("10000.00"))
                                .beginAmount(new BigDecimal("8000.00"))
                                .build()
                ))
                .liabilityAndEquityItems(List.of(
                        BalanceSheetItemResponse.builder()
                                .itemName("实收资本")
                                .rowNo(30)
                                .itemLevel(2)
                                .endAmount(new BigDecimal("10000.00"))
                                .beginAmount(new BigDecimal("8000.00"))
                                .build()
                ))
                .build();

        when(reportApplicationService.getBalanceSheet(any())).thenReturn(responseData);

        MockHttpServletResponse response = new MockHttpServletResponse();
        BalanceSheetQueryRequest req = new BalanceSheetQueryRequest();
        req.setAccountingDate(LocalDate.of(2026, 3, 31));

        exportService.exportBalanceSheet(req, response);

        byte[] content = response.getContentAsByteArray();
        assertThat(content).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("资产负债表");
        }
    }

    @Test
    @DisplayName("测试利润表导出 Excel 正常生成")
    void testExportIncomeStatement() throws Exception {
        IncomeStatementResponse responseData = IncomeStatementResponse.builder()
                .unitName("测试账套")
                .periodDesc("2026年3月")
                .items(List.of(
                        IncomeStatementItemResponse.builder()
                                .itemName("一、营业收入")
                                .rowNo(1)
                                .itemLevel(1)
                                .currentAmount(new BigDecimal("50000.00"))
                                .yearTotalAmount(new BigDecimal("150000.00"))
                                .build()
                ))
                .kpi(IncomeStatementKpiResponse.builder()
                        .revenueMonth(new BigDecimal("50000.00"))
                        .operatingProfitMonth(new BigDecimal("20000.00"))
                        .netProfitMonth(new BigDecimal("15000.00"))
                        .build())
                .build();

        when(reportApplicationService.getIncomeStatement(any())).thenReturn(responseData);

        MockHttpServletResponse response = new MockHttpServletResponse();
        IncomeStatementQueryRequest req = new IncomeStatementQueryRequest();
        req.setYear(2026);
        req.setMonth(3);

        exportService.exportIncomeStatement(req, response);

        byte[] content = response.getContentAsByteArray();
        assertThat(content).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(1);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("利润表");
        }
    }

    @Test
    @DisplayName("测试辅助核算账簿导出 Excel 正常生成（透视矩阵+明细双 Sheet）")
    void testExportAuxiliaryLedger() throws Exception {
        AuxiliaryMatrixResponse matrix = AuxiliaryMatrixResponse.builder()
                .columns(List.of(AuxiliaryItemResponse.builder().auxCode("D01").auxName("研发部").build()))
                .rows(List.of(AuxiliaryMatrixRowResponse.builder()
                        .subjectCode("6602")
                        .subjectName("管理费用")
                        .subjectCategoryDesc("损益类")
                        .amounts(Map.of("D01", new BigDecimal("3000.00")))
                        .totalAmount(new BigDecimal("3000.00"))
                        .percentage(new BigDecimal("100.00"))
                        .build()))
                .columnTotals(Map.of("D01", new BigDecimal("3000.00")))
                .grandTotal(new BigDecimal("3000.00"))
                .amountMode("NET")
                .build();

        when(auxiliaryLedgerApplicationService.getAuxiliaryMatrix(any())).thenReturn(matrix);
        when(auxiliaryLedgerApplicationService.getAuxiliarySummary(any())).thenReturn(
                AuxiliarySummaryResponse.builder()
                        .debitAmount(new BigDecimal("3000.00"))
                        .creditAmount(BigDecimal.ZERO)
                        .build()
        );
        when(auxiliaryLedgerApplicationService.getAuxiliaryEntries(any())).thenReturn(
                PageResponse.<AuxiliaryEntryRecordResponse>builder()
                        .total(1L)
                        .list(List.of(
                                AuxiliaryEntryRecordResponse.builder()
                                        .accountingDate(LocalDate.of(2026, 3, 10))
                                        .voucherWord("记-001")
                                        .auxType("DEPARTMENT")
                                        .auxCode("D01")
                                        .auxName("研发部")
                                        .subjectCode("6602")
                                        .subjectName("管理费用")
                                        .summary("办公用品报销")
                                        .debitAmount(new BigDecimal("3000.00"))
                                        .creditAmount(BigDecimal.ZERO)
                                        .build()
                        ))
                        .build()
        );

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuxiliaryLedgerQueryRequest req = new AuxiliaryLedgerQueryRequest();
        req.setAuxType("DEPARTMENT");

        exportService.exportAuxiliaryLedger(req, response);

        byte[] content = response.getContentAsByteArray();
        assertThat(content).isNotEmpty();

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("各核算项汇总对比透视");
            assertThat(wb.getSheetAt(1).getSheetName()).isEqualTo("凭证分录流水明细");
        }
    }
}
