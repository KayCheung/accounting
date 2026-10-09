package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产负债表响应报文")
public class BalanceSheetResponse implements Serializable {

    @Schema(description = "报告期截止日期", example = "2026-03-31")
    private LocalDate accountingDate;

    @Schema(description = "对比年初日期", example = "2026-01-01")
    private LocalDate compareDate;

    @Schema(description = "编制单位", example = "智能账务核心企业")
    private String unitName;

    @Schema(description = "货币币种", example = "CNY")
    private String currency;

    @Schema(description = "资产负债是否平衡 (资产总计 == 负债及所有者权益总计)")
    private Boolean balanced;

    @Schema(description = "试算差额")
    private BigDecimal diffAmount;

    @Schema(description = "资产总计期末数")
    private BigDecimal totalAssetEnd;

    @Schema(description = "资产总计年初数")
    private BigDecimal totalAssetBegin;

    @Schema(description = "负债及所有者权益总计期末数")
    private BigDecimal totalLiabilityAndEquityEnd;

    @Schema(description = "负债及所有者权益总计年初数")
    private BigDecimal totalLiabilityAndEquityBegin;

    @Schema(description = "左侧资产方项目列表")
    private List<BalanceSheetItemResponse> assetItems;

    @Schema(description = "右侧负债及所有者权益方项目列表")
    private List<BalanceSheetItemResponse> liabilityAndEquityItems;
}
