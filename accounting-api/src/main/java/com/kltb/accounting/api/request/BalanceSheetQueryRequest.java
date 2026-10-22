package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.kltb.accounting.api.constant.ClosingMethodEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Schema(description = "资产负债表查询请求")
public class BalanceSheetQueryRequest implements Serializable {

    @NotNull(message = "报告期时点会计日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "报告期时点会计日期（如 2026-03-31）", example = "2026-03-31")
    private LocalDate accountingDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "对比期时点会计日期（可选，默认当年1月1日年初；可自定义对比上月月末、上年同期或指定历史日）", example = "2026-01-01")
    private LocalDate compareDate;

    @Schema(description = "是否对比年初（兼容字段）", example = "true")
    private Boolean compareYearStart = true;

    @Schema(description = "结账核算方式：REPORT-表结法(动态计算未结转损益，推荐日常查看)；ACCOUNT-账结法(以实际结转凭证为准，用于审计对账)", example = "REPORT")
    private String closingMethod = ClosingMethodEnum.REPORT.getCode();
}
