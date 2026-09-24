USE `accounting`;

-- ========================================
-- 规则域：t_accounting_rule / t_accounting_rule_detail / t_accounting_rule_auxiliary
--         t_buffer_posting_rule / t_buffer_posting_detail
-- ========================================

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
