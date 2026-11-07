package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.*;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.infrastructure.excel.ExcelExportHelper;
import com.kltb.accounting.core.infrastructure.excel.FinancialExcelWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 财务报表统一导出应用服务（Application Service）
 * <p>
 * 提供科目总账、科目明细账、资产负债表、利润表及辅助核算账簿的高复用性 Excel 导出。
 * 遵循国家会计准则格式与企业财务规范，输出标准 .xlsx 二进制文件流。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportExportService {

    private final FinancialReportApplicationService reportApplicationService;
    private final AuxiliaryLedgerApplicationService auxiliaryLedgerApplicationService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 1. 导出科目总分类账 Excel
     */
    public void exportGeneralLedger(GeneralLedgerQueryRequest request, HttpServletResponse response) {
        log.info("[REPORT-EXPORT] 导出科目总账: range={} ~ {}", request.getStartDate(), request.getEndDate());
        GeneralLedgerResponse data = reportApplicationService.getGeneralLedger(request);

        FinancialExcelWriter writer = new FinancialExcelWriter();
        Sheet sheet = writer.createSheet("科目总分类账");

        int colCount = 8;
        int rowIdx = 0;

        // 1. 标题
        writer.writeTitle(sheet, rowIdx++, "科目总分类账", colCount);

        // 2. 元信息
        String periodText = String.format("期间：%s 至 %s | 账套：主账套（人民币 CNY）",
                data.getStartDate() != null ? data.getStartDate().format(DATE_FMT) : "",
                data.getEndDate() != null ? data.getEndDate().format(DATE_FMT) : "");
        String statusText = Boolean.TRUE.equals(data.getIsBalanced()) ? "✓ 借贷试算平衡" : "✕ 借贷不平衡";
        writer.writeMetaInfo(sheet, rowIdx++, periodText, statusText, colCount);

        // 3. 双层复杂表头（总账三栏式）
        Row h1 = writer.createRow(sheet, rowIdx++, 24);
        Row h2 = writer.createRow(sheet, rowIdx++, 22);

        // 第 1 行表头
        writer.setCellCenterText(h1, 0, "科目编码");
        writer.setCellCenterText(h1, 1, "科目名称");
        writer.setCellCenterText(h1, 2, "级次");
        writer.setCellCenterText(h1, 3, "方向");
        writer.setCellCenterText(h1, 4, "期初余额");
        writer.setCellCenterText(h1, 5, "本期发生额");
        writer.setCellCenterText(h1, 6, ""); // 跨列占位
        writer.setCellCenterText(h1, 7, "期末余额");

        // 样式覆盖表头
        for (int c = 0; c < colCount; c++) {
            h1.getCell(c).setCellStyle(writer.getHeaderStyle());
        }

        // 第 2 行表头
        for (int c = 0; c < colCount; c++) {
            writer.setCellCenterText(h2, c, "");
            h2.getCell(c).setCellStyle(writer.getSubHeaderStyle());
        }
        h2.getCell(5).setCellValue("借方发生额");
        h2.getCell(6).setCellValue("贷方发生额");

        // 合并第 1 行与第 2 行表头跨行/跨列
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0)); // 科目编码
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 1, 1)); // 科目名称
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 2, 2)); // 级次
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 3, 3)); // 方向
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 4, 4)); // 期初余额
        sheet.addMergedRegion(new CellRangeAddress(2, 2, 5, 6)); // 本期发生额跨2列
        sheet.addMergedRegion(new CellRangeAddress(2, 3, 7, 7)); // 期末余额

        // 4. 明细数据行
        if (data.getItems() != null) {
            for (GeneralLedgerItemResponse item : data.getItems()) {
                Row row = writer.createRow(sheet, rowIdx++, 20);

                // 科目编码
                writer.setCellCenterText(row, 0, item.getSubjectCode());

                // 科目名称（按级次缩进）
                int level = item.getSubjectLevel() != null ? item.getSubjectLevel() : 1;
                String indent = "  ".repeat(Math.max(0, level - 1));
                writer.setCellText(row, 1, indent + (item.getSubjectName() != null ? item.getSubjectName() : ""));

                // 级次
                writer.setCellCenterText(row, 2, level + "级");

                // 方向
                writer.setCellCenterText(row, 3, item.getBalanceDirectionDesc() != null ? item.getBalanceDirectionDesc() : "平");

                // 期初余额、借方发生额、贷方发生额、期末余额
                writer.setCellMoney(row, 4, item.getBeginBalance());
                writer.setCellMoney(row, 5, item.getDebitAmount());
                writer.setCellMoney(row, 6, item.getCreditAmount());
                writer.setCellMoney(row, 7, item.getEndBalance());
            }
        }

        // 5. 底部合计汇总行
        Row totalRow = writer.createRow(sheet, rowIdx++, 24);
        writer.setTotalLabelCell(totalRow, 0, "合计 (汇总试算)");
        writer.setTotalLabelCell(totalRow, 1, "");
        writer.setTotalLabelCell(totalRow, 2, "");
        writer.setTotalLabelCell(totalRow, 3, "");
        writer.setTotalMoneyCell(totalRow, 4, null); // 期初合计可根据需要
        writer.setTotalMoneyCell(totalRow, 5, data.getTotalPeriodDebit());
        writer.setTotalMoneyCell(totalRow, 6, data.getTotalPeriodCredit());
        writer.setTotalMoneyCell(totalRow, 7, null);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 3));

        writer.autoSizeColumns(sheet, colCount);

        String fileName = String.format("科目总分类账_%s_%s.xlsx",
                data.getStartDate() != null ? data.getStartDate().format(DATE_FMT) : "",
                data.getEndDate() != null ? data.getEndDate().format(DATE_FMT) : "");
        ExcelExportHelper.export(response, fileName, writer);
    }

    /**
     * 2. 导出科目明细账 Excel
     */
    public void exportSubsidiaryLedger(SubsidiaryLedgerQueryRequest request, HttpServletResponse response) {
        log.info("[REPORT-EXPORT] 导出科目明细账: subject={}, range={} ~ {}",
                request.getSubjectCode(), request.getStartDate(), request.getEndDate());
        SubsidiaryLedgerResponse data = reportApplicationService.getSubsidiaryLedger(request);

        FinancialExcelWriter writer = new FinancialExcelWriter();
        Sheet sheet = writer.createSheet("科目明细账");

        int colCount = 7;
        int rowIdx = 0;

        // 1. 标题
        writer.writeTitle(sheet, rowIdx++, "科目明细分类账", colCount);

        // 2. 元信息
        String subjectInfo = String.format("科目：%s %s | 级次：%s级",
                data.getSubjectCode() != null ? data.getSubjectCode() : "",
                data.getSubjectName() != null ? data.getSubjectName() : "",
                data.getSubjectLevel() != null ? data.getSubjectLevel() : 1);
        String periodInfo = String.format("期间：%s 至 %s | 方向：%s | 期末余额：%s 元",
                data.getStartDate() != null ? data.getStartDate().format(DATE_FMT) : "",
                data.getEndDate() != null ? data.getEndDate().format(DATE_FMT) : "",
                data.getBalanceDirectionDesc() != null ? data.getBalanceDirectionDesc() : "平",
                data.getEndBalance() != null ? data.getEndBalance().toPlainString() : "0.00");
        writer.writeMetaInfo(sheet, rowIdx++, subjectInfo, periodInfo, colCount);

        // 3. 表头
        List<String> headers = List.of("记账日期", "凭证字号", "业务摘要", "借方发生额", "贷方发生额", "方向", "余额");
        writer.writeHeader(sheet, rowIdx++, headers);

        // 4. 数据行
        if (data.getItems() != null) {
            for (SubsidiaryLedgerItemResponse item : data.getItems()) {
                Row row = writer.createRow(sheet, rowIdx++, 20);

                String dateStr = item.getAccountingDate() != null ? item.getAccountingDate().format(DATE_FMT) : "";
                String voucherNo = item.getVoucherNo() != null ? item.getVoucherNo() : "";
                String summary = item.getSummary() != null ? item.getSummary() : "";
                String dirDesc = item.getBalanceDirectionDesc() != null ? item.getBalanceDirectionDesc() : "平";

                boolean isSummaryRow = "PERIOD_TOTAL".equalsIgnoreCase(item.getRowType()) ||
                                      "YEAR_TOTAL".equalsIgnoreCase(item.getRowType());

                if (isSummaryRow) {
                    writer.setTotalLabelCell(row, 0, dateStr);
                    writer.setTotalLabelCell(row, 1, voucherNo);
                    writer.setTotalLabelCell(row, 2, summary);
                    writer.setTotalMoneyCell(row, 3, item.getDebitAmount());
                    writer.setTotalMoneyCell(row, 4, item.getCreditAmount());
                    writer.setTotalLabelCell(row, 5, dirDesc);
                    writer.setTotalMoneyCell(row, 6, item.getBalance());
                } else {
                    writer.setCellCenterText(row, 0, dateStr);
                    writer.setCellCenterText(row, 1, voucherNo);
                    writer.setCellText(row, 2, summary);
                    writer.setCellMoney(row, 3, item.getDebitAmount());
                    writer.setCellMoney(row, 4, item.getCreditAmount());
                    writer.setCellCenterText(row, 5, dirDesc);
                    writer.setCellMoney(row, 6, item.getBalance());
                }
            }
        }

        writer.autoSizeColumns(sheet, colCount);

        String fileName = String.format("科目明细账_%s_%s_%s.xlsx",
                data.getSubjectCode() != null ? data.getSubjectCode() : "科目",
                data.getStartDate() != null ? data.getStartDate().format(DATE_FMT) : "",
                data.getEndDate() != null ? data.getEndDate().format(DATE_FMT) : "");
        ExcelExportHelper.export(response, fileName, writer);
    }

    /**
     * 3. 导出标准资产负债表 Excel（左右经典双栏并列）
     */
    public void exportBalanceSheet(BalanceSheetQueryRequest request, HttpServletResponse response) {
        log.info("[REPORT-EXPORT] 导出资产负债表: date={}", request.getAccountingDate());
        BalanceSheetResponse data = reportApplicationService.getBalanceSheet(request);

        FinancialExcelWriter writer = new FinancialExcelWriter();
        Sheet sheet = writer.createSheet("资产负债表");

        int colCount = 8;
        int rowIdx = 0;

        // 1. 标题
        writer.writeTitle(sheet, rowIdx++, "资 产 负 债 表", colCount);

        // 2. 元信息
        String leftMeta = String.format("编制单位：%s | 报表日期：%s",
                data.getUnitName() != null ? data.getUnitName() : "主账套",
                data.getAccountingDate() != null ? data.getAccountingDate().format(DATE_FMT) : "");
        String rightMeta = String.format("货币单位：人民币元 | %s",
                Boolean.TRUE.equals(data.getBalanced()) ? "✓ 借贷平衡" : "✕ 差额: " + data.getDiffAmount());
        writer.writeMetaInfo(sheet, rowIdx++, leftMeta, rightMeta, colCount);

        // 3. 表头（双栏并排：左侧资产，右侧负债与所有者权益）
        List<String> headers = List.of(
                "资产项目", "行次", "期末余额", "年初余额",
                "负债和所有者权益", "行次", "期末余额", "年初余额"
        );
        writer.writeHeader(sheet, rowIdx++, headers);

        // 4. 数据行并列输出
        List<BalanceSheetItemResponse> leftList = data.getAssetItems() != null ? data.getAssetItems() : List.of();
        List<BalanceSheetItemResponse> rightList = data.getLiabilityAndEquityItems() != null ? data.getLiabilityAndEquityItems() : List.of();
        int maxRows = Math.max(leftList.size(), rightList.size());

        for (int i = 0; i < maxRows; i++) {
            Row row = writer.createRow(sheet, rowIdx++, 20);

            // 左侧：资产
            if (i < leftList.size()) {
                BalanceSheetItemResponse left = leftList.get(i);
                boolean isHeader = left.getItemLevel() != null && left.getItemLevel() == 1;
                String indent = (left.getItemLevel() != null && left.getItemLevel() > 1) ? "  " : "";
                writer.setCellText(row, 0, indent + (left.getItemName() != null ? left.getItemName() : ""));
                writer.setCellCenterText(row, 1, left.getRowNo() != null ? String.valueOf(left.getRowNo()) : "");
                writer.setCellMoney(row, 2, left.getEndAmount(), isHeader);
                writer.setCellMoney(row, 3, left.getBeginAmount(), isHeader);
            } else {
                writer.setCellText(row, 0, "");
                writer.setCellCenterText(row, 1, "");
                writer.setCellMoney(row, 2, null);
                writer.setCellMoney(row, 3, null);
            }

            // 右侧：负债和所有者权益
            if (i < rightList.size()) {
                BalanceSheetItemResponse right = rightList.get(i);
                boolean isHeader = right.getItemLevel() != null && right.getItemLevel() == 1;
                String indent = (right.getItemLevel() != null && right.getItemLevel() > 1) ? "  " : "";
                writer.setCellText(row, 4, indent + (right.getItemName() != null ? right.getItemName() : ""));
                writer.setCellCenterText(row, 5, right.getRowNo() != null ? String.valueOf(right.getRowNo()) : "");
                writer.setCellMoney(row, 6, right.getEndAmount(), isHeader);
                writer.setCellMoney(row, 7, right.getBeginAmount(), isHeader);
            } else {
                writer.setCellText(row, 4, "");
                writer.setCellCenterText(row, 5, "");
                writer.setCellMoney(row, 6, null);
                writer.setCellMoney(row, 7, null);
            }
        }

        // 5. 底部总计行（资产总计 vs 负债及所有者权益总计）
        Row totalRow = writer.createRow(sheet, rowIdx++, 24);
        writer.setTotalLabelCell(totalRow, 0, "资产总计");
        writer.setTotalLabelCell(totalRow, 1, "");
        writer.setTotalMoneyCell(totalRow, 2, data.getTotalAssetEnd());
        writer.setTotalMoneyCell(totalRow, 3, data.getTotalAssetBegin());

        writer.setTotalLabelCell(totalRow, 4, "负债及所有者权益总计");
        writer.setTotalLabelCell(totalRow, 5, "");
        writer.setTotalMoneyCell(totalRow, 6, data.getTotalLiabilityAndEquityEnd());
        writer.setTotalMoneyCell(totalRow, 7, data.getTotalLiabilityAndEquityBegin());

        writer.autoSizeColumns(sheet, colCount);

        String fileName = String.format("资产负债表_%s.xlsx",
                data.getAccountingDate() != null ? data.getAccountingDate().format(DATE_FMT) : "");
        ExcelExportHelper.export(response, fileName, writer);
    }

    /**
     * 4. 导出多步式利润表 Excel
     */
    public void exportIncomeStatement(IncomeStatementQueryRequest request, HttpServletResponse response) {
        log.info("[REPORT-EXPORT] 导出利润表: year={}, month={}", request.getYear(), request.getMonth());
        IncomeStatementResponse data = reportApplicationService.getIncomeStatement(request);

        FinancialExcelWriter writer = new FinancialExcelWriter();
        Sheet sheet = writer.createSheet("利润表");

        int colCount = 5;
        int rowIdx = 0;

        // 1. 标题
        writer.writeTitle(sheet, rowIdx++, "利 润 表", colCount);

        // 2. 元信息
        String leftMeta = String.format("编制单位：%s | 会计期间：%s",
                data.getUnitName() != null ? data.getUnitName() : "主账套",
                data.getPeriodDesc() != null ? data.getPeriodDesc() : (request.getYear() + "年" + request.getMonth() + "月"));
        String rightMeta = "货币单位：人民币元 (CNY)";
        writer.writeMetaInfo(sheet, rowIdx++, leftMeta, rightMeta, colCount);

        // 3. 表头
        List<String> headers = List.of("项目名称", "行次", "本月/本期金额", "本年累计金额", "上年同期金额");
        writer.writeHeader(sheet, rowIdx++, headers);

        // 4. 明细行
        if (data.getItems() != null) {
            for (IncomeStatementItemResponse item : data.getItems()) {
                Row row = writer.createRow(sheet, rowIdx++, 20);

                int level = item.getItemLevel() != null ? item.getItemLevel() : 2;
                boolean isKeyItem = level == 1 || level == 3;
                String indent = (level == 2) ? "    " : "";

                writer.setCellText(row, 0, indent + (item.getItemName() != null ? item.getItemName() : ""));
                writer.setCellCenterText(row, 1, item.getRowNo() != null ? String.valueOf(item.getRowNo()) : "");
                writer.setCellMoney(row, 2, item.getCurrentAmount(), isKeyItem);
                writer.setCellMoney(row, 3, item.getYearTotalAmount(), isKeyItem);
                writer.setCellMoney(row, 4, item.getCompareAmount(), isKeyItem);
            }
        }

        // 5. 顶层 KPI 汇总栏
        if (data.getKpi() != null) {
            rowIdx++;
            Row kpiRow = writer.createRow(sheet, rowIdx++, 22);
            writer.setTotalLabelCell(kpiRow, 0, "核心经营指标概览");
            writer.setTotalLabelCell(kpiRow, 1, "营业收入: " + (data.getKpi().getRevenueMonth() != null ? data.getKpi().getRevenueMonth().toPlainString() : "0.00"));
            writer.setTotalLabelCell(kpiRow, 2, "营业利润: " + (data.getKpi().getOperatingProfitMonth() != null ? data.getKpi().getOperatingProfitMonth().toPlainString() : "0.00"));
            writer.setTotalLabelCell(kpiRow, 3, "净利润: " + (data.getKpi().getNetProfitMonth() != null ? data.getKpi().getNetProfitMonth().toPlainString() : "0.00"));
            writer.setTotalLabelCell(kpiRow, 4, "累计净利润: " + (data.getKpi().getNetProfitYearTotal() != null ? data.getKpi().getNetProfitYearTotal().toPlainString() : "0.00"));
        }

        writer.autoSizeColumns(sheet, colCount);

        String fileName = String.format("利润表_%d年%d月.xlsx", request.getYear(), request.getMonth());
        ExcelExportHelper.export(response, fileName, writer);
    }

    /**
     * 5. 导出辅助核算账簿 Excel（Sheet1: 透视对比矩阵，Sheet2: 凭证分录明细）
     */
    public void exportAuxiliaryLedger(AuxiliaryLedgerQueryRequest request, HttpServletResponse response) {
        log.info("[REPORT-EXPORT] 导出辅助核算账簿: type={}, item={}", request.getAuxType(), request.getAuxCode());

        // 查询透视矩阵与明细流水
        AuxiliaryMatrixResponse matrix = auxiliaryLedgerApplicationService.getAuxiliaryMatrix(request);
        AuxiliarySummaryResponse summary = auxiliaryLedgerApplicationService.getAuxiliarySummary(request);

        // 获取全部明细流水（单次导出默认最多 5000 条明细）
        AuxiliaryLedgerQueryRequest pageReq = new AuxiliaryLedgerQueryRequest();
        pageReq.setAuxType(request.getAuxType());
        pageReq.setAuxCode(request.getAuxCode());
        pageReq.setStartDate(request.getStartDate());
        pageReq.setEndDate(request.getEndDate());
        pageReq.setVoucherStatus(request.getVoucherStatus());
        pageReq.setPageNo(1);
        pageReq.setPageSize(5000);
        PageResponse<AuxiliaryEntryRecordResponse> entries = auxiliaryLedgerApplicationService.getAuxiliaryEntries(pageReq);

        FinancialExcelWriter writer = new FinancialExcelWriter();

        // ========== Sheet 1: 交叉对比汇总矩阵 ==========
        Sheet matrixSheet = writer.createSheet("各核算项汇总对比透视");
        int mRowIdx = 0;

        List<AuxiliaryItemResponse> cols = matrix.getColumns() != null ? matrix.getColumns() : List.of();
        int mColCount = 3 + cols.size() + 2; // 科目编码、科目名称、账类 + N个核算项 + 合计 + 占比

        writer.writeTitle(matrixSheet, mRowIdx++, "辅助核算交叉汇总透视表", mColCount);

        String mMetaLeft = String.format("核算类别：%s | 统计口径：%s",
                request.getAuxType() != null ? request.getAuxType() : "全部",
                "NET".equalsIgnoreCase(matrix.getAmountMode()) ? "成本净额口径(借-贷)" : "发生额口径(借+贷)");
        String mMetaRight = String.format("期间：%s 至 %s | 全表总计：%s 元",
                request.getStartDate() != null ? request.getStartDate().format(DATE_FMT) : "不限",
                request.getEndDate() != null ? request.getEndDate().format(DATE_FMT) : "不限",
                matrix.getGrandTotal() != null ? matrix.getGrandTotal().toPlainString() : "0.00");
        writer.writeMetaInfo(matrixSheet, mRowIdx++, mMetaLeft, mMetaRight, mColCount);

        // 表头
        List<String> mHeaders = new ArrayList<>();
        mHeaders.add("科目编码");
        mHeaders.add("科目名称");
        mHeaders.add("账类");
        for (AuxiliaryItemResponse col : cols) {
            mHeaders.add(col.getAuxName() != null ? col.getAuxName() : col.getAuxCode());
        }
        mHeaders.add("合计");
        mHeaders.add("占比(%)");
        writer.writeHeader(matrixSheet, mRowIdx++, mHeaders);

        // 矩阵行数据
        if (matrix.getRows() != null) {
            for (AuxiliaryMatrixRowResponse rowData : matrix.getRows()) {
                Row r = writer.createRow(matrixSheet, mRowIdx++, 20);
                writer.setCellCenterText(r, 0, rowData.getSubjectCode());
                writer.setCellText(r, 1, rowData.getSubjectName());
                writer.setCellCenterText(r, 2, rowData.getSubjectCategoryDesc());

                int cIdx = 3;
                Map<String, BigDecimal> amounts = rowData.getAmounts();
                for (AuxiliaryItemResponse col : cols) {
                    BigDecimal val = amounts != null ? amounts.get(col.getAuxCode()) : BigDecimal.ZERO;
                    writer.setCellMoney(r, cIdx++, val);
                }
                writer.setCellMoney(r, cIdx++, rowData.getTotalAmount(), true);
                writer.setCellCenterText(r, cIdx, rowData.getPercentage() != null ? (rowData.getPercentage().toPlainString() + "%") : "0.00%");
            }
        }

        // 列合计行
        Row mTotalRow = writer.createRow(matrixSheet, mRowIdx++, 24);
        writer.setTotalLabelCell(mTotalRow, 0, "列合计");
        writer.setTotalLabelCell(mTotalRow, 1, "");
        writer.setTotalLabelCell(mTotalRow, 2, "");
        matrixSheet.addMergedRegion(new CellRangeAddress(mRowIdx - 1, mRowIdx - 1, 0, 2));

        int colIdx = 3;
        Map<String, BigDecimal> colTotals = matrix.getColumnTotals();
        for (AuxiliaryItemResponse col : cols) {
            BigDecimal cTot = colTotals != null ? colTotals.get(col.getAuxCode()) : BigDecimal.ZERO;
            writer.setTotalMoneyCell(mTotalRow, colIdx++, cTot);
        }
        writer.setTotalMoneyCell(mTotalRow, colIdx++, matrix.getGrandTotal());
        writer.setTotalLabelCell(mTotalRow, colIdx, "100.00%");

        writer.autoSizeColumns(matrixSheet, mColCount);

        // ========== Sheet 2: 辅助核算凭证流水明细 ==========
        Sheet entrySheet = writer.createSheet("凭证分录流水明细");
        int eRowIdx = 0;
        int eColCount = 9;

        writer.writeTitle(entrySheet, eRowIdx++, "辅助核算凭证分录明细表", eColCount);

        List<String> eHeaders = List.of(
                "会计日期", "财务凭证号", "核算类型", "核算项编码", "核算项名称",
                "会计科目", "业务摘要", "借方发生额", "贷方发生额"
        );
        writer.writeHeader(entrySheet, eRowIdx++, eHeaders);

        if (entries != null && entries.getList() != null) {
            for (AuxiliaryEntryRecordResponse entry : entries.getList()) {
                Row row = writer.createRow(entrySheet, eRowIdx++, 20);
                String dateStr = entry.getAccountingDate() != null ? entry.getAccountingDate().format(DATE_FMT) : "";
                writer.setCellCenterText(row, 0, dateStr);
                writer.setCellCenterText(row, 1, entry.getVoucherWord() != null ? entry.getVoucherWord() : entry.getVoucherNo());
                writer.setCellCenterText(row, 2, entry.getAuxType());
                writer.setCellCenterText(row, 3, entry.getAuxCode());
                writer.setCellText(row, 4, entry.getAuxName());
                writer.setCellText(row, 5, String.format("[%s] %s", entry.getSubjectCode(), entry.getSubjectName()));
                writer.setCellText(row, 6, entry.getSummary());
                writer.setCellMoney(row, 7, entry.getDebitAmount());
                writer.setCellMoney(row, 8, entry.getCreditAmount());
            }
        }

        // 明细合计
        if (summary != null) {
            Row eTotalRow = writer.createRow(entrySheet, eRowIdx++, 24);
            writer.setTotalLabelCell(eTotalRow, 0, "发生额合计");
            for (int i = 1; i <= 6; i++) {
                writer.setTotalLabelCell(eTotalRow, i, "");
            }
            entrySheet.addMergedRegion(new CellRangeAddress(eRowIdx - 1, eRowIdx - 1, 0, 6));
            writer.setTotalMoneyCell(eTotalRow, 7, summary.getDebitAmount());
            writer.setTotalMoneyCell(eTotalRow, 8, summary.getCreditAmount());
        }

        writer.autoSizeColumns(entrySheet, eColCount);

        String fileName = String.format("辅助核算账簿_%s_%s.xlsx",
                request.getAuxType() != null ? request.getAuxType() : "ALL",
                request.getStartDate() != null ? request.getStartDate().format(DATE_FMT) : "");
        ExcelExportHelper.export(response, fileName, writer);
    }
}
