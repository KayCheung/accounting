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
@Schema(description = "科目总账响应报文")
public class GeneralLedgerResponse implements Serializable {

    @Schema(description = "期间起始日期", example = "2026-01-01")
    private LocalDate startDate;

    @Schema(description = "期间截止日期", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "符合条件的科目总数", example = "32")
    private Integer totalSubjectCount;

    @Schema(description = "期初借方合计")
    private BigDecimal totalBeginDebit;

    @Schema(description = "期初贷方合计")
    private BigDecimal totalBeginCredit;

    @Schema(description = "本期借方发生额合计")
    private BigDecimal totalPeriodDebit;

    @Schema(description = "本期贷方发生额合计")
    private BigDecimal totalPeriodCredit;

    @Schema(description = "期末借方合计")
    private BigDecimal totalEndDebit;

    @Schema(description = "期末贷方合计")
    private BigDecimal totalEndCredit;

    @Schema(description = "期末借贷是否平衡")
    private Boolean isBalanced;

    @Schema(description = "总账科目明细行列表")
    private List<GeneralLedgerItemResponse> items;
}
