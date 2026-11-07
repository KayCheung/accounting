package com.kltb.accounting.core.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务报表归档快照表 PO
 * <p>
 * 对应表: t_financial_report_snapshot
 * 用于定期日终/月末生成财务报表（资产负债表、利润表）后的快照持久化归档与快速加载
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_financial_report_snapshot")
public class FinancialReportSnapshotPO extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表
     */
    private String reportType;

    /**
     * 报表名称
     */
    private String reportName;

    /**
     * 会计日期/报告日期
     */
    private LocalDate accountingDate;

    /**
     * 期间类型: DAY-日报, MONTH-月报, YEAR-年报
     */
    private String periodType;

    /**
     * 报表完整JSON内容
     */
    private String reportContent;

    /**
     * 资产总计/营业收入(核心财务指标)
     */
    private BigDecimal totalAsset;

    /**
     * 负债及所有者权益总计/净利润(核心财务指标)
     */
    private BigDecimal totalLiabilityEquity;

    /**
     * 是否平衡: 1-平衡/正常, 0-不平衡
     */
    private Integer isBalanced;
}
