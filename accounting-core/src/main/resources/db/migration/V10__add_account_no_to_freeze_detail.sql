-- accounting-core/src/main/resources/db/migration/V10__add_account_no_to_freeze_detail.sql
-- Flyway V10：账户资金冻结明细表补充 account_no 字段

USE `accounting`;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'account_no';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN account_no VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''账户编号'' AFTER id',
    'SELECT ''Column account_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND index_name = 'idx_account_no';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD INDEX idx_account_no (account_no)',
    'SELECT ''Index idx_account_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
