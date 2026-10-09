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
@Schema(description = "科目明细账响应报文")
public class SubsidiaryLedgerResponse implements Serializable {

    @Schema(description = "科目编码", example = "1002")
    private String subjectCode;

    @Schema(description = "科目名称", example = "银行存款")
    private String subjectName;

    @Schema(description = "科目级次", example = "1")
    private Integer subjectLevel;

    @Schema(description = "常规余额方向", example = "借")
    private String balanceDirectionDesc;

    @Schema(description = "期间起始日期", example = "2026-03-01")
    private LocalDate startDate;

    @Schema(description = "期间截止日期", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "期初余额", example = "12856430.00")
    private BigDecimal beginBalance;

    @Schema(description = "期末余额", example = "14268870.00")
    private BigDecimal endBalance;

    @Schema(description = "本期借方合计", example = "3521000.00")
    private BigDecimal totalDebitAmount;

    @Schema(description = "本期贷方合计", example = "2108560.00")
    private BigDecimal totalCreditAmount;

    @Schema(description = "明细账流水行（含期初余额、分录明细、本期合计）")
    private List<SubsidiaryLedgerItemResponse> items;
}
