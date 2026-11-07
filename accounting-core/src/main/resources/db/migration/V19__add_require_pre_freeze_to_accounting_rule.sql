-- accounting-core/src/main/resources/db/migration/V19__add_require_pre_freeze_to_accounting_rule.sql
-- Flyway V19：记账规则主表扩展“需先预冻结”控制字段

USE `accounting`;

ALTER TABLE t_accounting_rule
    ADD COLUMN require_pre_freeze TINYINT NOT NULL DEFAULT 0 COMMENT '是否需先预冻结：0-否；1-是' AFTER freeze_duration;
