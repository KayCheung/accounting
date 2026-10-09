package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "期末结转执行/预览请求")
public class TransferExecuteRequest {

    @NotNull(message = "会计日期不能为空")
    @Schema(description = "会计日期（yyyy-MM-dd）", example = "2026-10-08")
    private LocalDate accountingDate;

    @Schema(description = "指定执行的规则编码（选填，不传则执行全部启用的规则）", example = "TR_REV_PROFIT")
    private String ruleCode;

    @Schema(description = "结转类型过滤：1-损益结转, 2-成本结转, 3-自定义结转（选填）", example = "1")
    private Integer transferType;

    @Schema(description = "是否强制重新执行（若当日已成功执行过该规则，true 将允许重新生成凭证）", example = "false")
    private boolean forceRetry = false;
}
