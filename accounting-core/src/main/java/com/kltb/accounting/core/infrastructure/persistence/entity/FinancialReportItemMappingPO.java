package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.kltb.accounting.core.domain.enums.AvailableStatusEnum;
import com.kltb.accounting.core.domain.enums.ReportBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.ReportCalcOperatorEnum;
import com.kltb.accounting.core.domain.enums.ReportCategoryEnum;
import com.kltb.accounting.core.domain.enums.ReportDisplayLevelEnum;
import com.kltb.accounting.core.domain.enums.ReportTypeEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 财务报表行项目映射持久化对象
 * <p>
 * 对应表: t_financial_report_item_mapping
 * 驱动资产负债表、利润表等报表行项目与底层会计科目、汇总公式的动态映射
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_financial_report_item_mapping")
public class FinancialReportItemMappingPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表, CASH_FLOW-现金流量表
     */
    private ReportTypeEnum reportType;

    /**
     * 行项目编码(租户及报表内唯一)
     */
    private String itemCode;

    /**
     * 行项目名称(报表显示名称)
     */
    private String itemName;

    /**
     * 分类分组(如 CURRENT_ASSET, NON_CURRENT_ASSET, CURRENT_LIABILITY, EQUITY 等)
     */
    private ReportCategoryEnum itemCategory;

    /**
     * 报表行次与排序号
     */
    private Integer lineNo;

    /**
     * 展示层级: 1-大类标题, 2-数据明细项, 3-合计/小计项
     */
    private ReportDisplayLevelEnum displayLevel;

    /**
     * 绑定的科目代码列表(英文逗号分隔，如 1001,1002,1012)
     */
    private String subjectCodes;

    /**
     * 计算规则符号: + 累加, - 抵扣, SUM_CATEGORY 分类小计, SUM_TOTAL 报表总计, UNDISTRIBUTED_PROFIT 未分配利润, FORMULA 扩展公式
     */
    private ReportCalcOperatorEnum calcOperator;

    /**
     * 取数方向: AUTO-跟随科目本身方向, DEBIT-取借方, CREDIT-取贷方
     */
    private ReportBalanceDirectionEnum balanceDirection;

    /**
     * 扩展计算公式表达式
     */
    private String formulaExpr;

    /**
     * 状态: 1-启用, 2-停用
     */
    private AvailableStatusEnum status;

    /**
     * 备注说明
     */
    private String remark;
}
