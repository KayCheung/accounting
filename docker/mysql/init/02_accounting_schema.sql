-- 02_accounting_schema.sql
USE `accounting`;


-- ==================== Migration: V1__init_schema.sql ====================

-- accounting-core/src/main/resources/db/migration/V1__init_schema.sql
-- Flyway V1：数据库初始化（字符集/排序规则）
-- 对应 docs/sql/0-database-schema.sql

CREATE DATABASE IF NOT EXISTS `accounting`
    CHARACTER SET = utf8mb4
    COLLATE = utf8mb4_unicode_ci;

-- ==================== Migration: V2__account.sql ====================

-- accounting-core/src/main/resources/db/migration/V2__account.sql
-- Flyway V2：账户域（7 张表）
-- 对应 docs/sql/1-account.sql

USE `accounting`;

-- 账户表
CREATE TABLE t_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    owner_id VARCHAR(64) NOT NULL COMMENT '所有者ID，如果时内部账户，默认为 INNER',
    owner_type TINYINT NOT NULL COMMENT '所有者类型：1-个人,2-企业,99-其他',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    account_name VARCHAR(32) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型(字典CODE)，如：BASIC-基本户,PEND_SET-待结算户,LOAN_PRI-贷款本金账户,LOAN_INT-贷款利息账户,LOAN_GUA-贷款担保费账户',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    opening_balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '期初余额',
    balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '余额',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '账户状态：1-正常,2-冻结,3-注销',
    risk_status TINYINT NOT NULL DEFAULT '1' COMMENT '风控状态：1-正常,2-止入,3-止出,4-止入止出',
    request_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '开户请求号',
    open_date DATE NOT NULL COMMENT '开户日期',
    inactive_date DATE NOT NULL DEFAULT '1970-01-01' COMMENT '动支日期',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_account_no (account_no),
    UNIQUE KEY uk_owner_id (owner_id,subject_code),
    KEY idx_open_date (open_date,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- 子账户表
CREATE TABLE t_sub_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
    balance_type TINYINT NOT NULL COMMENT '余额类型：1-可用余额,2-冻结余额',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '余额',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_account_no (account_no,balance_type,balance_direction)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子账户表';

-- 账户明细表
CREATE TABLE t_account_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，结合trace_no实现幂等',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    pre_balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '交易前余额',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    post_balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '交易后余额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no,entry_id),
    KEY idx_accounting_date (accounting_date,account_no),
    KEY idx_txn_no (txn_no),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户明细表';

-- 子账户明细表
CREATE TABLE t_sub_account_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，结合trace_no实现幂等',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    balance_type TINYINT NOT NULL COMMENT '余额类型：1-可用余额,2-冻结余额',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    pre_balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '交易前余额',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    post_balance DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '交易后余额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no,entry_id),
    KEY idx_accounting_date (accounting_date,account_no),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子账户明细表';

-- 账户资金冻结明细表
CREATE TABLE `t_account_freeze_detail` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，结合trace_no实现幂等',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    freeze_amount DECIMAL(18,6) NOT NULL COMMENT '冻结金额',
    status TINYINT NOT NULL COMMENT '状态：1-冻结,2-已解冻',
    expire_time DATETIME NOT NULL DEFAULT '2099-12-31 00:00:00' COMMENT '冻结过期时间，2099 表示永不过期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no) USING BTREE,
    KEY idx_account_no (account_no),
    KEY idx_account_expire (expire_time, status),
    KEY idx_trade_time (`trade_time`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金冻结明细表';

-- 账户日余额表（按年分区）
CREATE TABLE t_account_balance (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    accounting_date DATE NOT NULL COMMENT '会计日（按日汇总）',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    begin_balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '期初余额（当日0点）',
    debit_amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '当日借方发生额',
    credit_amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '当日贷方发生额',
    end_balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '期末余额（当日24点）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    PRIMARY KEY (id, accounting_date),
    UNIQUE KEY uk_balance_dim (accounting_date, account_no, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户日余额表';

ALTER TABLE t_account_balance
PARTITION BY RANGE COLUMNS(accounting_date) (
    PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax  VALUES LESS THAN (MAXVALUE)
);

-- 账户余额快照表（按年分区）
CREATE TABLE t_account_balance_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    snapshot_date DATE NOT NULL COMMENT '快照日期',
    snapshot_type TINYINT NOT NULL COMMENT '快照类型：1-DAY,2-MONTH,3-YEAR,4-CUSTOM',
    snapshot_time DATETIME NOT NULL COMMENT '快照生成时间',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '快照时账户余额',
    ext_json VARCHAR(255) NOT NULL DEFAULT '' COMMENT '扩展统计信息',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    PRIMARY KEY (id, snapshot_date),
    UNIQUE KEY uk_snapshot_dim (snapshot_date, snapshot_type, account_no, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额快照表（周期快照）';

ALTER TABLE t_account_balance_snapshot
PARTITION BY RANGE COLUMNS(snapshot_date) (
    PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax  VALUES LESS THAN (MAXVALUE)
);

-- ==================== Migration: V3__voucher.sql ====================

-- accounting-core/src/main/resources/db/migration/V3__voucher.sql
-- Flyway V3：凭证域（4 张表）
-- 对应 docs/sql/2-voucher.sql

USE `accounting`;

-- 记账凭证表
CREATE TABLE t_accounting_voucher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    txn_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '事务编号(凭证先行，事务编号在凭证生成后补充)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，结合trace_no实现幂等',
    voucher_type VARCHAR(32) NOT NULL COMMENT '凭证类型(字典CODE)，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证',
    posting_type TINYINT NOT NULL COMMENT '入账类型：1-手工凭证,2-机制凭证',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    amount DECIMAL(18,6) NOT NULL COMMENT '金额',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败,5-已冲销',
    post_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '过账时间',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    attachment_count INT NOT NULL DEFAULT 0 COMMENT '附件数',
    orig_voucher_no VARCHAR(32) NULL DEFAULT NULL COMMENT '原凭证号（红冲凭证关联原凭证）',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    bookkeeper_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
    reviewer_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '复核人姓名',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no),
    UNIQUE KEY uk_trace_no (trace_no,trace_seq),
    KEY idx_orig_voucher_no (orig_voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证表';

-- 分录流水表
CREATE TABLE t_accounting_voucher_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    row_num INT NOT NULL COMMENT '分录行号',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '记账汇率',
    unit_price DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单价',
    quantity INT NOT NULL DEFAULT 0 COMMENT '数量',
    pricing_unit VARCHAR(32) NOT NULL DEFAULT '' COMMENT '计价单位',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '分录明细状态：1-未过账,2-已过账,3-过账失败',
    accounting_date DATE NOT NULL COMMENT '会计日',
    balance_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '余额更新时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_entry_id (entry_id),
    KEY idx_voucher_no (voucher_no),
    KEY idx_accounting_date (accounting_date,account_no,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分录流水表';

-- 记账凭证辅助核算项目
CREATE TABLE t_accounting_voucher_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    aux_type VARCHAR(32) NOT NULL COMMENT '辅助核算类型(字典CODE)，如 DEPT/CUSTOMER/PROJECT',
    aux_code VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码(字典CODE)',
    aux_name VARCHAR(64) NOT NULL COMMENT '辅助核算项目名称',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减',
    amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '金额',
    accounting_date DATE NOT NULL COMMENT '会计日',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_entry_id (entry_id,aux_code),
    KEY idx_voucher_no (voucher_no),
    KEY idx_accounting_date (accounting_date,aux_type,aux_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证辅助核算项目';

-- 记账凭证附件表
CREATE TABLE t_accounting_voucher_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    file_path VARCHAR(255) NOT NULL COMMENT '附件地址',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    KEY idx_voucher_no (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证附件表';

-- ==================== Migration: V4__rule.sql ====================

-- accounting-core/src/main/resources/db/migration/V4__rule.sql
-- Flyway V4：规则域（5 张表）
-- 对应 docs/sql/3-rule.sql

USE `accounting`;

-- 记账规则表
CREATE TABLE t_accounting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    voucher_type VARCHAR(32) NOT NULL COMMENT '凭证类型(字典CODE)',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    is_open_account TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许自动开户：0-否；1-是',
    freeze_duration INT NOT NULL DEFAULT 0 COMMENT '冻结时长，单位：秒',
    pre_rule_id BIGINT NOT NULL DEFAULT 0 COMMENT '前置入账规则ID',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-待启用；2-启用，3-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_accounting_rule (business_code,trading_code,pay_channel,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则表';

-- 记账规则明细表
CREATE TABLE t_accounting_rule_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '记账规则ID',
    row_num INT NOT NULL COMMENT '凭证分录行号',
    funds_type VARCHAR(32) NOT NULL COMMENT '交易款项类型(字典CODE)',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_scope TINYINT NOT NULL COMMENT '账户作用域：1-内部分户；2-外部分户',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    is_unilateral TINYINT NOT NULL DEFAULT 0 COMMENT '是否实时更新账户余额：0-否；1-是',
    extend_script LONGTEXT NOT NULL COMMENT 'SpEL扩展脚本（启动时预加载，规则变更时同步更新）',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_rule_id (rule_id, row_num, is_delete),
    UNIQUE KEY uk_subject_code (rule_id, subject_code, funds_type, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则明细表';

-- 记账规则辅助核算项表
CREATE TABLE t_accounting_rule_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '记账规则ID（冗余字段）',
    rule_detail_id BIGINT NOT NULL COMMENT '记账规则明细ID',
    aux_type VARCHAR(32) NOT NULL COMMENT '辅助核算类型(字典CODE)',
    aux_code VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码(字典CODE)',
    allocation_method TINYINT NOT NULL COMMENT '分摊方式：1-不分摊,2-固定金额,3-按比例',
    allocation_value DECIMAL(18,6) NOT NULL COMMENT '分摊值',
    extend_script LONGTEXT NOT NULL COMMENT 'SpEL扩展脚本',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_accounting_rule_auxiliary (rule_detail_id,aux_code,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则辅助核算项表';

-- 缓冲入账规则表
CREATE TABLE t_buffer_posting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条,2-日间批量,3-日终批量汇总',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码，与账户编号必须有一个不为空',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号，与会计科目必须有一个不为空',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    effective_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '生效时间',
    expiration_time DATETIME NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '失效时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    KEY idx_business_code (business_code,trading_code,pay_channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓冲入账规则表';

-- 缓冲记账明细表
CREATE TABLE t_buffer_posting_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '缓冲入账规则ID',
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条,2-日间批量,3-日终批量汇总',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，结合trace_no实现幂等',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    amount DECIMAL(18,6) NOT NULL COMMENT '金额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '缓冲入账状态：1-待入账,2-处理中,3-成功,4-失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '执行次数',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    start_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '执行开始时间',
    complete_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间',
    sharding BIGINT NOT NULL DEFAULT 0 COMMENT '分片值（同一账户必须在同一分片）',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no,entry_id),
    KEY idx_trace_no (trace_no),
    KEY idx_accounting_date_status (accounting_date, status, account_no),
    KEY idx_create_time (create_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓冲记账明细表';

-- ==================== Migration: V5__subject.sql ====================

-- accounting-core/src/main/resources/db/migration/V5__subject.sql
-- Flyway V5：科目域（3 张表）
-- 对应 docs/sql/4-subject.sql

USE `accounting`;

-- 会计科目表
CREATE TABLE IF NOT EXISTS t_account_subject (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(32) NOT NULL COMMENT '科目编码（遵循通用科目编码规则，前缀为父科目编码，如101001，101为父科目）',
    subject_name VARCHAR(64) NOT NULL COMMENT '科目名称',
    subject_level TINYINT NOT NULL COMMENT '科目级别',
    parent_subject_id BIGINT NOT NULL DEFAULT 0 COMMENT '父科目ID',
    subject_category TINYINT NOT NULL COMMENT '账类：1-资产类,2-负债类,3-权益类,4-共同类,5-成本类,6-损益类,0-表外科目',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    is_leaf TINYINT NOT NULL DEFAULT '0' COMMENT '是否末级科目',
    allow_post TINYINT NOT NULL DEFAULT '0' COMMENT '是否允许记账',
    allow_open_account TINYINT NOT NULL DEFAULT '0' COMMENT '是否允许建明细账户',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用,2-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_subject_code (subject_code, is_delete),
    KEY idx_parent_subject_id (parent_subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目表';

-- 会计科目辅助核算项
CREATE TABLE t_account_subject_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    auxiliary_type VARCHAR(32) NOT NULL COMMENT '辅助核算项类别(字典CODE)',
    required TINYINT NOT NULL DEFAULT 0 COMMENT '0-可选,1-必填',
    default_aux_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '默认辅助核算项目(字典CODE)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_subject_auxiliary (subject_code, auxiliary_type, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目辅助核算项';

-- 外部客户账户开户模板表
CREATE TABLE t_account_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_name VARCHAR(32) NOT NULL COMMENT '模板名称',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    customer_type TINYINT NOT NULL COMMENT '客户类型：1-个人,2-企业,99-其他',
    auto_open TINYINT NOT NULL DEFAULT '0' COMMENT '是否支持自动开户：0-否,1-是',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-待启用；2-启用，3-停用',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型(字典CODE)',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    acct_no_rule VARCHAR(32) NOT NULL COMMENT '账户编号生成规则',
    acct_name_rule VARCHAR(32) NOT NULL COMMENT '账户名称生成规则',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_business_code (business_code,customer_type,subject_code,is_delete),
    KEY idx_subject_code (subject_code,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部客户账户开户模板表';

-- ==================== Migration: V6__journal.sql ====================

-- accounting-core/src/main/resources/db/migration/V6__journal.sql
-- Flyway V6：流水域（3 张表）
-- 对应 docs/sql/5-journal.sql

USE `accounting`;

-- 业务记账流水表
CREATE TABLE t_business_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL DEFAULT 0 COMMENT '预留字段，结合trace_no实现幂等',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    accounting_date DATE NOT NULL COMMENT '会计日期（在此确定，全链路不可变更）',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-处理中,2-成功,3-失败',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_trace_no (trace_no,trace_seq),
    KEY idx_trade_time (trade_time, status),
    KEY idx_accounting_date (accounting_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记账流水表';

-- 业务记账流水明细表
CREATE TABLE t_business_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT NOT NULL DEFAULT 0 COMMENT '预留字段，结合trace_no实现幂等',
    customer_type TINYINT NOT NULL COMMENT '客户类型：1-个人,2-企业,99-其他',
    customer_id VARCHAR(64) NOT NULL COMMENT '客户ID',
    funds_type VARCHAR(32) NOT NULL COMMENT '交易款项类型(字典CODE)',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记账流水明细表';

-- 事务表
CREATE TABLE t_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    relate_account_count INT NOT NULL DEFAULT 0 COMMENT '本次事务涉及的账户总数',
    amount DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '事务总金额',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '交易币种(字典CODE)',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-处理中,2-成功,3-失败',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    finish_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '事务最终完成时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_trace_no (trace_no),
    KEY idx_accounting_date_status (accounting_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务表';

-- ==================== Migration: V7__infra.sql ====================

-- accounting-core/src/main/resources/db/migration/V7__infra.sql
-- Flyway V7：支撑域（5 张表）
-- 对应 docs/sql/6-infra.sql

USE `accounting`;

-- 字典表
CREATE TABLE t_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dict_type VARCHAR(32) NOT NULL COMMENT '字典类型编码',
    dict_code VARCHAR(32) NOT NULL COMMENT '字典项编码',
    dict_name VARCHAR(64) NOT NULL COMMENT '字典项名称（中文默认）',
    dict_name_en VARCHAR(128) NOT NULL DEFAULT '' COMMENT '英文名称（用于国际化）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号，越小越靠前',
    group_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分组键',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用,2-停用',
    is_system TINYINT NOT NULL DEFAULT 1 COMMENT '是否系统内置：1-是（禁止删除），0-否（可维护）',
    ext_json TEXT NULL COMMENT '扩展属性 JSON',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_dict_type_code (dict_type,dict_code,is_delete),
    KEY idx_dict_type (dict_type, status),
    KEY idx_group_key (group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典表';

-- 本地消息表（事务性发件箱 Outbox Pattern）
CREATE TABLE t_local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    topic VARCHAR(32) NOT NULL COMMENT '消息主题/队列名',
    tag VARCHAR(32) NOT NULL DEFAULT '' COMMENT '消息标签（可选）',
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键，用于幂等与对账',
    payload TEXT NULL COMMENT '消息体（JSON格式）',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '消息状态：1-待发送,2-已发送,3-发送失败,4-已确认',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    send_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '实际发送时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_message_id (message_id),
    UNIQUE KEY uk_business_key (business_key),
    KEY idx_create_time (create_time, topic, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（事务性发件箱）';

-- 消息回执表
CREATE TABLE t_message_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '关联 t_local_message.message_id',
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键',
    consumer_group VARCHAR(32) NOT NULL COMMENT '消费者组',
    topic VARCHAR(32) NOT NULL COMMENT '消息主题/队列名',
    status TINYINT NOT NULL COMMENT '消费结果：1-成功,2-失败',
    error_code VARCHAR(16) NOT NULL DEFAULT '' COMMENT '错误码',
    error_message VARCHAR(255) NOT NULL DEFAULT '' COMMENT '错误详情',
    payload TEXT NULL COMMENT '消息快照（JSON格式，用于排查）',
    received_time DATETIME NOT NULL COMMENT '消费者接收时间',
    processed_time DATETIME NOT NULL COMMENT '消费者处理完成时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本记录创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_message_id_consumer (message_id, consumer_group),
    KEY idx_business_key (business_key),
    KEY idx_received_time (received_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息回执表';

-- 期末结转规则表
CREATE TABLE t_period_end_transfer_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名称',
    transfer_type TINYINT NOT NULL COMMENT '结转类型：1-损益结转,2-成本结转,3-自定义结转',
    source_subject_code VARCHAR(32) NOT NULL COMMENT '源科目编码（支持通配符，如：6* 表示所有6开头的科目）',
    target_subject_code VARCHAR(32) NOT NULL COMMENT '目标科目编码',
    transfer_direction TINYINT NOT NULL COMMENT '结转方向：1-借方余额结转到贷方,2-贷方余额结转到借方',
    summary_template VARCHAR(128) NOT NULL DEFAULT '' COMMENT '摘要模板（支持变量，如：{year}年{month}月损益结转）',
    execute_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序（数字越小越先执行）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用,2-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_rule_code (rule_code, is_delete),
    KEY idx_transfer_type (transfer_type, status),
    KEY idx_execute_order (execute_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转规则表';

-- 期末结转记录表
CREATE TABLE t_period_end_transfer_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    transfer_no VARCHAR(32) NOT NULL COMMENT '结转流水号',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    transfer_type TINYINT NOT NULL COMMENT '结转类型：1-损益结转,2-成本结转,3-自定义结转',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
    voucher_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '生成的凭证号',
    total_amount DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '结转总金额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-处理中,2-成功,3-失败',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    execute_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    finish_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_accounting_date (accounting_date, status),
    KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转记录表';

-- ==================== Migration: V8__posting_engine_fields.sql ====================

-- accounting-core/src/main/resources/db/migration/V8__posting_engine_fields.sql
-- Flyway V8：过账引擎新增字段（Step 9/10/12 补充）
-- 修复 PO 与 DDL 不一致导致的运行时 SQLSyntaxErrorException

USE `accounting`;

-- t_accounting_voucher：过账治理字段（Step 12 P0-8）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'fail_reason';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN fail_reason VARCHAR(255) NOT NULL DEFAULT '''' COMMENT ''过账失败原因'' AFTER reviewer_name',
    'SELECT ''Column fail_reason already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'retry_count';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT ''手动重试次数'' AFTER fail_reason',
    'SELECT ''Column retry_count already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher' AND column_name = 'skip_flag';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher ADD COLUMN skip_flag TINYINT NOT NULL DEFAULT 0 COMMENT ''跳过标记：0-未跳过,1-人工跳过'' AFTER retry_count',
    'SELECT ''Column skip_flag already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_accounting_voucher_entry：单边/缓冲/方向字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'is_unilateral';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN is_unilateral TINYINT NOT NULL DEFAULT 0 COMMENT ''是否单边记账：0-否,1-是'' AFTER status',
    'SELECT ''Column is_unilateral already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'is_buffered';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN is_buffered TINYINT NOT NULL DEFAULT 0 COMMENT ''是否缓冲入账：0-否,1-是'' AFTER is_unilateral',
    'SELECT ''Column is_buffered already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_accounting_voucher_entry' AND column_name = 'change_direction';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_accounting_voucher_entry ADD COLUMN change_direction TINYINT NOT NULL COMMENT ''增减方向：1-增,2-减'' AFTER is_buffered',
    'SELECT ''Column change_direction already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- t_business_detail：款项明细编码字段（Step 9）
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_business_detail' AND column_name = 'item_code';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_business_detail ADD COLUMN item_code VARCHAR(32) NOT NULL COMMENT ''款项明细编码（字典CODE）'' AFTER funds_type',
    'SELECT ''Column item_code already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== Migration: V9__template_dicts.sql ====================

-- accounting-core/src/main/resources/db/migration/V9__template_dicts.sql
-- Flyway V9：开户模板与账务核心字典初始化（业务线编码、账户类型、币种）

USE `accounting`;

-- 1. 业务线字典（business_code）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('business_code', 'jiedianqian_cl', '芒好贷', 'MangHaoDai', 1, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_ant', '芒好贷-蚂蚁', 'MangHaoDai Ant', 2, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_360zx', '芒好贷-360智信', 'MangHaoDai 360', 4, 'LOAN', 1, 1, -1),
('business_code', 'MG_GCB', '购车宝', 'Car Loan', 5, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_yb', '延保分期', 'YanBaoFenQi', 6, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_quant', '量化派', 'Quant', 7, 'LOAN', 1, 1, -1),
('business_code', 'jiedianqian_ddb', '订单宝', 'DingDanBao', 7, 'LOAN', 1, 1, -1);

-- 2. 账户类型字典（account_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('account_type', '贷款本金', '贷款本金', 'Loan Principal', 1, 'LOAN', 1, 1, -1),
('account_type', '贷款逾期本金', '贷款逾期本金', 'Overdue Principal', 2, 'LOAN', 1, 1, -1),
('account_type', '利息账户', '利息账户', 'Interest Account', 3, 'LOAN', 1, 1, -1),
('account_type', '罚息账户', '罚息账户', 'Penalty Interest', 4, 'LOAN', 1, 1, -1),
('account_type', '担保费账户', '担保费账户', 'Guarantee Fee', 5, 'LOAN', 1, 1, -1),
('account_type', '基本户', '基本户', 'Basic Account', 6, 'GENERAL', 1, 1, -1),
('account_type', '专用账户', '专用账户', 'Special Account', 7, 'GENERAL', 1, 1, -1),
('account_type', '结算账户', '结算账户', 'Settlement Account', 8, 'GENERAL', 1, 1, -1),
('account_type', 'CASH', '现金账户', 'Cash Account', 9, 'GENERAL', 1, 1, -1),
('account_type', 'DEPOSIT', '存款账户', 'Deposit Account', 10, 'GENERAL', 1, 1, -1);

-- 3. 币种字典（currency）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('currency', 'CNY', '人民币', 'RMB', 1, 'CURRENCY', 1, 1, -1),
('currency', 'USD', '美元', 'US Dollar', 2, 'CURRENCY', 1, 1, -1),
('currency', 'EUR', '欧元', 'Euro', 3, 'CURRENCY', 1, 1, -1),
('currency', 'HKD', '港币', 'HK Dollar', 4, 'CURRENCY', 1, 1, -1);

-- ==================== Migration: V10__add_account_no_to_freeze_detail.sql ====================

-- accounting-core/src/main/resources/db/migration/V10__add_account_no_to_freeze_detail.sql
-- Flyway V10：账户资金冻结明细表补充 account_no 字段

USE `accounting`;

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'account_no';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN account_no VARCHAR(32) NOT NULL DEFAULT '''' COMMENT ''账户编号'' AFTER id',
    'SELECT ''Column account_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND index_name = 'idx_account_no';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD INDEX idx_account_no (account_no)',
    'SELECT ''Index idx_account_no already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== Migration: V11__rule_dicts.sql ====================

-- accounting-core/src/main/resources/db/migration/V11__rule_dicts.sql
-- Flyway V11：记账规则与分录核心字典初始化

USE `accounting`;

-- 1. 凭证类型字典（voucher_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('voucher_type', 'PAYMENT', '付款凭证', 'Payment Voucher', 1, 'VOUCHER', 1, 1, -1),
('voucher_type', 'RECEIPT', '收款凭证', 'Receipt Voucher', 2, 'VOUCHER', 1, 1, -1),
('voucher_type', 'TRANSFER', '转账凭证', 'Transfer Voucher', 3, 'VOUCHER', 1, 1, -1),
('voucher_type', 'REVERSAL', '冲账凭证', 'Reversal Voucher', 4, 'VOUCHER', 1, 1, -1),
('voucher_type', 'PERIOD_END', '期末结转凭证', 'Period-End Transfer', 5, 'VOUCHER', 1, 1, -1),
('voucher_type', 'GENERAL', '记账凭证', 'General Voucher', 6, 'VOUCHER', 1, 1, -1);

-- 2. 支付渠道字典（pay_channel）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('pay_channel', 'CASH', '现金', 'Cash', 1, 'CHANNEL', 1, 1, -1),
('pay_channel', 'ALIPAY', '支付宝', 'Alipay', 2, 'CHANNEL', 1, 1, -1),
('pay_channel', 'WECHAT', '微信支付', 'WeChat Pay', 3, 'CHANNEL', 1, 1, -1),
('pay_channel', 'BANK', '银行卡/网银', 'Bank Transfer', 4, 'CHANNEL', 1, 1, -1),
('pay_channel', 'UNIONPAY', '银联在线', 'UnionPay', 5, 'CHANNEL', 1, 1, -1),
('pay_channel', 'INTERNAL', '内部清算', 'Internal Clearing', 6, 'CHANNEL', 1, 1, -1);

-- 3. 常用交易编码字典（trading_code）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('trading_code', 'CASH_PAY', '现金付款', 'Cash Payment', 1, 'TRADE', 1, 1, -1),
('trading_code', 'LOAN_DISBURSE', '贷款放款', 'Loan Disbursement', 2, 'TRADE', 1, 1, -1),
('trading_code', 'LOAN_REPAY', '贷款还款', 'Loan Repayment', 3, 'TRADE', 1, 1, -1),
('trading_code', 'TRANSFER', '资金转账', 'Fund Transfer', 4, 'TRADE', 1, 1, -1),
('trading_code', 'FEE_DEDUCT', '手续费扣收', 'Fee Deduction', 5, 'TRADE', 1, 1, -1),
('trading_code', 'WITHDRAW', '商户/个人提现', 'Withdrawal', 6, 'TRADE', 1, 1, -1);

-- 4. 交易款项类型字典（funds_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('funds_type', 'PRINCIPAL', '本金款项', 'Principal', 1, 'FUNDS', 1, 1, -1),
('funds_type', 'INTEREST', '利息款项', 'Interest', 2, 'FUNDS', 1, 1, -1),
('funds_type', 'PENALTY', '罚息/违约金', 'Penalty', 3, 'FUNDS', 1, 1, -1),
('funds_type', 'FEE', '服务费/手续费', 'Fee', 4, 'FUNDS', 1, 1, -1),
('funds_type', 'TAX', '税费支出', 'Tax', 5, 'FUNDS', 1, 1, -1),
('funds_type', 'DEPOSIT', '风险保证金', 'Deposit', 6, 'FUNDS', 1, 1, -1),
('funds_type', 'DISCOUNT', '营销立减/贴息', 'Discount', 7, 'FUNDS', 1, 1, -1);

-- 5. 辅助核算类型字典（auxiliary_type）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, tenant_id) VALUES
('auxiliary_type', 'CUSTOMER', '客户', 'Customer', 1, 'AUX', 1, 1, -1),
('auxiliary_type', 'SUPPLIER', '供应商辅助', 'Supplier', 2, 'AUX', 1, 1, -1),
('auxiliary_type', 'DEPARTMENT', '部门辅助', 'Department', 3, 'AUX', 1, 1, -1),
('auxiliary_type', 'PROJECT', '项目辅助', 'Project', 4, 'AUX', 1, 1, -1),
('auxiliary_type', 'EMPLOYEE', '员工/经办人辅助', 'Employee', 5, 'AUX', 1, 1, -1);

-- ==================== Migration: V12__add_status_to_buffer_posting_rule.sql ====================

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

-- ==================== Migration: V13__manual_voucher_apply.sql ====================

-- Flyway migration V13__manual_voucher_apply.sql
-- 1. 手工凭证申请流转表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '申请单号 (MVA+日期+序号)',
    `voucher_type` VARCHAR(32) NOT NULL DEFAULT '记账凭证' COMMENT '凭证类型(字典CODE/名称)',
    `trade_type` TINYINT NOT NULL DEFAULT 2 COMMENT '交易类别：1-正常, 2-调账',
    `accounting_date` DATE NOT NULL COMMENT '会计日期',
    `summary` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '申请摘要',
    `attachment_count` INT NOT NULL DEFAULT 0 COMMENT '附件张数',
    `total_debit_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '借方合计金额',
    `total_credit_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '贷方合计金额',
    `apply_status` TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废',
    `maker_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '制单人姓名',
    `auditor_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '初审人姓名',
    `audit_time` DATETIME NULL DEFAULT NULL COMMENT '初审时间',
    `audit_opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '初审意见/驳回原因',
    `reviewer_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '复核人姓名(会计主管)',
    `review_time` DATETIME NULL DEFAULT NULL COMMENT '复核时间',
    `review_opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '复核意见/驳回原因',
    `bookkeeper_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
    `bookkeeping_time` DATETIME NULL DEFAULT NULL COMMENT '记账时间',
    `voucher_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '正式入账凭证号(终审复核通过并记账后回填)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY `uk_apply_no` (`apply_no`),
    KEY `idx_apply_status` (`apply_status`),
    KEY `idx_voucher_no` (`voucher_no`),
    KEY `idx_accounting_date` (`accounting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请流转表';

-- 2. 手工凭证申请分录明细表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_entry` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `row_num` INT NOT NULL COMMENT '分录行号',
    `subject_code` VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    `account_no` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号',
    `debit_credit` TINYINT NOT NULL COMMENT '借贷方向：1-借, 2-贷',
    `amount` DECIMAL(18,6) NOT NULL COMMENT '分录金额(绝对值法则，正数)',
    `currency` VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `summary` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '分录摘要',
    `is_unilateral` TINYINT NOT NULL DEFAULT 1 COMMENT '资金单边处理(1-实时过账, 0-普通)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请分录明细表';

-- 3. 手工凭证申请辅助核算分摊表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_auxiliary` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `entry_row_num` INT NOT NULL COMMENT '关联分录行号',
    `subject_code` VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    `aux_type` VARCHAR(32) NOT NULL COMMENT '辅助核算类型(如 DEPT/PROJECT/CUSTOMER/SUPPLIER)',
    `aux_type_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '辅助核算类型名称',
    `aux_code` VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码',
    `aux_name` VARCHAR(64) NOT NULL COMMENT '辅助核算项目名称',
    `change_direction` TINYINT NOT NULL DEFAULT 1 COMMENT '增减方向：1-增, 2-减',
    `amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '核算金额',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请辅助核算分摊表';

-- 4. 手工凭证申请附件表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_attachment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `file_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '附件文件名称',
    `file_path` VARCHAR(255) NOT NULL COMMENT '附件存储地址/URL',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '附件大小(字节)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请附件表';

-- 5. 手工凭证审批流转审计日志表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `action` VARCHAR(32) NOT NULL COMMENT '审批流动作',
    `action_desc` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '动作描述',
    `operator_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `operator_role` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作角色',
    `pre_status` TINYINT NULL DEFAULT NULL COMMENT '流转前状态',
    `post_status` TINYINT NULL DEFAULT NULL COMMENT '流转后状态',
    `opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '审批意见/驳回原因',
    `operate_time` DATETIME NOT NULL COMMENT '操作时间',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`),
    KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证审批流转审计日志表';

-- ==================== Migration: V14__voucher_type_ext_and_manual_dicts.sql ====================

-- accounting-core/src/main/resources/db/migration/V14__voucher_type_ext_and_manual_dicts.sql
-- Flyway V14：凭证类型扩展属性补充（字头/表头/默认交易编码）与手工记账合法字典项初始化

USE `accounting`;

-- 1. 凭证类型字典补充扩展属性 JSON（ext_json）
UPDATE t_dictionary 
SET ext_json = '{"prefix":"收","title":"收款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'RECEIPT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"付","title":"付款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PAYMENT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"转","title":"转账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'TRANSFER';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"冲","title":"冲账凭证","tradeType":3,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'REVERSAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"结","title":"期末结转凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PERIOD_END';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"记","title":"记账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'GENERAL';

-- 2. 新增调账凭证字典项（voucher_type = ADJUST）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, ext_json, tenant_id) VALUES
('voucher_type', 'ADJUST', '调账凭证', 'Adjustment Voucher', 7, 'VOUCHER', 1, 1, '{"prefix":"调","title":"调账凭证","tradeType":2,"tradingCode":"ADJUST","payChannel":"INTERNAL"}', -1);

-- 3. 补充业务线字典（business_code = MANUAL）与交易编码字典（trading_code = ADJUST）
INSERT IGNORE INTO t_dictionary (dict_type, dict_code, dict_name, dict_name_en, sort_order, group_key, status, is_system, ext_json, tenant_id) VALUES
('business_code', 'MANUAL', '手工记账', 'Manual Accounting', 99, 'MANUAL', 1, 1, '', -1),
('trading_code', 'ADJUST', '账务调整', 'Account Adjustment', 99, 'TRADE', 1, 1, '', -1);

-- ==================== Migration: V15__voucher_no_prefix.sql ====================

-- accounting-core/src/main/resources/db/migration/V15__voucher_no_prefix.sql
-- Flyway V15：凭证类型扩展属性补充流水号前缀（voucherNoPrefix: REC, PAY, TRF, ADJ, REV, PET, VOU）

USE `accounting`;

UPDATE t_dictionary 
SET ext_json = '{"prefix":"收","voucherNoPrefix":"REC","title":"收款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'RECEIPT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"付","voucherNoPrefix":"PAY","title":"付款凭证","tradeType":1,"tradingCode":"CASH_PAY","payChannel":"BANK"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PAYMENT';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"转","voucherNoPrefix":"TRF","title":"转账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'TRANSFER';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"冲","voucherNoPrefix":"REV","title":"冲账凭证","tradeType":3,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'REVERSAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"结","voucherNoPrefix":"PET","title":"期末结转凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'PERIOD_END';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"记","voucherNoPrefix":"VOU","title":"记账凭证","tradeType":1,"tradingCode":"TRANSFER","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'GENERAL';

UPDATE t_dictionary 
SET ext_json = '{"prefix":"调","voucherNoPrefix":"ADJ","title":"调账凭证","tradeType":2,"tradingCode":"ADJUST","payChannel":"INTERNAL"}'
WHERE dict_type = 'voucher_type' AND dict_code = 'ADJUST';

-- ==================== Migration: V16__add_freeze_detail_ext_amounts.sql ====================

-- accounting-core/src/main/resources/db/migration/V16__add_freeze_detail_ext_amounts.sql
-- Flyway V16：账户资金冻结明细表补充初始冻结金额、累计已解冻金额、累计已扣款金额扩展字段

USE `accounting`;

-- 1. 增加 orig_freeze_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'orig_freeze_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN orig_freeze_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''初始冻结金额'' AFTER freeze_amount',
    'SELECT ''Column orig_freeze_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 增加 unfrozen_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'unfrozen_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN unfrozen_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''累计已解冻金额'' AFTER orig_freeze_amount',
    'SELECT ''Column unfrozen_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 增加 deducted_amount 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_account_freeze_detail' AND column_name = 'deducted_amount';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_account_freeze_detail ADD COLUMN deducted_amount DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT ''累计已扣款金额'' AFTER unfrozen_amount',
    'SELECT ''Column deducted_amount already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 历史存量数据平滑订正：orig_freeze_amount 回填当前 freeze_amount
UPDATE t_account_freeze_detail
SET orig_freeze_amount = freeze_amount
WHERE orig_freeze_amount = 0;

-- ==================== Migration: V17__add_transfer_rule_cycle_fields.sql ====================

-- accounting-core/src/main/resources/db/migration/V17__add_transfer_rule_cycle_fields.sql
-- Flyway V17：期末结转规则表补充是否支持自动结转与结转周期字段

USE `accounting`;

-- 1. 增加 auto_transfer 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND column_name = 'auto_transfer';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD COLUMN auto_transfer TINYINT NOT NULL DEFAULT 1 COMMENT ''是否支持自动结转：0-仅限手动,1-支持自动'' AFTER status',
    'SELECT ''Column auto_transfer already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 增加 period_cycle 字段
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND column_name = 'period_cycle';
SET @sql = IF(@col_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD COLUMN period_cycle TINYINT NOT NULL DEFAULT 2 COMMENT ''结转周期：1-每日,2-月末,3-季末,4-年末,5-仅手动'' AFTER auto_transfer',
    'SELECT ''Column period_cycle already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 增加索引以加速定时任务与日切筛选可自动结转规则
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = 'accounting' AND table_name = 't_period_end_transfer_rule' AND index_name = 'idx_auto_cycle';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE t_period_end_transfer_rule ADD INDEX idx_auto_cycle (status, auto_transfer, period_cycle)',
    'SELECT ''Index idx_auto_cycle already exists'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== Migration: V18__add_orig_freeze_no.sql ====================

-- accounting-core/src/main/resources/db/migration/V18__add_orig_freeze_no.sql
-- Flyway V18：t_business_record 增加 orig_freeze_no 字段

ALTER TABLE t_business_record
    ADD COLUMN orig_freeze_no VARCHAR(64) DEFAULT NULL COMMENT '关联预冻结单号(freeze_id)' AFTER summary;

-- ==================== Migration: V19__add_require_pre_freeze_to_accounting_rule.sql ====================

-- accounting-core/src/main/resources/db/migration/V19__add_require_pre_freeze_to_accounting_rule.sql
-- Flyway V19：记账规则主表扩展“需先预冻结”控制字段

USE `accounting`;

ALTER TABLE t_accounting_rule
    ADD COLUMN require_pre_freeze TINYINT NOT NULL DEFAULT 0 COMMENT '是否需先预冻结：0-否；1-是' AFTER freeze_duration;

-- ==================== Migration: V20__add_auxiliary_query_indexes.sql ====================

-- accounting-core/src/main/resources/db/migration/V20__add_auxiliary_query_indexes.sql
-- Flyway V20：辅助核算项目记录表补充查询优化索引

USE `accounting`;

ALTER TABLE t_accounting_voucher_auxiliary
    ADD INDEX idx_aux_type_code (aux_type, aux_code),
    ADD INDEX idx_aux_subject_code (subject_code);

-- ==================== Migration: V21__add_extra_params_and_auxiliary_spel.sql ====================

-- V21: 流水表扩展业务参数与辅助核算动态SpEL支持
ALTER TABLE `t_business_record` ADD COLUMN `extra_attrs` TEXT NULL COMMENT '扩展业务参数JSON' AFTER `summary`;
ALTER TABLE `t_business_detail` ADD COLUMN `extra_attrs` TEXT NULL COMMENT '明细扩展业务参数JSON' AFTER `amount`;

-- ==================== Migration: V22__financial_report_snapshot.sql ====================

-- V22: 财务报表定时归档快照表与规则逻辑删除存量冲突治理
-- 解决 BUG261010-003: FinancialReportJobHandler 数据持久化归档
-- 解决 BUG261010-005: 存量 is_delete=1 历史数据平滑升级消除唯一索引冲突

-- 1. 创建财务报表归档快照表
CREATE TABLE IF NOT EXISTS `t_financial_report_snapshot` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `report_type` VARCHAR(32) NOT NULL COMMENT '报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表',
    `report_name` VARCHAR(64) NOT NULL COMMENT '报表名称',
    `accounting_date` DATE NOT NULL COMMENT '会计日期/报告日期',
    `period_type` VARCHAR(16) NOT NULL DEFAULT 'DAY' COMMENT '期间类型: DAY-日报, MONTH-月报, YEAR-年报',
    `report_content` MEDIUMTEXT NOT NULL COMMENT '报表完整JSON内容',
    `total_asset` DECIMAL(18,2) DEFAULT NULL COMMENT '资产总计/营业收入(核心财务指标)',
    `total_liability_equity` DECIMAL(18,2) DEFAULT NULL COMMENT '负债及所有者权益总计/净利润(核心财务指标)',
    `is_balanced` TINYINT NOT NULL DEFAULT 1 COMMENT '是否平衡: 1-平衡/正常, 0-不平衡',
    `tenant_id` BIGINT NOT NULL DEFAULT -1 COMMENT '租户ID',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识: 0-未删除, 非0为已删除(主键ID)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tenant_report_date` (`tenant_id`, `report_type`, `accounting_date`, `period_type`, `is_delete`),
    KEY `idx_accounting_date` (`accounting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务报表归档快照表';

-- 2. 治理 t_accounting_rule_detail 与 t_accounting_rule_auxiliary 存量 is_delete=1 的历史脏数据
-- 将其规范化为自身主键 id，彻底消除 uk_rule_id 与 uk_subject_code 唯一索引冲突隐患
UPDATE `t_accounting_rule_detail` SET `is_delete` = `id` WHERE `is_delete` = 1;
UPDATE `t_accounting_rule_auxiliary` SET `is_delete` = `id` WHERE `is_delete` = 1;

-- ==================== Migration: V23__remove_item_code_from_business_detail.sql ====================

USE `accounting`;

-- V23: 业务流水明细模型精简（移除冗余的 item_code，唯一索引改为 funds_type 保证幂等）

-- 1. 安全处理唯一索引：若已存在旧 uk_trace_no 则先删除
SET @schema_name = COALESCE(DATABASE(), 'accounting');

SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND index_name = 'uk_trace_no';
SET @sql = IF(@idx_exists > 0,
    'ALTER TABLE `t_business_detail` DROP INDEX `uk_trace_no`',
    'SELECT ''Index uk_trace_no does not exist, skip drop'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 安全创建基于 funds_type 的唯一索引
SET @idx_exists = 0;
SELECT COUNT(*) INTO @idx_exists FROM information_schema.statistics
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND index_name = 'uk_trace_no';
SET @sql = IF(@idx_exists = 0,
    'ALTER TABLE `t_business_detail` ADD UNIQUE KEY `uk_trace_no` (`trace_no`, `trace_seq`, `customer_id`, `funds_type`)',
    'SELECT ''Index uk_trace_no already exists, skip add'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 安全删除冗余字段 item_code
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 't_business_detail' AND column_name = 'item_code';
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `t_business_detail` DROP COLUMN `item_code`',
    'SELECT ''Column item_code already removed, skip drop'' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== Migration: V24__alter_account_request_no_length.sql ====================

USE `accounting`;

-- V24: 扩充 t_account 表 request_no 字段长度至 64 位，保持与 trace_no 及开户契约一致
ALTER TABLE `t_account` MODIFY COLUMN `request_no` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '开户请求号';

-- ==================== Migration: V25__add_eod_status_table.sql ====================

USE `accounting`;

-- V25: 补充日切状态表 t_eod_status
CREATE TABLE IF NOT EXISTS `t_eod_status` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `accounting_date` DATE NOT NULL COMMENT '会计日期（T日）',
    `eod_status` TINYINT NOT NULL DEFAULT 1 COMMENT '日切状态：1-未开始,2-切日中,3-清理中,4-快照中,5-试算中,6-结转中,7-归档中,8-完成,9-失败',
    `switch_date_time` DATETIME COMMENT '切日完成时间',
    `archive_date_time` DATETIME COMMENT '归档完成时间',
    `failed_stage` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '失败阶段',
    `fail_reason` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    `total_duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY `uk_eod_date` (`accounting_date`, `is_delete`),
    KEY `idx_eod_status` (`eod_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日切状态表';

-- ==================== Migration: V26__remove_nature_from_subject.sql ====================

USE `accounting`;

-- V26: 移除会计科目表 t_account_subject 中冗余无业务语义的 nature 字段
SET @schema_name = COALESCE(DATABASE(), 'accounting');

SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists FROM information_schema.columns
    WHERE table_schema = @schema_name AND table_name = 't_account_subject' AND column_name = 'nature';
SET @sql = IF(@col_exists > 0,
    'ALTER TABLE `t_account_subject` DROP COLUMN `nature`',
    'SELECT \'Column nature already removed, skip drop\' AS msg');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ==================== Migration: V27__alter_subject_balance_direction.sql ====================

USE `accounting`;

-- 1. 更新会计科目表 t_account_subject 余额方向字段注释并更新历史表外数据
ALTER TABLE `t_account_subject`
    MODIFY COLUMN `debit_credit` TINYINT NOT NULL COMMENT '余额方向：0-无(表外), 1-借, 2-贷, 3-双向';

UPDATE `t_account_subject` SET `debit_credit` = 0 WHERE `subject_category` = 0;

-- 2. 更新账户表与子账户表余额方向字段注释
ALTER TABLE `t_account`
    MODIFY COLUMN `balance_direction` TINYINT NOT NULL COMMENT '余额方向：0-平/无, 1-借, 2-贷';

ALTER TABLE `t_sub_account`
    MODIFY COLUMN `balance_direction` TINYINT NOT NULL COMMENT '余额方向：0-平/无, 1-借, 2-贷';

