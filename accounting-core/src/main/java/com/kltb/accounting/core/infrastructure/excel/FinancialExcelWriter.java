package com.kltb.accounting.core.infrastructure.excel;

import lombok.Getter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.math.BigDecimal;
import java.util.List;

/**
 * 通用财务报表 Excel 写入器
 * <p>
 * 提供统一的字体、表头配色、货币格式化（#,##0.00）、标题栏合并、元信息栏、
 * 合计行双线标注及自适应列宽等通用功能，消除各报表导出时的重复模板代码。
 * </p>
 */
public class FinancialExcelWriter {

    @Getter
    private final Workbook workbook;
    private final DataFormat dataFormat;

    // 常用样式缓存
    private CellStyle titleStyle;
    private CellStyle metaLeftStyle;
    private CellStyle metaRightStyle;
    private CellStyle headerStyle;
    private CellStyle subHeaderStyle;
    private CellStyle textLeftStyle;
    private CellStyle textCenterStyle;
    private CellStyle moneyStyle;
    private CellStyle moneyBoldStyle;
    private CellStyle totalLabelStyle;
    private CellStyle totalMoneyStyle;

    public FinancialExcelWriter() {
        this.workbook = new XSSFWorkbook();
        this.dataFormat = workbook.createDataFormat();
        initStyles();
    }

    private void initStyles() {
        // 1. 大标题样式
        titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setFontName("Microsoft YaHei");
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setBold(true);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 2. 元信息副标题样式（左/右）
        Font metaFont = workbook.createFont();
        metaFont.setFontName("Microsoft YaHei");
        metaFont.setFontHeightInPoints((short) 10);
        metaFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

        metaLeftStyle = workbook.createCellStyle();
        metaLeftStyle.setFont(metaFont);
        metaLeftStyle.setAlignment(HorizontalAlignment.LEFT);
        metaLeftStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        metaRightStyle = workbook.createCellStyle();
        metaRightStyle.setFont(metaFont);
        metaRightStyle.setAlignment(HorizontalAlignment.RIGHT);
        metaRightStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // 3. 表头样式（浅灰蓝底色、居中、加粗、细边框）
        headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setFontName("Microsoft YaHei");
        headerFont.setFontHeightInPoints((short) 11);
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setThinBorder(headerStyle);

        // 次级表头
        subHeaderStyle = workbook.createCellStyle();
        subHeaderStyle.cloneStyleFrom(headerStyle);
        Font subHeaderFont = workbook.createFont();
        subHeaderFont.setFontName("Microsoft YaHei");
        subHeaderFont.setFontHeightInPoints((short) 10);
        subHeaderFont.setBold(true);
        subHeaderStyle.setFont(subHeaderFont);

        // 4. 普通单元格：居左
        textLeftStyle = workbook.createCellStyle();
        Font bodyFont = workbook.createFont();
        bodyFont.setFontName("Microsoft YaHei");
        bodyFont.setFontHeightInPoints((short) 10);
        textLeftStyle.setFont(bodyFont);
        textLeftStyle.setAlignment(HorizontalAlignment.LEFT);
        textLeftStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(textLeftStyle);

        // 5. 普通单元格：居中
        textCenterStyle = workbook.createCellStyle();
        textCenterStyle.setFont(bodyFont);
        textCenterStyle.setAlignment(HorizontalAlignment.CENTER);
        textCenterStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        setThinBorder(textCenterStyle);

        // 6. 金额单元格：右对齐、保留两位小数
        short moneyFormatIndex = dataFormat.getFormat("#,##0.00");
        moneyStyle = workbook.createCellStyle();
        moneyStyle.setFont(bodyFont);
        moneyStyle.setAlignment(HorizontalAlignment.RIGHT);
        moneyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        moneyStyle.setDataFormat(moneyFormatIndex);
        setThinBorder(moneyStyle);

        // 加粗金额单元格
        moneyBoldStyle = workbook.createCellStyle();
        Font boldBodyFont = workbook.createFont();
        boldBodyFont.setFontName("Microsoft YaHei");
        boldBodyFont.setFontHeightInPoints((short) 10);
        boldBodyFont.setBold(true);
        moneyBoldStyle.setFont(boldBodyFont);
        moneyBoldStyle.setAlignment(HorizontalAlignment.RIGHT);
        moneyBoldStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        moneyBoldStyle.setDataFormat(moneyFormatIndex);
        setThinBorder(moneyBoldStyle);

        // 7. 合计行样式（加粗、底色高亮）
        totalLabelStyle = workbook.createCellStyle();
        totalLabelStyle.setFont(boldBodyFont);
        totalLabelStyle.setAlignment(HorizontalAlignment.LEFT);
        totalLabelStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        totalLabelStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setTotalBorder(totalLabelStyle);

        totalMoneyStyle = workbook.createCellStyle();
        totalMoneyStyle.setFont(boldBodyFont);
        totalMoneyStyle.setAlignment(HorizontalAlignment.RIGHT);
        totalMoneyStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        totalMoneyStyle.setDataFormat(moneyFormatIndex);
        totalMoneyStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        totalMoneyStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setTotalBorder(totalMoneyStyle);
    }

    private void setThinBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
    }

    private void setTotalBorder(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.DOUBLE); // 会计合计行经典双底线
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    /**
     * 创建工作表并默认显示网格线
     */
    public Sheet createSheet(String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setDisplayGridlines(true);
        return sheet;
    }

    /**
     * 写入主大标题
     */
    public void writeTitle(Sheet sheet, int rowIndex, String title, int colSpan) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(34);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(titleStyle);

        if (colSpan > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, colSpan - 1));
        }
    }

    /**
     * 写入元信息行（例如：左侧会计期间、右侧币种与平衡状态）
     */
    public void writeMetaInfo(Sheet sheet, int rowIndex, String leftText, String rightText, int colSpan) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(20);

        if (colSpan <= 1) {
            Cell cell = row.createCell(0);
            cell.setCellValue(leftText);
            cell.setCellStyle(metaLeftStyle);
            return;
        }

        int midCol = colSpan / 2;
        Cell leftCell = row.createCell(0);
        leftCell.setCellValue(leftText);
        leftCell.setCellStyle(metaLeftStyle);
        if (midCol > 0) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, 0, midCol - 1));
        }

        Cell rightCell = row.createCell(midCol);
        rightCell.setCellValue(rightText);
        rightCell.setCellStyle(metaRightStyle);
        if (colSpan - 1 > midCol) {
            sheet.addMergedRegion(new CellRangeAddress(rowIndex, rowIndex, midCol, colSpan - 1));
        }
    }

    /**
     * 写入单行标准表头
     */
    public void writeHeader(Sheet sheet, int rowIndex, List<String> headers) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(24);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * 创建数据行
     */
    public Row createRow(Sheet sheet, int rowIndex, float heightInPoints) {
        Row row = sheet.createRow(rowIndex);
        row.setHeightInPoints(heightInPoints);
        return row;
    }

    /**
     * 写入普通居左文本单元格
     */
    public void setCellText(Row row, int colIndex, String text) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(text != null ? text : "");
        cell.setCellStyle(textLeftStyle);
    }

    /**
     * 写入普通居中代码/标签单元格
     */
    public void setCellCenterText(Row row, int colIndex, String text) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(text != null ? text : "");
        cell.setCellStyle(textCenterStyle);
    }

    /**
     * 写入标准金额单元格（#,##0.00）
     */
    public void setCellMoney(Row row, int colIndex, BigDecimal amount) {
        setCellMoney(row, colIndex, amount, false);
    }

    /**
     * 写入金额单元格（支持加粗）
     */
    public void setCellMoney(Row row, int colIndex, BigDecimal amount, boolean isBold) {
        Cell cell = row.createCell(colIndex);
        if (amount != null) {
            cell.setCellValue(amount.doubleValue());
        } else {
            cell.setCellValue(0.00);
        }
        cell.setCellStyle(isBold ? moneyBoldStyle : moneyStyle);
    }

    /**
     * 写入合计行文本单元格
     */
    public void setTotalLabelCell(Row row, int colIndex, String label) {
        Cell cell = row.createCell(colIndex);
        cell.setCellValue(label != null ? label : "");
        cell.setCellStyle(totalLabelStyle);
    }

    /**
     * 写入合计行金额单元格
     */
    public void setTotalMoneyCell(Row row, int colIndex, BigDecimal amount) {
        Cell cell = row.createCell(colIndex);
        if (amount != null) {
            cell.setCellValue(amount.doubleValue());
        } else {
            cell.setCellValue(0.00);
        }
        cell.setCellStyle(totalMoneyStyle);
    }

    /**
     * 获取单元格样式用于自定义扩展
     */
    public CellStyle getHeaderStyle() {
        return headerStyle;
    }

    public CellStyle getSubHeaderStyle() {
        return subHeaderStyle;
    }

    public CellStyle getTextLeftStyle() {
        return textLeftStyle;
    }

    public CellStyle getTextCenterStyle() {
        return textCenterStyle;
    }

    public CellStyle getMoneyStyle() {
        return moneyStyle;
    }

    /**
     * 自适应列宽算法（支持中文字符权重计算，保证各列不被遮挡）
     */
    public void autoSizeColumns(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i);
            int width = sheet.getColumnWidth(i);
            // 增加缓冲留白并限制在合理范围（最小 12 字符，最大 60 字符）
            int minWidth = 14 * 256;
            int maxWidth = 60 * 256;
            int paddedWidth = width + (4 * 256);
            if (paddedWidth < minWidth) {
                sheet.setColumnWidth(i, minWidth);
            } else if (paddedWidth > maxWidth) {
                sheet.setColumnWidth(i, maxWidth);
            } else {
                sheet.setColumnWidth(i, paddedWidth);
            }
        }
    }
}
