package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
@Schema(description = "利润表查询请求")
public class IncomeStatementQueryRequest implements Serializable {

    @NotNull(message = "会计年度不能为空")
    @Schema(description = "会计年度（如 2026）", example = "2026")
    private Integer year;

    @NotNull(message = "报告期月份不能为空")
    @Min(value = 1, message = "月份最小为 1")
    @Max(value = 12, message = "月份最大为 12")
    @Schema(description = "报告期月份（1-12，表示1至该月累计）", example = "3")
    private Integer month;

    @Schema(description = "对比方式：1-上年同期, 2-上月环比, 0-不对比", example = "1")
    private Integer compareType = 1;
}
