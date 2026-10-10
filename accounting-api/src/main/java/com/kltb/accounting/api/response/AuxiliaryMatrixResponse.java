package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 辅助核算交叉汇总对比（矩阵透视）响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算交叉汇总对比矩阵响应")
public class AuxiliaryMatrixResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "动态列定义（各辅助核算项目）")
    private List<AuxiliaryItemResponse> columns;

    @Schema(description = "矩阵行数据（各会计科目）")
    private List<AuxiliaryMatrixRowResponse> rows;

    @Schema(description = "各核算项列合计金额映射（key为auxCode，value为列合计金额）")
    private Map<String, BigDecimal> columnTotals;

    @Schema(description = "全表总计发生额", example = "880600.00")
    private BigDecimal grandTotal;
}
