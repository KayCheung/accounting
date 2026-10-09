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
@Schema(description = "资产负债表行项目")
public class BalanceSheetItemResponse implements Serializable {

    @Schema(description = "行次", example = "1")
    private Integer rowNo;

    @Schema(description = "项目名称", example = "货币资金")
    private String itemName;

    @Schema(description = "项目级次：1-一级大类/小计, 2-明细行项目", example = "2")
    private Integer itemLevel;

    @Schema(description = "科目代码归集说明", example = "1001,1002,1012")
    private String subjectCodes;

    @Schema(description = "期末余额", example = "14358490.00")
    private BigDecimal endAmount;

    @Schema(description = "年初余额", example = "12941750.00")
    private BigDecimal beginAmount;
}
