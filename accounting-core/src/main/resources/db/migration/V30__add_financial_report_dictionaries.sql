-- V30: 财务报表大类与计算规则字典数据初始化及公式表达式规范化

-- 1. 财务报表大类分类字典 (financial_report_category)
INSERT IGNORE INTO `t_dictionary` (`dict_type`, `dict_code`, `dict_name`, `dict_name_en`, `sort_order`, `group_key`, `status`, `is_system`, `tenant_id`) VALUES
('financial_report_category', 'CURRENT_ASSET', '流动资产', 'Current Assets', 1, 'REPORT', 1, 1, -1),
('financial_report_category', 'NON_CURRENT_ASSET', '非流动资产', 'Non-Current Assets', 2, 'REPORT', 1, 1, -1),
('financial_report_category', 'ASSET_TOTAL', '资产总计', 'Total Assets', 3, 'REPORT', 1, 1, -1),
('financial_report_category', 'CURRENT_LIABILITY', '流动负债', 'Current Liabilities', 4, 'REPORT', 1, 1, -1),
('financial_report_category', 'NON_CURRENT_LIABILITY', '非流动负债', 'Non-Current Liabilities', 5, 'REPORT', 1, 1, -1),
('financial_report_category', 'LIAB_TOTAL', '负债合计', 'Total Liabilities', 6, 'REPORT', 1, 1, -1),
('financial_report_category', 'EQUITY', '所有者权益', 'Owner Equity', 7, 'REPORT', 1, 1, -1),
('financial_report_category', 'LIAB_EQUITY_TOTAL', '负债和所有者权益总计', 'Total Liabilities and Equity', 8, 'REPORT', 1, 1, -1),
('financial_report_category', 'OPERATING_REVENUE', '营业收入', 'Operating Revenue', 9, 'REPORT', 1, 1, -1),
('financial_report_category', 'OPERATING_COST', '营业成本', 'Operating Cost', 10, 'REPORT', 1, 1, -1),
('financial_report_category', 'EXPENSE', '期间费用与税金', 'Expenses & Taxes', 11, 'REPORT', 1, 1, -1),
('financial_report_category', 'INCOME', '投资与营业外收益', 'Income & Investment', 12, 'REPORT', 1, 1, -1),
('financial_report_category', 'PROFIT', '利润指标', 'Profit Metrics', 13, 'REPORT', 1, 1, -1);

-- 2. 财务报表计算规则符号字典 (financial_report_calc_operator)
INSERT IGNORE INTO `t_dictionary` (`dict_type`, `dict_code`, `dict_name`, `dict_name_en`, `sort_order`, `group_key`, `status`, `is_system`, `tenant_id`) VALUES
('financial_report_calc_operator', '+', '科目累加', 'Add', 1, 'REPORT', 1, 1, -1),
('financial_report_calc_operator', '-', '科目抵扣/减项', 'Subtract', 2, 'REPORT', 1, 1, -1),
('financial_report_calc_operator', 'SUM_CATEGORY', '分类小计', 'Sum Category', 3, 'REPORT', 1, 1, -1),
('financial_report_calc_operator', 'SUM_TOTAL', '报表总计', 'Sum Total', 4, 'REPORT', 1, 1, -1),
('financial_report_calc_operator', 'UNDISTRIBUTED_PROFIT', '未分配利润(含未结转损益)', 'Undistributed Profit', 5, 'REPORT', 1, 1, -1),
('financial_report_calc_operator', 'FORMULA', '公式表达式计算', 'Formula Calculation', 6, 'REPORT', 1, 1, -1);

-- 3. 规范化 t_financial_report_item_mapping 中利润表公式项的标准变量表达式
UPDATE `t_financial_report_item_mapping`
SET `formula_expr` = 'REV_OPERATING - COST_OPERATING - TAX_SURCHARGE - EXP_SELLING - EXP_ADMIN - EXP_RD - EXP_FINANCIAL + INC_INVESTMENT'
WHERE `report_type` = 'INCOME_STATEMENT' AND `item_code` = 'PROFIT_OPERATING';

UPDATE `t_financial_report_item_mapping`
SET `formula_expr` = 'PROFIT_OPERATING + INC_NON_OPERATING - EXP_NON_OPERATING'
WHERE `report_type` = 'INCOME_STATEMENT' AND `item_code` = 'PROFIT_TOTAL';

UPDATE `t_financial_report_item_mapping`
SET `formula_expr` = 'PROFIT_TOTAL - EXP_INCOME_TAX'
WHERE `report_type` = 'INCOME_STATEMENT' AND `item_code` = 'PROFIT_NET';
