package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "单条规则执行结果")
public class TransferExecuteItemResponse {

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "结转流水号")
    private String transferNo;

    @Schema(description = "生成的凭证号")
    private String voucherNo;

    @Schema(description = "结转总金额")
    private BigDecimal totalAmount;

    @Schema(description = "执行状态：1-处理中, 2-成功, 3-失败")
    private Integer status;

    @Schema(description = "执行状态描述")
    private String statusDesc;

    @Schema(description = "失败原因（若失败）")
    private String failReason;
}
