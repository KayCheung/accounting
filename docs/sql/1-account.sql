USE `accounting`;

-- ========================================
-- 账户域：t_account / t_sub_account / t_account_detail / t_sub_account_detail
--         t_account_freeze_detail / t_account_balance / t_account_balance_snapshot
-- ========================================

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
    KEY idx_account_expire (expire_time, status),
    KEY idx_trade_time (`trade_time`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金冻结明细表';

-- 账户日余额表（按年分区）
CREATE TABLE t_account_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
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
    UNIQUE KEY uk_balance_dim (accounting_date, account_no, is_delete),
    KEY idx_subject_date (accounting_date, subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户日余额表';

ALTER TABLE t_account_balance
PARTITION BY RANGE COLUMNS(accounting_date) (
    PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax  VALUES LESS THAN (MAXVALUE)
);

-- 账户余额快照表（按年分区）
CREATE TABLE t_account_balance_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
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
    UNIQUE KEY uk_snapshot_dim (snapshot_date, snapshot_type, account_no, is_delete),
    KEY idx_snapshot_account (snapshot_date, account_no),
    KEY idx_snapshot_subject (snapshot_date, subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额快照表（周期快照）';

ALTER TABLE t_account_balance_snapshot
PARTITION BY RANGE COLUMNS(snapshot_date) (
    PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax  VALUES LESS THAN (MAXVALUE)
);
