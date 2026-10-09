package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "利润表响应报文")
public class IncomeStatementResponse implements Serializable {

    @Schema(description = "报告期间说明", example = "2026年1-3月")
    private String periodDesc;

    @Schema(description = "报告期截止日期", example = "2026-03-31")
    private LocalDate accountingDate;

    @Schema(description = "货币币种", example = "CNY")
    private String currency;

    @Schema(description = "编制单位", example = "智能账务核心示范租户")
    private String unitName;

    @Schema(description = "顶层核心财务 KPI")
    private IncomeStatementKpiResponse kpi;

    @Schema(description = "多步式利润表明细项目列表")
    private List<IncomeStatementItemResponse> items;
}
