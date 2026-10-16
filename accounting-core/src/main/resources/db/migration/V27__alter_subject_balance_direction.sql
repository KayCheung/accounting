-- accounting-core/src/main/resources/db/migration/V27__alter_subject_balance_direction.sql
-- Flyway V27: 扩展科目余额方向（0-无(表外), 1-借, 2-贷, 3-双向），数据订正表外科目余额方向为0

USE `accounting`;

-- 1. 更新会计科目表 t_account_subject 余额方向字段注释
ALTER TABLE `t_account_subject`
    MODIFY COLUMN `debit_credit` TINYINT NOT NULL COMMENT '余额方向：0-无(表外), 1-借, 2-贷, 3-双向';

-- 数据订正：历史表外科目 (subject_category = 0) 的余额方向统一订正为 0 (无)
UPDATE `t_account_subject` SET `debit_credit` = 0 WHERE `subject_category` = 0;

-- 2. 更新账户表 t_account 余额方向字段注释（支持 0-平/无, 1-借, 2-贷）
ALTER TABLE `t_account`
    MODIFY COLUMN `balance_direction` TINYINT NOT NULL COMMENT '余额方向：0-平/无, 1-借, 2-贷';

-- 3. 更新子账户表 t_sub_account 余额方向字段注释
ALTER TABLE `t_sub_account`
    MODIFY COLUMN `balance_direction` TINYINT NOT NULL COMMENT '余额方向：0-平/无, 1-借, 2-贷';
