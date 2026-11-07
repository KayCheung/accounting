-- accounting-core/src/main/resources/db/migration/V16__add_freeze_detail_ext_amounts.sql
-- Flyway V16：账户资金冻结明细表补充初始冻结金额、累计已解冻金额、累计已扣款金额扩展字段

USE `accounting`;

-- 1. 增加 orig_freeze_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'orig_freeze_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN orig_freeze_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''初始冻结金额'' AFTER freeze_amount',
    'SELECT ''Column orig_freeze_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 增加 unfrozen_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'unfrozen_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN unfrozen_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''累计已解冻金额'' AFTER orig_freeze_amount',
    'SELECT ''Column unfrozen_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 增加 deducted_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'deducted_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN deducted_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''累计已扣款金额'' AFTER unfrozen_amount',
    'SELECT ''Column deducted_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 历史存量数据平滑订正：orig_freeze_amount 回填当前 freeze_amount
UPDATE t_account_freeze_detail
SET orig_freeze_amount = freeze_amount
WHERE orig_freeze_amount = 0;
