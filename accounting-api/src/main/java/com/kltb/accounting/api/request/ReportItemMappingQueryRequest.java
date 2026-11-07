package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 财务报表行项目映射配置分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "财务报表行项目映射配置分页查询请求")
public class ReportItemMappingQueryRequest extends PageRequest {

    @Schema(description = "报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表, CASH_FLOW-现金流量表")
    private String reportType;

    @Schema(description = "关键词模糊匹配(编码或名称)")
    private String keyword;

    @Schema(description = "分类分组(如 CURRENT_ASSET, NON_CURRENT_ASSET, CURRENT_LIABILITY, EQUITY, OPERATING_REVENUE 等)")
    private String itemCategory;

    @Schema(description = "展示层级: 1-大类标题, 2-数据明细项, 3-合计/小计项")
    private Integer displayLevel;

    @Schema(description = "启用状态: 1-启用, 2-停用")
    private Integer status;
}
