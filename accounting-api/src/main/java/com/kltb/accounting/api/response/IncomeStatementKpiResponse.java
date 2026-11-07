package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "利润表顶层财务核心 KPI 摘要")
public class IncomeStatementKpiResponse implements Serializable {

    @Schema(description = "本月营业收入", example = "8520000.00")
    private BigDecimal revenueMonth;

    @Schema(description = "本月营业利润", example = "2128400.00")
    private BigDecimal operatingProfitMonth;

    @Schema(description = "本月净利润", example = "1596300.00")
    private BigDecimal netProfitMonth;

    @Schema(description = "本月毛利率（%）", example = "35.3")
    private BigDecimal grossMarginRate;

    @Schema(description = "本年累计净利润", example = "4218500.00")
    private BigDecimal netProfitYearTotal;

    @Schema(description = "营收较同期增长率（%）", example = "12.3")
    private BigDecimal revenueYoY;

    @Schema(description = "净利润较同期增长率（%）", example = "9.1")
    private BigDecimal netProfitYoY;
}
