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
@Schema(description = "利润表行项目")
public class IncomeStatementItemResponse implements Serializable {

    @Schema(description = "行次", example = "1")
    private Integer rowNo;

    @Schema(description = "项目名称", example = "一、营业收入")
    private String itemName;

    @Schema(description = "项目级别：1-主要项/分类, 2-明细减项/加项, 3-合计/利润总额", example = "1")
    private Integer itemLevel;

    @Schema(description = "本月/本期金额", example = "8520000.00")
    private BigDecimal currentAmount;

    @Schema(description = "本年累计金额", example = "24500000.00")
    private BigDecimal yearTotalAmount;

    @Schema(description = "对比期金额（上年同期或上月）", example = "7580000.00")
    private BigDecimal compareAmount;

    @Schema(description = "同比增长率（%）", example = "12.4")
    private BigDecimal growthRate;

    @Schema(description = "归集到底层具体科目明细清单")
    private java.util.List<IncomeStatementSubjectDetailResponse> detailSubjects;
}
