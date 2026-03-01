-- ========================================
-- 数据库回滚脚本 V2
-- 日期: 2026-03-01
-- 说明: 回滚 4-schema-adjustment-20260301-v2.sql 的所有变更
-- ========================================

USE `accounting`;

-- ========================================
-- 回滚修复 6: orig_voucher_no 恢复为非空
-- ========================================

ALTER TABLE t_accounting_voucher 
MODIFY COLUMN orig_voucher_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '原凭证号（红冲凭证关联原凭证）';

-- ========================================
-- 回滚修复 5: 恢复凭证状态枚举值
-- ========================================

ALTER TABLE t_accounting_voucher 
MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败';

-- ========================================
-- 回滚修复 4: 恢复事务状态枚举和字段
-- ========================================

-- 恢复事务状态注释
ALTER TABLE t_transaction 
MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '事务状态：1-未提交,2-部分提交,3-全部提交,4-部分回滚,5-全部回滚,6-失败';

-- 恢复删除的字段
ALTER TABLE t_transaction 
ADD COLUMN total_entry_count INT NOT NULL DEFAULT 0 COMMENT '本次事务总记账明细条数' AFTER accounting_date,
ADD COLUMN success_entry_count INT NOT NULL DEFAULT 0 COMMENT '已成功记账的明细条数' AFTER total_entry_count,
ADD COLUMN pending_entry_count INT NOT NULL DEFAULT 0 COMMENT '处理中/未提交的明细条数' AFTER success_entry_count,
ADD COLUMN fail_entry_count INT NOT NULL DEFAULT 0 COMMENT '记账失败的明细条数' AFTER pending_entry_count;

-- ========================================
-- 回滚修复 2: 删除缓冲明细表的会计日期字段
-- ========================================

-- 恢复原索引
ALTER TABLE t_buffer_posting_detail 
DROP INDEX idx_accounting_date_status,
ADD INDEX idx_status (status, account_no);

-- 删除会计日期字段
ALTER TABLE t_buffer_posting_detail 
DROP COLUMN accounting_date;

-- ========================================
-- 回滚新需求 1: 删除期末结转相关表
-- ========================================

DROP TABLE IF EXISTS t_period_end_transfer_record;
DROP TABLE IF EXISTS t_period_end_transfer_rule;

-- ========================================
-- 回滚修复 1: 删除本地消息表
-- ========================================

DROP TABLE IF EXISTS t_local_message;

-- ========================================
-- 回滚说明
-- ========================================

/*
回滚内容：
1. 删除本地消息表（t_local_message）
2. 删除缓冲明细表的会计日期字段
3. 恢复事务状态枚举和相关字段
4. 恢复凭证状态枚举值
5. 恢复 orig_voucher_no 为非空
6. 删除期末结转相关表

注意事项：
- 回滚前请确认是否有数据依赖
- 回滚后需要同步更新业务代码
*/
