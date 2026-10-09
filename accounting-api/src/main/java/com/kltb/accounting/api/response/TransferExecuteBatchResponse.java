package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "期末结转执行响应")
public class TransferExecuteBatchResponse {

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "执行规则总数")
    private Integer totalRules;

    @Schema(description = "成功规则数")
    private Integer successCount;

    @Schema(description = "失败规则数")
    private Integer failedCount;

    @Schema(description = "结转总金额")
    private BigDecimal totalAmount;

    @Schema(description = "总耗时（毫秒）")
    private Long totalDurationMs;

    @Schema(description = "各规则执行明细")
    private List<TransferExecuteItemResponse> items;
}
