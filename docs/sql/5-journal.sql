USE `accounting`;

-- ========================================
-- 流水域：t_business_record / t_business_detail / t_transaction
-- ========================================

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

-- 业务记账流水明细表（t_business_record 的从表）
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
    UNIQUE KEY uk_trace_no (trace_no,trace_seq,customer_id,item_code),
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
    finish_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '事务最终完成时间',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_trace_no (trace_no),
    KEY idx_accounting_date_status (accounting_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务表';
