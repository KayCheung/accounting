package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "科目明细账流水行")
public class SubsidiaryLedgerItemResponse implements Serializable {

    @Schema(description = "行类型：BEGIN_BALANCE(期初余额), ENTRY(分录明细), PERIOD_TOTAL(本期合计), YEAR_TOTAL(本年累计)", example = "ENTRY")
    private String rowType;

    @Schema(description = "会计日期", example = "2026-03-15")
    private LocalDate accountingDate;

    @Schema(description = "记账凭证号", example = "VC202603150001")
    private String voucherNo;

    @Schema(description = "分录流水号", example = "ENT20260315000101")
    private String entryId;

    @Schema(description = "业务摘要", example = "销售商品收到客户银行汇票")
    private String summary;

    @Schema(description = "借方发生额", example = "500000.00")
    private BigDecimal debitAmount;

    @Schema(description = "贷方发生额", example = "0.00")
    private BigDecimal creditAmount;

    @Schema(description = "方向：1-借, 2-贷, 0-平", example = "1")
    private Integer balanceDirection;

    @Schema(description = "余额方向描述：借 / 贷 / 平", example = "借")
    private String balanceDirectionDesc;

    @Schema(description = "轧差后动态余额", example = "13356430.00")
    private BigDecimal balance;
}
