-- accounting-core/src/main/resources/db/migration/V15__voucher_no_prefix.sql
-- Flyway V15：凭证类型扩展属性补充流水号前缀（voucherNoPrefix: REC, PAY, TRF, ADJ, REV, PET, VOU）

USE `accounting`;

UPDATE t_dictionary 
SET ext_json = '{"prefix":"收","voucherNoPrefix":"REC","title":"收款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'RECEIPT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"付","voucherNoPrefix":"PAY","title":"付款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PAYMENT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"转","voucherNoPrefix":"TRF","title":"转账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'TRANSFER';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"冲","voucherNoPrefix":"REV","title":"冲账凭证","tradeType":3,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'REVERSAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"结","voucherNoPrefix":"PET","title":"期末结转凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PERIOD_END';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"记","voucherNoPrefix":"VOU","title":"记账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'GENERAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"调","voucherNoPrefix":"ADJ","title":"调账凭证","tradeType":2,"tradingCode":"ADJUST","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'ADJUST';
