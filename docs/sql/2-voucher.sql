USE `accounting`;

-- ========================================
-- 凭证域：t_accounting_voucher / t_accounting_voucher_entry
--         t_accounting_voucher_auxiliary / t_accounting_voucher_attachment
-- ========================================

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
