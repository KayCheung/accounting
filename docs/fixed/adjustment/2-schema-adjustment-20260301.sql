-- ========================================
-- 数据库设计调整脚本
-- 执行日期：2026-03-01
-- 基于业务逻辑澄清的数据库调整
-- 说明：本脚本包含字段调整、索引优化等内容
-- ========================================

USE `accounting`;

-- ========================================
-- 第一部分：备份关键表（可选，建议在生产环境执行）
-- ========================================

-- CREATE TABLE t_accounting_rule_backup_20260301 AS SELECT * FROM t_accounting_rule;
-- CREATE TABLE t_business_record_backup_20260301 AS SELECT * FROM t_business_record;
-- CREATE TABLE t_accounting_voucher_backup_20260301 AS SELECT * FROM t_accounting_voucher;
-- CREATE TABLE t_transaction_backup_20260301 AS SELECT * FROM t_transaction;
-- CREATE TABLE t_buffer_posting_detail_backup_20260301 AS SELECT * FROM t_buffer_posting_detail;

-- ========================================
-- 第二部分：字段调整
-- ========================================

-- 1. 删除 t_accounting_rule.accounting_mode 字段
-- 原因：单边记账的判断应该在分录级别（is_unilateral），而不是规则级别
ALTER TABLE t_accounting_rule DROP COLUMN IF EXISTS accounting_mode;

-- 2. 新增 t_business_record.accounting_date 字段
-- 原因：会计日期在记账接口调用时确定，需要记录在业务记账流水表中
ALTER TABLE t_business_record ADD COLUMN IF NOT EXISTS accounting_date DATE NULL COMMENT '会计日期';

-- 更新历史数据（根据 trade_time 推算会计日期）
UPDATE t_business_record SET accounting_date = DATE(trade_time) WHERE accounting_date IS NULL;

-- 修改字段为 NOT NULL
ALTER TABLE t_business_record MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';

-- 3. 调整 t_accounting_voucher.status 枚举值注释
-- 原因：红冲逻辑需要标记原凭证为"已冲销"状态（status=4）
ALTER TABLE t_accounting_voucher MODIFY COLUMN status TINYINT NOT NULL DEFAULT '1' 
  COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销';

-- 处理历史数据：将原来的"过账失败"状态改为"未过账"
-- UPDATE t_accounting_voucher SET status = 1 WHERE status = 4;

-- 4. 新增 t_transaction.accounting_date 字段
-- 原因：日切流程需要根据会计日期处理存量事务
ALTER TABLE t_transaction ADD COLUMN IF NOT EXISTS accounting_date DATE NULL COMMENT '会计日期';

-- 更新历史数据（从 t_business_record 关联获取）
UPDATE t_transaction t 
INNER JOIN t_business_record b ON t.trace_no = b.trace_no 
SET t.accounting_date = b.accounting_date 
WHERE t.accounting_date IS NULL;

-- 修改字段为 NOT NULL
ALTER TABLE t_transaction MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';

-- ========================================
-- 第三部分：索引优化
-- ========================================

-- 1. t_business_record 新增会计日期索引
-- 原因：日切流程需要根据会计日期查询业务记账流水
ALTER TABLE t_business_record ADD KEY IF NOT EXISTS idx_accounting_date (accounting_date, status);

-- 2. t_buffer_posting_detail 新增日切相关索引
-- 原因：日切流程需要扫描指定会计日期且状态为"待入账"的缓冲明细
ALTER TABLE t_buffer_posting_detail ADD KEY IF NOT EXISTS idx_accounting_date_status (accounting_date, status, account_no);

-- 3. t_transaction 新增日切相关索引
-- 原因：日切流程需要扫描指定会计日期且状态为"处理中"的事务
ALTER TABLE t_transaction ADD KEY IF NOT EXISTS idx_accounting_date_status (accounting_date, status);

-- 4. t_accounting_voucher 新增红冲查询索引
-- 原因：红冲逻辑需要查询是否已存在红冲凭证（通过 orig_voucher_no 查询）
ALTER TABLE t_accounting_voucher ADD KEY IF NOT EXISTS idx_orig_voucher_no (orig_voucher_no);

-- ========================================
-- 第四部分：数据完整性验证
-- ========================================

-- 验证 t_business_record.accounting_date
SELECT '验证 t_business_record.accounting_date' AS check_item, 
       COUNT(*) AS total, 
       SUM(CASE WHEN accounting_date IS NULL THEN 1 ELSE 0 END) AS null_count
FROM t_business_record;

-- 验证 t_transaction.accounting_date
SELECT '验证 t_transaction.accounting_date' AS check_item, 
       COUNT(*) AS total, 
       SUM(CASE WHEN accounting_date IS NULL THEN 1 ELSE 0 END) AS null_count
FROM t_transaction;

-- 验证 t_accounting_voucher.status
SELECT '验证 t_accounting_voucher.status' AS check_item, 
       status, 
       COUNT(*) AS count
FROM t_accounting_voucher
GROUP BY status;

-- 验证索引是否创建成功
SELECT '验证索引创建' AS check_item,
       TABLE_NAME,
       INDEX_NAME,
       COLUMN_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'accounting'
  AND TABLE_NAME IN ('t_business_record', 't_buffer_posting_detail', 't_transaction', 't_accounting_voucher')
  AND INDEX_NAME IN ('idx_accounting_date', 'idx_accounting_date_status', 'idx_orig_voucher_no')
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;

-- ========================================
-- 调整完成
-- ========================================

SELECT '数据库调整完成' AS message, NOW() AS completion_time;
