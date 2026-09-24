USE `accounting`;

-- Step 7 补充：为 t_buffer_posting_rule 添加 status 字段
-- 原 DDL 缺少状态列，但业务逻辑（停用、重叠检测）需要状态管理。
ALTER TABLE t_buffer_posting_rule
    ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-待启用；2-启用，3-停用'
    AFTER debit_credit;
