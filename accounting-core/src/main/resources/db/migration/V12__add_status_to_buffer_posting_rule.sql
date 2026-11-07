-- accounting-core/src/main/resources/db/migration/V12__add_status_to_buffer_posting_rule.sql
-- Flyway V12：缓冲入账规则表补充 status 字段、默认值及初始示范数据

USE `accounting`;

-- 1. 幂等为 t_buffer_posting_rule 表补充 status 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_buffer_posting_rule' AND column_name = 'status';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_buffer_posting_rule ADD COLUMN status TINYINT NOT NULL DEFAULT 1 COMMENT ''状态：1-待启用，2-启用，3-停用'' AFTER debit_credit',
    'SELECT ''Column status already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 为 subject_code 和 account_no 补充默认值为空字符串（支持只填账号或只填科目的缓冲规则）
ALTER TABLE t_buffer_posting_rule MODIFY COLUMN subject_code VARCHAR(32) NOT NULL DEFAULT '' COMMENT '会计科目编码，与账户编号必须有一个不为空';
ALTER TABLE t_buffer_posting_rule MODIFY COLUMN account_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号，与会计科目必须有一个不为空';

-- 3. 插入演示缓冲入账规则（若不存在）
INSERT IGNORE INTO t_buffer_posting_rule (
    id, rule_name, buffer_mode, business_code, trading_code, pay_channel,
    subject_code, account_no, debit_credit, status, effective_time, expiration_time,
    create_id, create_name, update_id, update_name, tenant_id
) VALUES
(101, '现金付款-异步逐条削峰规则', 1, 'PAYMENT', 'CASH_PAY', 'CASH', '100101', '', 1, 2, '2026-01-01 00:00:00', '2099-12-31 23:59:59', 'system', 'system', 'system', 'system', -1),
(102, '贷款放款-日间批量缓冲规则', 2, 'LOAN', 'LOAN_DISBURSE', 'BANK', '', '00120260301000001', 2, 2, '2026-01-01 00:00:00', '2099-12-31 23:59:59', 'system', 'system', 'system', 'system', -1);
