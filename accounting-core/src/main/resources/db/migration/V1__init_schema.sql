-- accounting-core/src/main/resources/db/migration/V1__init_schema.sql
-- Flyway V1：数据库初始化（字符集/排序规则）
-- 对应 docs/sql/0-database-schema.sql

CREATE DATABASE IF NOT EXISTS `accounting`
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;
