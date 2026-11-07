-- accounting-core/src/main/resources/db/migration/V17__add_transfer_rule_cycle_fields.sql
-- Flyway V17：期末结转规则表补充是否支持自动结转与结转周期字段

USE `accounting`;

-- 1. 增加 auto_transfer 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND column_name = 'auto_transfer';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD COLUMN auto_transfer TINYINT NOT NULL DEFAULT 1 COMMENT ''是否支持自动结转：0-仅限手动,1-支持自动'' AFTER status',
    'SELECT ''Column auto_transfer already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 增加 period_cycle 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND column_name = 'period_cycle';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD COLUMN period_cycle TINYINT NOT NULL DEFAULT 2 COMMENT ''结转周期：1-每日,2-月末,3-季末,4-年末,5-仅手动'' AFTER auto_transfer',
    'SELECT ''Column period_cycle already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 增加索引以加速定时任务与日切筛选可自动结转规则
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND index_name = 'idx_auto_cycle';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD INDEX idx_auto_cycle (status, auto_transfer, period_cycle)',
    'SELECT ''Index idx_auto_cycle already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
