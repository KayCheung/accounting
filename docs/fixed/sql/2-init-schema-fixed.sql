-- ========================================
-- 明细账，基于记账凭证生成t_account_detail记账明细记录，并更新账户余额。t_sub_account_detail 为独立记账流程，主要是冻结账户余额和解冻账户余额时记录变更明细。
-- ========================================

-- 外部客户账户明细表
CREATE TABLE t_account_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
	business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
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
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
    balance_type TINYINT NOT NULL COMMENT '余额类型：1-可用余额,2-冻结余额',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
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

-- 账户资金冻结明细表 (用途：记录冻结交易便于异常后解冻)
CREATE TABLE `t_account_freeze_detail` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
	business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金冻结明细表（以凭证为粒度，账户信息通过分录关联，目的在于处理异常冻结资金）';

-- ========================================
-- 缓冲处理，通过"记账凭证"信息匹配缓冲入账规则，生成缓冲入账明细 t_buffer_posting_detail，后台任务定期扫描t_buffer_posting_detail表中待处理、失败明细记录，执行缓冲入账记账程序代码，成功记为已处理、失败记为失败，更新执行次数和完成时间。
-- ========================================

-- 缓冲入账规则表
CREATE TABLE t_buffer_posting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条入账,2-日间批量入账,3-日终批量汇总入账',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码，与账户编号必须有一个不为空',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号，与会计科目必须有一个不为空（等于t_account表中的account_no）',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    effective_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '生效时间',
    expiration_time DATETIME NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '失效时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    KEY idx_business_code (business_code,trading_code,pay_channel)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓冲入账规则表';

-- 缓冲记账明细表（异步流程）
CREATE TABLE t_buffer_posting_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '缓冲入账规则ID(记录由哪个规则触发缓冲方便后期追溯)',
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条入账,2-日间批量入账,3-日终批量汇总入账',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
	business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    amount DECIMAL(18,6) NOT NULL COMMENT '金额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '缓冲入账状态：1-待入账,2-入账处理中,3-入账成功,4-入账失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '执行次数',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    start_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '执行开始时间',
    complete_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间（成功/失败时填充）',
	sharding BIGINT NOT NULL DEFAULT 0 COMMENT '分片值(用于定时任务分片执行，同一账户必须在同一分片)',
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

-- ========================================
-- 日终或日切（异步流程）
-- ========================================

-- 账户日余额表
CREATE TABLE t_account_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    accounting_date DATE NOT NULL COMMENT '会计日（按日汇总）',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷（当日期末余额的方向）',
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

-- 账户余额快照表（周期快照，适合报表/对账/导出）
CREATE TABLE t_account_balance_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    snapshot_date DATE NOT NULL COMMENT '快照日期（如：日快照用自然日，月快照用月末日）',
    snapshot_type TINYINT NOT NULL COMMENT '快照类型：1-DAY,2-MONTH,3-YEAR,4-CUSTOM 等',
    snapshot_time DATETIME NOT NULL COMMENT '快照生成时间（系统实际生成时间）',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷（当日期末余额的方向）',
    balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '快照时账户余额',
    ext_json VARCHAR(255) NOT NULL DEFAULT '' COMMENT '扩展统计信息，如按交易类型汇总的金额/笔数等',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_snapshot_dim (snapshot_date, snapshot_type, account_no,is_delete),
    KEY idx_snapshot_account (snapshot_date, account_no),
    KEY idx_snapshot_subject (snapshot_date, subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额快照表（周期快照）';

ALTER TABLE t_account_balance_snapshot
PARTITION BY RANGE COLUMNS(snapshot_date) (
    PARTITION p2025 VALUES LESS THAN ('2026-01-01'),
    PARTITION p2026 VALUES LESS THAN ('2027-01-01'),
    PARTITION pmax  VALUES LESS THAN (MAXVALUE)
);

-- ========================================
-- 字典表
-- ========================================

-- 字典表：统一管理系统中所有枚举值、分类码、配置项等静态数据
CREATE TABLE t_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dict_type VARCHAR(32) NOT NULL COMMENT '字典类型编码，如：auxiliary_type, trading_code, pay_channel, funds_type 等',
    dict_code VARCHAR(32) NOT NULL COMMENT '字典项编码，如：ASSET, LIABILITY, WECHAT, LOAN_DISBURSE 等',
    dict_name VARCHAR(64) NOT NULL COMMENT '字典项名称（中文默认）',
    dict_name_en VARCHAR(128) NOT NULL DEFAULT '' COMMENT '英文名称（用于国际化）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号，越小越靠前',
    group_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分组键，用于前端分组展示（如"支付渠道"下分"线上/线下"）',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用,2-停用',
    is_system TINYINT NOT NULL DEFAULT 1 COMMENT '是否系统内置：1-是（禁止删除），0-否（可维护）',
    ext_json TEXT NOT NULL DEFAULT '' COMMENT '扩展属性，如：{"color":"#FF0000", "icon":"loan", "rule_script":"..."}',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_dict_type_code (dict_type,dict_code,is_delete),
    KEY idx_dict_type (dict_type, status),
    KEY idx_group_key (group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典表';

-- ========================================
-- 本地消息和消息回执表
-- ========================================
-- 本地消息表（用于可靠事件发布 / 事务性发件箱模式）
CREATE TABLE t_local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    topic VARCHAR(32) NOT NULL COMMENT '消息主题/队列名',
    tag VARCHAR(32) NOT NULL DEFAULT '' COMMENT '消息标签（可选）',
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键（如订单号、流水号），用于幂等与对账',
    payload TEXT NOT NULL DEFAULT '' COMMENT '消息体（JSON格式，支持结构化数据）',
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

-- 消息回执表：记录下游消费者对本地消息的处理结果
CREATE TABLE t_message_receipt (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '关联 t_local_message.message_id',
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键（如订单号、流水号），用于幂等与对账',
    consumer_group VARCHAR(32) NOT NULL COMMENT '消费者组',
    topic VARCHAR(32) NOT NULL COMMENT '消息主题/队列名',
    status TINYINT NOT NULL COMMENT '消费者处理结果：1-成功,2-失败',
    error_code VARCHAR(16) NOT NULL DEFAULT '' COMMENT '错误码（如：INSUFFICIENT_BALANCE）',
    error_message VARCHAR(255) NOT NULL DEFAULT '' COMMENT '错误详情（可选）',
    payload TEXT NOT NULL DEFAULT '' COMMENT '消费者收到的消息快照（JSON格式，可选，用于排查）',
    received_time DATETIME NOT NULL COMMENT '消费者接收时间（由消费者上报）',
    processed_time DATETIME NOT NULL COMMENT '消费者处理完成时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本记录创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_message_id_consumer (message_id, consumer_group),
    KEY idx_business_key (business_key),
    KEY idx_received_time (received_time, receipt_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息回执表（记录消费者处理结果）';

-- ========================================
-- 期末结转（V2 新增）
-- ========================================

-- 期末结转规则表
CREATE TABLE t_period_end_transfer_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名称',
    transfer_type TINYINT NOT NULL COMMENT '结转类型：1-损益结转,2-成本结转,3-自定义结转',
    source_subject_code VARCHAR(32) NOT NULL COMMENT '源科目编码（支持通配符，如：6*表示所有6开头的科目）',
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
