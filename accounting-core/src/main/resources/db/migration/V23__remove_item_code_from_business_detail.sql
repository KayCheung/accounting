USE `accounting`;

-- V23: 业务流水明细模型精简（移除冗余的 item_code，唯一索引改为 funds_type 保证幂等）

-- 1. 安全处理唯一索引：若已存在旧 uk_trace_no 则先删除
SET @schema_name = COALESCE(DATABASE(), 'accounting');

SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND index_name = 'uk_trace_no';
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `t_business_detail` DROP INDEX `uk_trace_no`',
    'SELECT ''Index uk_trace_no does not exist, skip drop'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 安全创建基于 funds_type 的唯一索引
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND index_name = 'uk_trace_no';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `t_business_detail` ADD UNIQUE KEY `uk_trace_no` (`trace_no`, `trace_seq`, `customer_id`, `funds_type`)',
    'SELECT ''Index uk_trace_no already exists, skip add'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 安全删除冗余字段 item_code
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND column_name = 'item_code';
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `t_business_detail` DROP COLUMN `item_code`',
    'SELECT ''Column item_code already removed, skip drop'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
