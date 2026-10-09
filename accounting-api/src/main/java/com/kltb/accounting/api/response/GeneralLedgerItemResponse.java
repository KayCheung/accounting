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
@Schema(description = "科目总账行项目")
public class GeneralLedgerItemResponse implements Serializable {

    @Schema(description = "科目编码", example = "1002")
    private String subjectCode;

    @Schema(description = "科目名称", example = "银行存款")
    private String subjectName;

    @Schema(description = "科目级次", example = "1")
    private Integer subjectLevel;

    @Schema(description = "余额方向：1-借, 2-贷", example = "1")
    private Integer balanceDirection;

    @Schema(description = "余额方向描述：借 / 贷 / 平", example = "借")
    private String balanceDirectionDesc;

    @Schema(description = "期初余额", example = "12856430.00")
    private BigDecimal beginBalance;

    @Schema(description = "本期借方发生额", example = "3521000.00")
    private BigDecimal debitAmount;

    @Schema(description = "本期贷方发生额", example = "2108560.00")
    private BigDecimal creditAmount;

    @Schema(description = "期末余额", example = "14268870.00")
    private BigDecimal endBalance;
}
