package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "期末结转规则分页查询请求")
public class TransferRuleQueryRequest extends PageRequest {

    @Schema(description = "规则编码模糊匹配")
    private String ruleCode;

    @Schema(description = "规则名称模糊匹配")
    private String ruleName;

    @Schema(description = "结转类型：1-损益结转, 2-成本结转, 3-自定义结转")
    private Integer transferType;

    @Schema(description = "状态：1-启用, 2-停用")
    private Integer status;

    @Schema(description = "是否支持自动结转")
    private Boolean autoTransfer;

    @Schema(description = "结转周期：1-每日(DAILY), 2-月末(MONTHLY), 3-季末(QUARTERLY), 4-年末(YEARLY), 5-仅手动(MANUAL)")
    private Integer periodCycle;
}

