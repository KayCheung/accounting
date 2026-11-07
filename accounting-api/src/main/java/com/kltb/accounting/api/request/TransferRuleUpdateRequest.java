package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "期末结转规则修改请求")
public class TransferRuleUpdateRequest {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 64, message = "规则名称最大长度64")
    @Schema(description = "规则名称", example = "主营业务收入损益结转")
    private String ruleName;

    @NotNull(message = "结转类型不能为空")
    @Schema(description = "结转类型：1-损益结转, 2-成本结转, 3-自定义结转", example = "1")
    private Integer transferType;

    @NotBlank(message = "源科目编码范围不能为空")
    @Size(max = 32, message = "源科目编码最大长度32")
    @Schema(description = "源科目编码（支持通配符如 60*）", example = "60*")
    private String sourceSubjectCode;

    @NotBlank(message = "目标科目编码不能为空")
    @Size(max = 32, message = "目标科目编码最大长度32")
    @Schema(description = "目标科目编码（需为末级科目）", example = "410301")
    private String targetSubjectCode;

    @NotNull(message = "结转方向不能为空")
    @Schema(description = "结转方向：1-借方余额结转到贷方, 2-贷方余额结转到借方", example = "2")
    private Integer transferDirection;

    @Size(max = 128, message = "摘要模板最大长度128")
    @Schema(description = "摘要模板（支持 {year}、{month} 占位符）", example = "{year}年{month}月损益结转")
    private String summaryTemplate;

    @Schema(description = "执行顺序（数值越小越优先执行）", example = "10")
    private Integer executeOrder;

    @Schema(description = "是否支持自动结转（true-支持, false-仅手动）", example = "true")
    private Boolean autoTransfer;

    @Schema(description = "结转周期：1-每日(DAILY), 2-月末(MONTHLY), 3-季末(QUARTERLY), 4-年末(YEARLY), 5-仅手动(MANUAL)", example = "2")
    private Integer periodCycle;
}

