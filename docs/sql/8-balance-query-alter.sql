USE `accounting`;

-- ========================================
-- Step 15: Balance Query API
-- t_account_freeze_detail 补充 account_no 字段（便于按账户查询冻结记录）
-- ========================================

-- 为 t_account_freeze_detail 增加 account_no 字段
ALTER TABLE t_account_freeze_detail
    ADD COLUMN account_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号' AFTER voucher_no;

-- 增加索引
ALTER TABLE t_account_freeze_detail
    ADD INDEX idx_account_no (account_no, status);
