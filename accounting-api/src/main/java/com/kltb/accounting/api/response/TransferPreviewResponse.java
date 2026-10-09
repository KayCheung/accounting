package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "期末结转试算预览总响应")
public class TransferPreviewResponse {

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "预计执行规则总数")
    private Integer totalRules;

    @Schema(description = "有匹配发生额的规则数")
    private Integer activeRules;

    @Schema(description = "拟结转总金额")
    private BigDecimal grandTotalAmount;

    @Schema(description = "全局借贷是否平衡")
    private boolean allBalanced;

    @Schema(description = "各规则预览明细")
    private List<TransferPreviewRuleItemResponse> rulePreviews;
}
