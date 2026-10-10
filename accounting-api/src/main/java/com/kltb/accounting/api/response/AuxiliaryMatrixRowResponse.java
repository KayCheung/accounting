package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 辅助核算交叉对比矩阵行响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算交叉对比矩阵行响应 DTO")
public class AuxiliaryMatrixRowResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "科目编码", example = "6601")
    private String subjectCode;

    @Schema(description = "科目名称", example = "销售费用")
    private String subjectName;

    @Schema(description = "科目账类：1-资产, 2-负债, 3-权益, 4-共同, 5-成本, 6-损益", example = "6")
    private Integer subjectCategory;

    @Schema(description = "科目账类名称", example = "损益类")
    private String subjectCategoryDesc;

    @Schema(description = "各核算项对应发生额映射（key为auxCode，value为金额）")
    private Map<String, BigDecimal> amounts;

    @Schema(description = "科目行发生合计", example = "312000.00")
    private BigDecimal totalAmount;

    @Schema(description = "行合计占全表总计百分比(%)", example = "35.43")
    private BigDecimal percentage;
}
