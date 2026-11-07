package com.kltb.accounting.core.infrastructure.excel;

import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Excel 导出 HTTP 响应通用工具类
 * <p>
 * 统一封装导出响应头设置、文件名 UTF-8 URL 编码、二进制流写入及异常关闭逻辑，
 * 避免各 Controller/Service 重复编写底层流处理代码。
 * </p>
 */
@Slf4j
public final class ExcelExportHelper {

    private ExcelExportHelper() {
        // 工具类禁止实例化
    }

    /**
     * 将 Workbook 输出到 HttpServletResponse 响应流中供浏览器下载
     *
     * @param response     HTTP 响应对象
     * @param fileName     下载的文件名（如：科目总账_20261022.xlsx）
     * @param excelWriter  财务报表构建器
     */
    public static void export(HttpServletResponse response, String fileName, FinancialExcelWriter excelWriter) {
        if (excelWriter == null) {
            throw new IllegalArgumentException("导出的 Excel 构建器不能为空");
        }
        export(response, fileName, excelWriter.getWorkbook());
    }

    /**
     * 将 Workbook 输出到 HttpServletResponse 响应流中供浏览器下载
     *
     * @param response  HTTP 响应对象
     * @param fileName  下载的文件名
     * @param workbook  POI 工作簿
     */
    public static void export(HttpServletResponse response, String fileName, Workbook workbook) {
        if (response == null) {
            throw new IllegalArgumentException("HTTP 响应对象不能为空");
        }
        if (workbook == null) {
            throw new IllegalArgumentException("导出的工作簿不能为空");
        }

        try {
            // 设置标准 Excel (OOXML .xlsx) 的 Content-Type 与编码
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            // 格式化文件名，兼容现代浏览器标准 RFC 5987 / RFC 6266
            String safeFileName = fileName.endsWith(".xlsx") ? fileName : (fileName + ".xlsx");
            String encodedFileName = URLEncoder.encode(safeFileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);

            try (OutputStream out = response.getOutputStream()) {
                workbook.write(out);
                out.flush();
            }
        } catch (IOException e) {
            log.error("导出 Excel 文件失败: fileName={}", fileName, e);
            throw new RuntimeException("导出 Excel 文件失败: " + e.getMessage(), e);
        } finally {
            try {
                workbook.close();
            } catch (IOException e) {
                log.warn("关闭 Excel 工作簿异常", e);
            }
        }
    }
}
