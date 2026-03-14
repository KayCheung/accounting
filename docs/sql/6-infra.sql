USE `accounting`;

-- ========================================
-- 支撑域：t_dictionary / t_local_message / t_message_receipt
--         t_period_end_transfer_rule / t_period_end_transfer_record
-- ========================================

-- 字典表
CREATE TABLE t_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dict_type VARCHAR(32) NOT NULL COMMENT '字典类型编码，如：auxiliary_type, trading_code, pay_channel, funds_type 等',
    dict_code VARCHAR(32) NOT NULL COMMENT '字典项编码，如：ASSET, LIABILITY, WECHAT, LOAN_DISBURSE 等',
    dict_name VARCHAR(64) NOT NULL COMMENT '字典项名称（中文默认）',
    dict_name_en VARCHAR(128) NOT NULL DEFAULT '' COMMENT '英文名称（用于国际化）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号，越小越靠前',
    group_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分组键',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用,2-停用',
    is_system TINYINT NOT NULL DEFAULT 1 COMMENT '是否系统内置：1-是（禁止删除），0-否（可维护）',
    ext_json TEXT NOT NULL DEFAULT '' COMMENT '扩展属性 JSON',
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
    payload TEXT NOT NULL DEFAULT '' COMMENT '消息体（JSON格式）',
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
    payload TEXT NOT NULL DEFAULT '' COMMENT '消息快照（JSON格式，用于排查）',
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
    finish_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_accounting_date (accounting_date, status),
    KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转记录表';
