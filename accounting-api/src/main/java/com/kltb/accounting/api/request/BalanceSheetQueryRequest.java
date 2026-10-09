package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Schema(description = "资产负债表查询请求")
public class BalanceSheetQueryRequest implements Serializable {

    @NotNull(message = "报告期会计日期不能为空")
    @Schema(description = "报告期会计日期（如 2026-03-31）", example = "2026-03-31")
    private LocalDate accountingDate;

    @Schema(description = "是否对比年初（默认 true）", example = "true")
    private Boolean compareYearStart = true;
}
