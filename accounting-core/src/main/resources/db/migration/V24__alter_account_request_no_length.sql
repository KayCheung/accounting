-- accounting-core/src/main/resources/db/migration/V24__alter_account_request_no_length.sql
-- Flyway V24：扩充 t_account 表 request_no 字段长度至 64 位，保持与 trace_no 及开户契约一致

USE `accounting`;

ALTER TABLE `t_account` MODIFY COLUMN `request_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '开户请求号';
