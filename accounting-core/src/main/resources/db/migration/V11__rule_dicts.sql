-- accounting-core/src/main/resources/db/migration/V11__rule_dicts.sql
-- Flyway V11：记账规则与分录核心字典初始化

USE `accounting`;

-- 1. 凭证类型字典（voucher_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('voucher_type', 'PAYMENT', '付款凭证', 'Payment Voucher', 1, 'VOUCHER', 1, 1, -1),
('voucher_type', 'RECEIPT', '收款凭证', 'Receipt Voucher', 2, 'VOUCHER', 1, 1, -1),
('voucher_type', 'TRANSFER', '转账凭证', 'Transfer Voucher', 3, 'VOUCHER', 1, 1, -1),
('voucher_type', 'REVERSAL', '冲账凭证', 'Reversal Voucher', 4, 'VOUCHER', 1, 1, -1),
('voucher_type', 'PERIOD_END', '期末结转凭证', 'Period-End Transfer', 5, 'VOUCHER', 1, 1, -1),
('voucher_type', 'GENERAL', '记账凭证', 'General Voucher', 6, 'VOUCHER', 1, 1, -1);

-- 2. 支付渠道字典（pay_channel）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('pay_channel', 'CASH', '现金', 'Cash', 1, 'CHANNEL', 1, 1, -1),
('pay_channel', 'ALIPAY', '支付宝', 'Alipay', 2, 'CHANNEL', 1, 1, -1),
('pay_channel', 'WECHAT', '微信支付', 'WeChat Pay', 3, 'CHANNEL', 1, 1, -1),
('pay_channel', 'BANK', '银行卡/网银', 'Bank Transfer', 4, 'CHANNEL', 1, 1, -1),
('pay_channel', 'UNIONPAY', '银联在线', 'UnionPay', 5, 'CHANNEL', 1, 1, -1),
('pay_channel', 'INTERNAL', '内部清算', 'Internal Clearing', 6, 'CHANNEL', 1, 1, -1);

-- 3. 常用交易编码字典（trading_code）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('trading_code', 'CASH_PAY', '现金付款', 'Cash Payment', 1, 'TRADE', 1, 1, -1),
('trading_code', 'LOAN_DISBURSE', '贷款放款', 'Loan Disbursement', 2, 'TRADE', 1, 1, -1),
('trading_code', 'LOAN_REPAY', '贷款还款', 'Loan Repayment', 3, 'TRADE', 1, 1, -1),
('trading_code', 'TRANSFER', '资金转账', 'Fund Transfer', 4, 'TRADE', 1, 1, -1),
('trading_code', 'FEE_DEDUCT', '手续费扣收', 'Fee Deduction', 5, 'TRADE', 1, 1, -1),
('trading_code', 'WITHDRAW', '商户/个人提现', 'Withdrawal', 6, 'TRADE', 1, 1, -1);

-- 4. 交易款项类型字典（funds_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('funds_type', 'PRINCIPAL', '本金款项', 'Principal', 1, 'FUNDS', 1, 1, -1),
('funds_type', 'INTEREST', '利息款项', 'Interest', 2, 'FUNDS', 1, 1, -1),
('funds_type', 'PENALTY', '罚息/违约金', 'Penalty', 3, 'FUNDS', 1, 1, -1),
('funds_type', 'FEE', '服务费/手续费', 'Fee', 4, 'FUNDS', 1, 1, -1),
('funds_type', 'TAX', '税费支出', 'Tax', 5, 'FUNDS', 1, 1, -1),
('funds_type', 'DEPOSIT', '风险保证金', 'Deposit', 6, 'FUNDS', 1, 1, -1),
('funds_type', 'DISCOUNT', '营销立减/贴息', 'Discount', 7, 'FUNDS', 1, 1, -1);

-- 5. 辅助核算类型字典（auxiliary_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('auxiliary_type', 'CUSTOMER', '客户', 'Customer', 1, 'AUX', 1, 1, -1),
('auxiliary_type', 'SUPPLIER', '供应商辅助', 'Supplier', 2, 'AUX', 1, 1, -1),
('auxiliary_type', 'DEPARTMENT', '部门辅助', 'Department', 3, 'AUX', 1, 1, -1),
('auxiliary_type', 'PROJECT', '项目辅助', 'Project', 4, 'AUX', 1, 1, -1),
('auxiliary_type', 'EMPLOYEE', '员工/经办人辅助', 'Employee', 5, 'AUX', 1, 1, -1);
