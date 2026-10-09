package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "单规则结转预览详情")
public class TransferPreviewRuleItemResponse {

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "结转类型：1-损益结转, 2-成本结转, 3-自定义结转")
    private Integer transferType;

    @Schema(description = "结转类型描述")
    private String transferTypeDesc;

    @Schema(description = "源科目模式")
    private String sourceSubjectCode;

    @Schema(description = "目标科目")
    private String targetSubjectCode;

    @Schema(description = "目标科目名称")
    private String targetSubjectName;

    @Schema(description = "匹配到的源账户数量")
    private Integer matchedAccountCount;

    @Schema(description = "拟结转总金额")
    private BigDecimal totalAmount;

    @Schema(description = "借方总金额")
    private BigDecimal totalDebitAmount;

    @Schema(description = "贷方总金额")
    private BigDecimal totalCreditAmount;

    @Schema(description = "借贷是否平衡")
    private boolean balanced;

    @Schema(description = "是否支持自动结转")
    private Boolean autoTransfer;

    @Schema(description = "结转周期：1-每日, 2-月末, 3-季末, 4-年末, 5-仅手动")
    private Integer periodCycle;

    @Schema(description = "结转周期描述")
    private String periodCycleDesc;

    @Schema(description = "预估分录列表")
    private List<TransferPreviewEntryItemResponse> entries;
}

