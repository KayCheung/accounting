-- ========================================
-- 数据库设计调整回滚脚本
-- 执行日期：2026-03-01
-- 说明：如果调整后发现问题，可以使用本脚本回滚
-- 警告：回滚前请确认业务影响，建议在非生产环境测试
-- ========================================

USE `accounting`;

-- ========================================
-- 第一部分：删除新增的索引
-- ========================================

-- 1. 删除 t_business_record 的会计日期索引
ALTER TABLE t_business_record DROP KEY IF EXISTS idx_accounting_date;

-- 2. 删除 t_buffer_posting_detail 的日切相关索引
ALTER TABLE t_buffer_posting_detail DROP KEY IF EXISTS idx_accounting_date_status;

-- 3. 删除 t_transaction 的日切相关索引
ALTER TABLE t_transaction DROP KEY IF EXISTS idx_accounting_date_status;

-- 4. 删除 t_accounting_voucher 的红冲查询索引
ALTER TABLE t_accounting_voucher DROP KEY IF EXISTS idx_orig_voucher_no;

-- ========================================
-- 第二部分：删除新增的字段
-- ========================================

-- 1. 删除 t_business_record.accounting_date 字段
ALTER TABLE t_business_record DROP COLUMN IF EXISTS accounting_date;

-- 2. 删除 t_transaction.accounting_date 字段
ALTER TABLE t_transaction DROP COLUMN IF EXISTS accounting_date;

-- ========================================
-- 第三部分：恢复字段注释
-- ========================================

-- 1. 恢复 t_accounting_voucher.status 枚举值注释
ALTER TABLE t_accounting_voucher MODIFY COLUMN status TINYINT NOT NULL DEFAULT '1' 
  COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败';

-- ========================================
-- 第四部分：恢复 t_accounting_rule.accounting_mode 字段（可选）
-- ========================================

-- 注意：如果需要恢复此字段，需要从备份表中恢复数据
-- ALTER TABLE t_accounting_rule ADD COLUMN accounting_mode TINYINT NOT NULL DEFAULT '1' COMMENT '记账模式：1-实时,2-异步';

-- 从备份表恢复数据（如果备份表存在）
-- UPDATE t_accounting_rule t
-- INNER JOIN t_accounting_rule_backup_20260301 b ON t.id = b.id
-- SET t.accounting_mode = b.accounting_mode;

-- ========================================
-- 第五部分：验证回滚结果
-- ========================================

-- 验证字段是否已删除
SELECT '验证字段删除' AS check_item,
       TABLE_NAME,
       COLUMN_NAME
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_SCHEMA = 'accounting'
  AND TABLE_NAME IN ('t_business_record', 't_transaction')
  AND COLUMN_NAME = 'accounting_date';

-- 验证索引是否已删除
SELECT '验证索引删除' AS check_item,
       TABLE_NAME,
       INDEX_NAME
FROM INFORMATION_SCHEMA.STATISTICS
WHERE TABLE_SCHEMA = 'accounting'
  AND TABLE_NAME IN ('t_business_record', 't_buffer_posting_detail', 't_transaction', 't_accounting_voucher')
  AND INDEX_NAME IN ('idx_accounting_date', 'idx_accounting_date_status', 'idx_orig_voucher_no')
GROUP BY TABLE_NAME, INDEX_NAME;

-- ========================================
-- 回滚完成
-- ========================================

SELECT '数据库回滚完成' AS message, NOW() AS completion_time;
