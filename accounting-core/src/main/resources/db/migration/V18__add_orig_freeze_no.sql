-- accounting-core/src/main/resources/db/migration/V18__add_orig_freeze_no.sql
-- Flyway V18：t_business_record 增加 orig_freeze_no 字段

ALTER TABLE t_business_record
    ADD COLUMN orig_freeze_no VARCHAR(64) DEFAULT NULL COMMENT '关联预冻结单号(freeze_id)' AFTER summary;
