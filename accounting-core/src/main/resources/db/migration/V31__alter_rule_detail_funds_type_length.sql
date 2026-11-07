-- accounting-core/src/main/resources/db/migration/V31__alter_rule_detail_funds_type_length.sql
-- Flyway V31: 扩充记账规则明细表 t_accounting_rule_detail 中 funds_type 字段长度至 128 位以支持多款项逗号分隔配置

USE `accounting`;

ALTER TABLE `t_accounting_rule_detail`
    MODIFY COLUMN `funds_type` VARCHAR(128) NOT NULL COMMENT '交易款项类型(字典CODE，支持多款项逗号分隔)';
