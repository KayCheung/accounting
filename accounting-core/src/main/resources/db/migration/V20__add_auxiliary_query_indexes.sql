-- accounting-core/src/main/resources/db/migration/V20__add_auxiliary_query_indexes.sql
-- Flyway V20：辅助核算项目记录表补充查询优化索引

USE `accounting`;

ALTER TABLE t_accounting_voucher_auxiliary
    ADD INDEX idx_aux_type_code (aux_type, aux_code),
    ADD INDEX idx_aux_subject_code (subject_code);
