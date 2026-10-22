package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 财务报表行项目映射配置响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "财务报表行项目映射配置响应 DTO")
public class ReportItemMappingResponse {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "租户ID")
    private Integer tenantId;

    @Schema(description = "报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表, CASH_FLOW-现金流量表")
    private String reportType;

    @Schema(description = "行项目唯一编码")
    private String itemCode;

    @Schema(description = "行项目显示名称")
    private String itemName;

    @Schema(description = "分类分组")
    private String itemCategory;

    @Schema(description = "报表行次与排序号")
    private Integer lineNo;

    @Schema(description = "展示层级: 1-大类标题, 2-数据明细项, 3-合计/小计项")
    private Integer displayLevel;

    @Schema(description = "绑定的科目代码列表(英文逗号分隔)")
    private String subjectCodes;

    @Schema(description = "计算规则符号: + 累加, - 抵扣, SUM_CATEGORY 分类小计, SUM_TOTAL 报表总计, UNDISTRIBUTED_PROFIT 未分配利润, FORMULA 扩展公式")
    private String calcOperator;

    @Schema(description = "取数方向: AUTO-跟随科目, DEBIT-借方, CREDIT-贷方")
    private String balanceDirection;

    @Schema(description = "扩展计算公式表达式")
    private String formulaExpr;

    @Schema(description = "状态: 1-启用, 2-停用")
    private Integer status;

    @Schema(description = "备注说明")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
