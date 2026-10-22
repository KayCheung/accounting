package com.kltb.accounting.core.domain.constant;

/**
 * 财务报表标准行项编码常量统一定义
 * <p>
 * 严禁在业务逻辑中散落硬编码魔法值字符串。
 */
public final class ReportItemConstants {

    private ReportItemConstants() {
    }

    // =========================================================================
    // 资产负债表行项编码 (Balance Sheet)
    // =========================================================================

    public static final String ASSET_CURRENT_HEADER = "ASSET_CURRENT_HEADER";           // 流动资产标题
    public static final String ASSET_CASH = "ASSET_CASH";                               // 货币资金
    public static final String ASSET_NOTE_REC = "ASSET_NOTE_REC";                       // 应收票据
    public static final String ASSET_AR = "ASSET_AR";                                   // 应收账款
    public static final String ASSET_PREPAY = "ASSET_PREPAY";                           // 预付款项
    public static final String ASSET_OTHER_REC = "ASSET_OTHER_REC";                     // 其他应收款
    public static final String ASSET_INVENTORY = "ASSET_INVENTORY";                     // 存货
    public static final String ASSET_OTHER_CURRENT = "ASSET_OTHER_CURRENT";             // 其他流动资产
    public static final String ASSET_CURRENT_TOTAL = "ASSET_CURRENT_TOTAL";             // 流动资产合计
    public static final String ASSET_NON_CURRENT_HEADER = "ASSET_NON_CURRENT_HEADER";   // 非流动资产标题
    public static final String ASSET_FIXED = "ASSET_FIXED";                             // 固定资产
    public static final String ASSET_INTANGIBLE = "ASSET_INTANGIBLE";                   // 无形资产
    public static final String ASSET_NON_CURRENT_TOTAL = "ASSET_NON_CURRENT_TOTAL";   // 非流动资产合计
    public static final String ASSET_TOTAL = "ASSET_TOTAL";                             // 资产总计

    public static final String LIAB_CURRENT_HEADER = "LIAB_CURRENT_HEADER";             // 流动负债标题
    public static final String LIAB_ST_LOAN = "LIAB_ST_LOAN";                           // 短期借款
    public static final String LIAB_AP = "LIAB_AP";                                     // 应付账款
    public static final String LIAB_ADV_REC = "LIAB_ADV_REC";                           // 预收款项
    public static final String LIAB_PAYROLL = "LIAB_PAYROLL";                           // 应付职工薪酬
    public static final String LIAB_TAX = "LIAB_TAX";                                   // 应交税费
    public static final String LIAB_OTHER_PAY = "LIAB_OTHER_PAY";                       // 其他应付款
    public static final String LIAB_OTHER_CURRENT = "LIAB_OTHER_CURRENT";               // 其他流动负债
    public static final String LIAB_CURRENT_TOTAL = "LIAB_CURRENT_TOTAL";               // 流动负债合计
    public static final String LIAB_NON_CURRENT_HEADER = "LIAB_NON_CURRENT_HEADER";   // 非流动负债标题
    public static final String LIAB_LT_LOAN = "LIAB_LT_LOAN";                           // 长期借款
    public static final String LIAB_NON_CURRENT_TOTAL = "LIAB_NON_CURRENT_TOTAL";     // 非流动负债合计
    public static final String LIAB_TOTAL = "LIAB_TOTAL";                               // 负债合计

    public static final String EQUITY_HEADER = "EQUITY_HEADER";                         // 所有者权益标题
    public static final String EQUITY_CAPITAL = "EQUITY_CAPITAL";                       // 实收资本(或股本)
    public static final String EQUITY_RESERVE_CAPITAL = "EQUITY_RESERVE_CAPITAL";       // 资本公积
    public static final String EQUITY_SURPLUS_RESERVE = "EQUITY_SURPLUS_RESERVE";       // 盈余公积
    public static final String EQUITY_UNDISTRIBUTED_PROFIT = "EQUITY_UNDISTRIBUTED_PROFIT"; // 未分配利润
    public static final String EQUITY_TOTAL = "EQUITY_TOTAL";                           // 所有者权益合计
    public static final String LIAB_EQUITY_TOTAL = "LIAB_EQUITY_TOTAL";                 // 负债和所有者权益总计

    // =========================================================================
    // 利润表行项编码 (Income Statement)
    // =========================================================================

    public static final String REV_OPERATING = "REV_OPERATING";                         // 一、营业收入
    public static final String COST_OPERATING = "COST_OPERATING";                       // 减：营业成本
    public static final String TAX_SURCHARGE = "TAX_SURCHARGE";                         // 税金及附加
    public static final String EXP_SELLING = "EXP_SELLING";                             // 销售费用
    public static final String EXP_ADMIN = "EXP_ADMIN";                                 // 管理费用
    public static final String EXP_RD = "EXP_RD";                                       // 研发费用
    public static final String EXP_FINANCIAL = "EXP_FINANCIAL";                         // 财务费用
    public static final String INC_INVESTMENT = "INC_INVESTMENT";                       // 加：投资收益
    public static final String PROFIT_OPERATING = "PROFIT_OPERATING";                   // 二、营业利润
    public static final String INC_NON_OPERATING = "INC_NON_OPERATING";                 // 加：营业外收入
    public static final String EXP_NON_OPERATING = "EXP_NON_OPERATING";                 // 减：营业外支出
    public static final String PROFIT_TOTAL = "PROFIT_TOTAL";                           // 三、利润总额
    public static final String EXP_INCOME_TAX = "EXP_INCOME_TAX";                       // 减：所得税费用
    public static final String PROFIT_NET = "PROFIT_NET";                               // 四、净利润
}
