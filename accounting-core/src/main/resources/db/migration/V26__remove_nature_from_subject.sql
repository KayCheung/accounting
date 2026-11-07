-- accounting-core/src/main/resources/db/migration/V26__remove_nature_from_subject.sql
-- Flyway V26: 移除会计科目表 t_account_subject 中冗余且存在歧义的 nature 字段

USE `accounting`;

SET @schema_name = COALESCE(DATABASE(), 'accounting');

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 't_account_subject' AND column_name = 'nature';
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `t_account_subject` DROP COLUMN `nature`',
    'SELECT ''Column nature already removed, skip drop'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
