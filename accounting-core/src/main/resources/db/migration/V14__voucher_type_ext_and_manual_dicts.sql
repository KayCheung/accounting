-- accounting-core/src/main/resources/db/migration/V14__voucher_type_ext_and_manual_dicts.sql
-- Flyway V14：凭证类型扩展属性补充（字头/表头/默认交易编码）与手工记账合法字典项初始化

USE `accounting`;

-- 1. 凭证类型字典补充扩展属性 JSON（ext_json）
UPDATE t_dictionary 
SET ext_json = '{"prefix":"收","title":"收款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'RECEIPT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"付","title":"付款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PAYMENT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"转","title":"转账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'TRANSFER';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"冲","title":"冲账凭证","tradeType":3,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'REVERSAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"结","title":"期末结转凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PERIOD_END';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"记","title":"记账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'GENERAL';

-- 2. 新增调账凭证字典项（voucher_type = ADJUST）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, ext_json, tenant_id) VALUES
('voucher_type', 'ADJUST', '调账凭证', 'Adjustment Voucher', 7, 'VOUCHER', 1, 1, '{"prefix":"调","title":"调账凭证","tradeType":2,"tradingCode":"ADJUST","payChannel":"INTERNAL"}', -1);

-- 3. 补充业务线字典（business_code = MANUAL）与交易编码字典（trading_code = ADJUST）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, ext_json, tenant_id) VALUES
('business_code', 'MANUAL', '手工记账', 'Manual Accounting', 99, 'MANUAL', 1, 1, '', -1),
('trading_code', 'ADJUST', '账务调整', 'Account Adjustment', 99, 'TRADE', 1, 1, '', -1);
