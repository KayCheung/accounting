package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 财务报表行项目映射新增请求
 */
@Data
@Schema(description = "财务报表行项目映射新增请求")
public class ReportItemMappingCreateRequest {

    @NotBlank(message = "报表类型不能为空")
    @Schema(description = "报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表, CASH_FLOW-现金流量表", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reportType;

    @NotBlank(message = "行项目编码不能为空")
    @Schema(description = "行项目唯一编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemCode;

    @NotBlank(message = "行项目名称不能为空")
    @Schema(description = "行项目显示名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemName;

    @Schema(description = "分类分组(如 CURRENT_ASSET, NON_CURRENT_ASSET, CURRENT_LIABILITY, EQUITY, OPERATING_REVENUE 等)")
    private String itemCategory;

    @NotNull(message = "行次序号不能为空")
    @Schema(description = "报表行次与排序号", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer lineNo;

    @NotNull(message = "展示层级不能为空")
    @Schema(description = "展示层级: 1-大类标题, 2-数据明细项, 3-合计/小计项", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer displayLevel;

    @Schema(description = "绑定的科目代码列表(英文逗号分隔，如 1001,1002,1012)")
    private String subjectCodes;

    @NotBlank(message = "计算规则符号不能为空")
    @Schema(description = "计算规则符号: + 累加, - 抵扣, SUM_CATEGORY 分类小计, SUM_TOTAL 报表总计, UNDISTRIBUTED_PROFIT 未分配利润, FORMULA 扩展公式", requiredMode = Schema.RequiredMode.REQUIRED)
    private String calcOperator;

    @Schema(description = "取数方向: AUTO-跟随科目, DEBIT-借方, CREDIT-贷方")
    private String balanceDirection;

    @Schema(description = "扩展计算公式表达式")
    private String formulaExpr;

    @Schema(description = "状态: 1-启用, 2-停用")
    private Integer status;

    @Schema(description = "备注说明")
    private String remark;
}
