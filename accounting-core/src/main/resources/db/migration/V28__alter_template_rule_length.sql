-- accounting-core/src/main/resources/db/migration/V28__alter_template_rule_length.sql
-- Flyway V28: 扩充开户模板表 t_account_template 中账号与户名生成规则字段长度至 255 位以容纳长宏变量组合

USE `accounting`;

ALTER TABLE `t_account_template`
    MODIFY COLUMN `acct_no_rule` VARCHAR(255) NOT NULL COMMENT '账户编号生成规则',
    MODIFY COLUMN `acct_name_rule` VARCHAR(255) NOT NULL COMMENT '账户名称生成规则';
