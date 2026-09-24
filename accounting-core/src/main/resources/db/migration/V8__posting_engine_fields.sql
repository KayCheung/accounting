-- accounting-core/src/main/resources/db/migration/V8__posting_engine_fields.sql
-- Flyway V8：过账引擎新增字段（Step 9/10/12 补充）
-- 修复 PO 与 DDL 不一致导致的运行时 SQLSyntaxErrorException

USE `accounting`;

-- t_accounting_voucher：过账治理字段（Step 12 P0-8）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'fail_reason';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN fail_reason VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''过账失败原因'' AFTER reviewer_name',
    'SELECT ''Column fail_reason already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'retry_count';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''手动重试次数'' AFTER fail_reason',
    'SELECT ''Column retry_count already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'skip_flag';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN skip_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''跳过标记：0-未跳过,1-人工跳过'' AFTER retry_count',
    'SELECT ''Column skip_flag already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_accounting_voucher_entry：单边/缓冲/方向字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'is_unilateral';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN is_unilateral TINYINT NOT NULL DEFAULT 0 COMMENT ''是否单边记账：0-否,1-是'' AFTER status',
    'SELECT ''Column is_unilateral already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'is_buffered';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN is_buffered TINYINT NOT NULL DEFAULT 0 COMMENT ''是否缓冲入账：0-否,1-是'' AFTER is_unilateral',
    'SELECT ''Column is_buffered already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'change_direction';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN change_direction TINYINT NOT NULL COMMENT ''增减方向：1-增,2-减'' AFTER is_buffered',
    'SELECT ''Column change_direction already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_business_detail：款项明细编码字段（Step 9）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_business_detail' AND column_name = 'item_code';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_business_detail ADD COLUMN item_code VARCHAR(32) NOT NULL COMMENT ''款项明细编码（字典CODE）'' AFTER funds_type',
    'SELECT ''Column item_code already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
