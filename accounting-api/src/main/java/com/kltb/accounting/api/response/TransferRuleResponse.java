package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "期末结转规则响应对象")
public class TransferRuleResponse {

    @Schema(description = "规则ID")
    private Long id;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "结转类型：1-损益结转, 2-成本结转, 3-自定义结转")
    private Integer transferType;

    @Schema(description = "结转类型描述")
    private String transferTypeDesc;

    @Schema(description = "源科目编码范围（通配符）")
    private String sourceSubjectCode;

    @Schema(description = "目标科目编码")
    private String targetSubjectCode;

    @Schema(description = "目标科目名称")
    private String targetSubjectName;

    @Schema(description = "结转方向：1-借方余额结转到贷方, 2-贷方余额结转到借方")
    private Integer transferDirection;

    @Schema(description = "结转方向描述")
    private String transferDirectionDesc;

    @Schema(description = "摘要模板")
    private String summaryTemplate;

    @Schema(description = "执行顺序")
    private Integer executeOrder;

    @Schema(description = "状态：1-启用, 2-停用")
    private Integer status;

    @Schema(description = "是否支持自动结转")
    private Boolean autoTransfer;

    @Schema(description = "结转周期：1-每日, 2-月末, 3-季末, 4-年末, 5-仅手动")
    private Integer periodCycle;

    @Schema(description = "结转周期描述")
    private String periodCycleDesc;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}

