-- accounting-core/src/main/resources/db/migration/V9__template_dicts.sql
-- Flyway V9：开户模板与账务核心字典初始化（业务线编码、账户类型、币种）

USE `accounting`;

-- 1. 业务线字典（business_code）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('business_code', 'jiedianqian_cl', '芒好贷', 'MangHaoDai', 1, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_ant', '芒好贷-蚂蚁', 'MangHaoDai Ant', 2, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_360zx', '芒好贷-360智信', 'MangHaoDai 360', 4, 'LOAN', 1, 1, -1),
('business_code', 'MG_GCB', '购车宝', 'Car Loan', 5, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_yb', '延保分期', 'YanBaoFenQi', 6, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_quant', '量化派', 'Quant', 7, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_ddb', '订单宝', 'DingDanBao', 7, 'LOAN', 1, 1, -1);

-- 2. 账户类型字典（account_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('account_type', '贷款本金', '贷款本金', 'Loan Principal', 1, 'LOAN', 1, 1, -1),
('account_type', '贷款逾期本金', '贷款逾期本金', 'Overdue Principal', 2, 'LOAN', 1, 1, -1),
('account_type', '利息账户', '利息账户', 'Interest Account', 3, 'LOAN', 1, 1, -1),
('account_type', '罚息账户', '罚息账户', 'Penalty Interest', 4, 'LOAN', 1, 1, -1),
('account_type', '担保费账户', '担保费账户', 'Guarantee Fee', 5, 'LOAN', 1, 1, -1),
('account_type', '基本户', '基本户', 'Basic Account', 6, 'GENERAL', 1, 1, -1),
('account_type', '专用账户', '专用账户', 'Special Account', 7, 'GENERAL', 1, 1, -1),
('account_type', '结算账户', '结算账户', 'Settlement Account', 8, 'GENERAL', 1, 1, -1),
('account_type', 'CASH', '现金账户', 'Cash Account', 9, 'GENERAL', 1, 1, -1),
('account_type', 'DEPOSIT', '存款账户', 'Deposit Account', 10, 'GENERAL', 1, 1, -1);

-- 3. 币种字典（currency）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('currency', 'CNY', '人民币', 'RMB', 1, 'CURRENCY', 1, 1, -1),
('currency', 'USD', '美元', 'US Dollar', 2, 'CURRENCY', 1, 1, -1),
('currency', 'EUR', '欧元', 'Euro', 3, 'CURRENCY', 1, 1, -1),
('currency', 'HKD', '港币', 'HK Dollar', 4, 'CURRENCY', 1, 1, -1);
