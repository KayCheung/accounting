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
@Schema(description = "资产负债表行项目归集明细科目")
public class BalanceSheetSubjectDetailResponse implements Serializable {

    @Schema(description = "科目编码", example = "100201")
    private String subjectCode;

    @Schema(description = "科目名称", example = "基本户存款")
    private String subjectName;

    @Schema(description = "期末余额", example = "10000.00")
    private BigDecimal endAmount;

    @Schema(description = "对比期初/年初余额", example = "8000.00")
    private BigDecimal beginAmount;
}
