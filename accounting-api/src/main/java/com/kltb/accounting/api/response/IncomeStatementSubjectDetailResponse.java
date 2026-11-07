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
@Schema(description = "利润表行项目归集明细科目")
public class IncomeStatementSubjectDetailResponse implements Serializable {

    @Schema(description = "科目编码", example = "600101")
    private String subjectCode;

    @Schema(description = "科目名称", example = "主营业务收入-产品销售")
    private String subjectName;

    @Schema(description = "本月/本期金额", example = "500000.00")
    private BigDecimal currentAmount;

    @Schema(description = "本年/期间累计金额", example = "1500000.00")
    private BigDecimal yearTotalAmount;

    @Schema(description = "对比期金额", example = "450000.00")
    private BigDecimal compareAmount;
}
