-- V29: 财务报表行项目映射配置表与 CAS 标准初始数据
-- 解决需求: 财务报表行项科目映射从代码硬编码平滑演进为数据库配置驱动 (t_financial_report_item_mapping)

CREATE TABLE IF NOT EXISTS `t_financial_report_item_mapping` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID: -1表示全租户通用准则标准',
    `report_type` VARCHAR(32) NOT NULL COMMENT '报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表, CASH_FLOW-现金流量表',
    `item_code` VARCHAR(64) NOT NULL COMMENT '行项目编码(全局或报表内唯一)',
    `item_name` VARCHAR(128) NOT NULL COMMENT '行项目名称(报表显示名称)',
    `item_category` VARCHAR(64) NOT NULL DEFAULT 'DEFAULT' COMMENT '分类分组(如 CURRENT_ASSET, NON_CURRENT_ASSET, CURRENT_LIABILITY, EQUITY, OPERATING_REVENUE 等)',
    `line_no` INT NOT NULL DEFAULT 0 COMMENT '报表行次与排序号',
    `display_level` TINYINT NOT NULL DEFAULT 2 COMMENT '展示层级: 1-大类标题, 2-数据明细项, 3-合计/小计项',
    `subject_codes` VARCHAR(512) DEFAULT NULL COMMENT '绑定的科目代码列表(英文逗号分隔，如 1001,1002,1012)',
    `calc_operator` VARCHAR(32) NOT NULL DEFAULT '+' COMMENT '计算规则符号: + 累加, - 抵扣, SUM_CATEGORY 分类小计, SUM_TOTAL 报表总计, UNDISTRIBUTED_PROFIT 未分配利润(含未结转损益), FORMULA 自定义公式',
    `balance_direction` VARCHAR(16) NOT NULL DEFAULT 'AUTO' COMMENT '取数方向: AUTO-跟随科目本身方向, DEBIT-取借方, CREDIT-取贷方',
    `formula_expr` VARCHAR(255) DEFAULT NULL COMMENT '扩展计算公式表达式',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-启用, 2-停用',
    `remark` VARCHAR(255) DEFAULT NULL COMMENT '备注说明',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识: 0-未删除, 非0为删除时间戳或ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tenant_report_item` (`tenant_id`, `report_type`, `item_code`, `is_delete`),
    KEY `idx_report_type_sort` (`tenant_id`, `report_type`, `line_no`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务报表行项目映射配置表';

-- 预置 CAS (中国企业会计准则) 资产负债表标准配置项
INSERT INTO `t_financial_report_item_mapping`
(`tenant_id`, `report_type`, `item_code`, `item_name`, `item_category`, `line_no`, `display_level`, `subject_codes`, `calc_operator`, `balance_direction`, `formula_expr`, `status`, `remark`)
VALUES
(-1, 'BALANCE_SHEET', 'ASSET_CURRENT_HEADER', '流动资产：', 'CURRENT_ASSET', 1, 1, NULL, '+', 'AUTO', NULL, 1, '流动资产分组标题'),
(-1, 'BALANCE_SHEET', 'ASSET_CASH', '货币资金', 'CURRENT_ASSET', 2, 2, '1001,1002,1012', '+', 'AUTO', NULL, 1, '库存现金/银行存款/其他货币资金'),
(-1, 'BALANCE_SHEET', 'ASSET_NOTE_REC', '应收票据', 'CURRENT_ASSET', 3, 2, '1121', '+', 'AUTO', NULL, 1, '应收票据'),
(-1, 'BALANCE_SHEET', 'ASSET_AR', '应收账款', 'CURRENT_ASSET', 4, 2, '1122', '+', 'AUTO', NULL, 1, '应收账款'),
(-1, 'BALANCE_SHEET', 'ASSET_PREPAY', '预付款项', 'CURRENT_ASSET', 5, 2, '1123', '+', 'AUTO', NULL, 1, '预付款项'),
(-1, 'BALANCE_SHEET', 'ASSET_OTHER_REC', '其他应收款', 'CURRENT_ASSET', 6, 2, '1221', '+', 'AUTO', NULL, 1, '其他应收款'),
(-1, 'BALANCE_SHEET', 'ASSET_INVENTORY', '存货', 'CURRENT_ASSET', 7, 2, '1403,1405,1406', '+', 'AUTO', NULL, 1, '原材料/库存商品/发出商品'),
(-1, 'BALANCE_SHEET', 'ASSET_OTHER_CURRENT', '其他流动资产', 'CURRENT_ASSET', 8, 2, '1999', '+', 'AUTO', NULL, 1, '其他1字头流动资产兜底'),
(-1, 'BALANCE_SHEET', 'ASSET_CURRENT_TOTAL', '流动资产合计', 'CURRENT_ASSET', 9, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '流动资产小计'),
(-1, 'BALANCE_SHEET', 'ASSET_NON_CURRENT_HEADER', '非流动资产：', 'NON_CURRENT_ASSET', 10, 1, NULL, '+', 'AUTO', NULL, 1, '非流动资产分组标题'),
(-1, 'BALANCE_SHEET', 'ASSET_FIXED', '固定资产', 'NON_CURRENT_ASSET', 11, 2, '1601,1602', '+', 'AUTO', NULL, 1, '固定资产与累计折旧净额'),
(-1, 'BALANCE_SHEET', 'ASSET_INTANGIBLE', '无形资产', 'NON_CURRENT_ASSET', 12, 2, '1701', '+', 'AUTO', NULL, 1, '无形资产'),
(-1, 'BALANCE_SHEET', 'ASSET_NON_CURRENT_TOTAL', '非流动资产合计', 'NON_CURRENT_ASSET', 13, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '非流动资产小计'),
(-1, 'BALANCE_SHEET', 'ASSET_TOTAL', '资产总计', 'ASSET_TOTAL', 14, 3, NULL, 'SUM_TOTAL', 'AUTO', NULL, 1, '资产两项合计总计'),

(-1, 'BALANCE_SHEET', 'LIAB_CURRENT_HEADER', '流动负债：', 'CURRENT_LIABILITY', 51, 1, NULL, '+', 'AUTO', NULL, 1, '流动负债分组标题'),
(-1, 'BALANCE_SHEET', 'LIAB_ST_LOAN', '短期借款', 'CURRENT_LIABILITY', 52, 2, '2001', '+', 'AUTO', NULL, 1, '短期借款'),
(-1, 'BALANCE_SHEET', 'LIAB_AP', '应付账款', 'CURRENT_LIABILITY', 53, 2, '2202', '+', 'AUTO', NULL, 1, '应付账款'),
(-1, 'BALANCE_SHEET', 'LIAB_ADV_REC', '预收款项', 'CURRENT_LIABILITY', 54, 2, '2203', '+', 'AUTO', NULL, 1, '预收款项'),
(-1, 'BALANCE_SHEET', 'LIAB_PAYROLL', '应付职工薪酬', 'CURRENT_LIABILITY', 55, 2, '2211', '+', 'AUTO', NULL, 1, '应付职工薪酬'),
(-1, 'BALANCE_SHEET', 'LIAB_TAX', '应交税费', 'CURRENT_LIABILITY', 56, 2, '2221', '+', 'AUTO', NULL, 1, '应交税费'),
(-1, 'BALANCE_SHEET', 'LIAB_OTHER_PAY', '其他应付款', 'CURRENT_LIABILITY', 57, 2, '2241', '+', 'AUTO', NULL, 1, '其他应付款'),
(-1, 'BALANCE_SHEET', 'LIAB_OTHER_CURRENT', '其他流动负债', 'CURRENT_LIABILITY', 58, 2, '2999', '+', 'AUTO', NULL, 1, '其他2字头流动负债兜底'),
(-1, 'BALANCE_SHEET', 'LIAB_CURRENT_TOTAL', '流动负债合计', 'CURRENT_LIABILITY', 59, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '流动负债小计'),
(-1, 'BALANCE_SHEET', 'LIAB_NON_CURRENT_HEADER', '非流动负债：', 'NON_CURRENT_LIABILITY', 60, 1, NULL, '+', 'AUTO', NULL, 1, '非流动负债分组标题'),
(-1, 'BALANCE_SHEET', 'LIAB_LT_LOAN', '长期借款', 'NON_CURRENT_LIABILITY', 61, 2, '2501', '+', 'AUTO', NULL, 1, '长期借款'),
(-1, 'BALANCE_SHEET', 'LIAB_NON_CURRENT_TOTAL', '非流动负债合计', 'NON_CURRENT_LIABILITY', 62, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '非流动负债小计'),
(-1, 'BALANCE_SHEET', 'LIAB_TOTAL', '负债合计', 'LIAB_TOTAL', 63, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '流动与非流动负债合计'),

(-1, 'BALANCE_SHEET', 'EQUITY_HEADER', '所有者权益：', 'EQUITY', 70, 1, NULL, '+', 'AUTO', NULL, 1, '所有者权益分组标题'),
(-1, 'BALANCE_SHEET', 'EQUITY_CAPITAL', '实收资本(或股本)', 'EQUITY', 71, 2, '4001', '+', 'AUTO', NULL, 1, '实收资本'),
(-1, 'BALANCE_SHEET', 'EQUITY_RESERVE_CAPITAL', '资本公积', 'EQUITY', 72, 2, '4002', '+', 'AUTO', NULL, 1, '资本公积'),
(-1, 'BALANCE_SHEET', 'EQUITY_SURPLUS_RESERVE', '盈余公积', 'EQUITY', 73, 2, '4101', '+', 'AUTO', NULL, 1, '盈余公积'),
(-1, 'BALANCE_SHEET', 'EQUITY_UNDISTRIBUTED_PROFIT', '未分配利润', 'EQUITY', 74, 2, '4103,4104', 'UNDISTRIBUTED_PROFIT', 'AUTO', NULL, 1, '本年利润与利润分配(表结法含未结转损益)'),
(-1, 'BALANCE_SHEET', 'EQUITY_TOTAL', '所有者权益合计', 'EQUITY', 75, 3, NULL, 'SUM_CATEGORY', 'AUTO', NULL, 1, '所有者权益小计'),
(-1, 'BALANCE_SHEET', 'LIAB_EQUITY_TOTAL', '负债和所有者权益总计', 'LIAB_EQUITY_TOTAL', 76, 3, NULL, 'SUM_TOTAL', 'AUTO', NULL, 1, '负债与所有者权益总计');

-- 预置 CAS 利润表标准配置项
INSERT INTO `t_financial_report_item_mapping`
(`tenant_id`, `report_type`, `item_code`, `item_name`, `item_category`, `line_no`, `display_level`, `subject_codes`, `calc_operator`, `balance_direction`, `formula_expr`, `status`, `remark`)
VALUES
(-1, 'INCOME_STATEMENT', 'REV_OPERATING', '一、营业收入', 'OPERATING_REVENUE', 1, 1, '6001,6051', '+', 'AUTO', NULL, 1, '主营业务收入与其它业务收入'),
(-1, 'INCOME_STATEMENT', 'COST_OPERATING', '    减：营业成本', 'OPERATING_COST', 2, 2, '6401,6402', '-', 'AUTO', NULL, 1, '主营业务成本与其他业务成本'),
(-1, 'INCOME_STATEMENT', 'TAX_SURCHARGE', '    税金及附加', 'EXPENSE', 3, 2, '6403', '-', 'AUTO', NULL, 1, '税金及附加'),
(-1, 'INCOME_STATEMENT', 'EXP_SELLING', '    销售费用', 'EXPENSE', 4, 2, '6601', '-', 'AUTO', NULL, 1, '销售费用'),
(-1, 'INCOME_STATEMENT', 'EXP_ADMIN', '    管理费用', 'EXPENSE', 5, 2, '6602', '-', 'AUTO', NULL, 1, '管理费用'),
(-1, 'INCOME_STATEMENT', 'EXP_RD', '    研发费用', 'EXPENSE', 6, 2, '6605', '-', 'AUTO', NULL, 1, '研发费用'),
(-1, 'INCOME_STATEMENT', 'EXP_FINANCIAL', '    财务费用', 'EXPENSE', 7, 2, '6603', '-', 'AUTO', NULL, 1, '财务费用'),
(-1, 'INCOME_STATEMENT', 'INC_INVESTMENT', '    加：投资收益', 'INCOME', 8, 2, '6111', '+', 'AUTO', NULL, 1, '投资收益'),
(-1, 'INCOME_STATEMENT', 'PROFIT_OPERATING', '二、营业利润', 'PROFIT', 9, 1, NULL, 'FORMULA', 'AUTO', 'REV_OPERATING - COST_OPERATING - TAX_SURCHARGE - EXP_SELLING - EXP_ADMIN - EXP_RD - EXP_FINANCIAL + INC_INVESTMENT', 1, '营业利润小计'),
(-1, 'INCOME_STATEMENT', 'INC_NON_OPERATING', '    加：营业外收入', 'INCOME', 10, 2, '6301', '+', 'AUTO', NULL, 1, '营业外收入'),
(-1, 'INCOME_STATEMENT', 'EXP_NON_OPERATING', '    减：营业外支出', 'EXPENSE', 11, 2, '6711', '-', 'AUTO', NULL, 1, '营业外支出'),
(-1, 'INCOME_STATEMENT', 'PROFIT_TOTAL', '三、利润总额', 'PROFIT', 12, 1, NULL, 'FORMULA', 'AUTO', 'PROFIT_OPERATING + INC_NON_OPERATING - EXP_NON_OPERATING', 1, '利润总额小计'),
(-1, 'INCOME_STATEMENT', 'EXP_INCOME_TAX', '    减：所得税费用', 'EXPENSE', 13, 2, '6801', '-', 'AUTO', NULL, 1, '所得税费用'),
(-1, 'INCOME_STATEMENT', 'PROFIT_NET', '四、净利润', 'PROFIT', 14, 3, NULL, 'FORMULA', 'AUTO', 'PROFIT_TOTAL - EXP_INCOME_TAX', 1, '净利润最终指标');
