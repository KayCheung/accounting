package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Schema(description = "利润表查询请求")
public class IncomeStatementQueryRequest implements Serializable {

    @Schema(description = "会计年度（如 2026，标准月度模式使用）", example = "2026")
    private Integer year;

    @Schema(description = "报告期月份（1-12，表示1至该月累计，标准月度模式使用）", example = "3")
    private Integer month;

    @Schema(description = "对比方式：1-上年同期, 2-上月环比, 0-不对比", example = "1")
    private Integer compareType = 1;

    // 自定义期间扩展
    @Schema(description = "报告期间起始日期（自定义期间模式使用，如 2026-01-01）", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "报告期间截止日期（自定义期间模式使用，如 2026-03-31）", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "对比期间起始日期（自定义期间模式使用，如 2025-01-01）", example = "2025-01-01")
    private LocalDate compareStartDate;

    @Schema(description = "对比期间截止日期（自定义期间模式使用，如 2025-03-31）", example = "2025-03-31")
    private LocalDate compareEndDate;
}
