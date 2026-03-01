USE `accounting`;

-- ========================================
-- 会计科目相关表，t_account_subject企业会计科目表，树形结构。t_account_subject_auxiliary为科目默认的辅助核算项，用于记账时自动生成 t_accounting_voucher_auxiliary 辅助核算项明细记录。
-- ========================================

-- 会计科目表
CREATE TABLE IF NOT EXISTS t_account_subject (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(32) NOT NULL COMMENT '科目编码（遵循通用科目编码规则，前缀为父科目编码，如101001，101为父科目）',
    subject_name VARCHAR(64) NOT NULL COMMENT '科目名称',
    subject_level TINYINT NOT NULL COMMENT '科目级别',
    parent_subject_id BIGINT NOT NULL DEFAULT 0 COMMENT '父科目ID',
    subject_category TINYINT NOT NULL COMMENT '账类：1-资产类,2-负债类,3-权益类,4-共同类,5-成本类,6-损益类,0-表外科目',
    nature TINYINT NOT NULL COMMENT '科目性质：1-非特殊性科目,2-销账类科目,3-贷款类科目,4-现金类科目',
    debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    is_leaf TINYINT NOT NULL DEFAULT '0' COMMENT '是否末级科目',
    allow_post TINYINT NOT NULL DEFAULT '0' COMMENT '是否允许记账',
    allow_open_account TINYINT NOT NULL DEFAULT '0' COMMENT '是否允许建明细账户',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-启用,2-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除,不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_subject_code (subject_code, is_delete),
    KEY idx_parent_subject_id (parent_subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目表';

-- 会计科目辅助核算项（是t_account_subject的从表）
CREATE TABLE t_account_subject_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    auxiliary_type VARCHAR(32) NOT NULL COMMENT '辅助核算项类别(字典CODE)',
    required TINYINT NOT NULL DEFAULT 0 COMMENT '0-可选,1-必填',
    default_aux_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT '默认辅助核算项目(字典CODE)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_subject_auxiliary (subject_code, auxiliary_type, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目辅助核算项';

-- ========================================
-- 内部账户与外部客户账户，外部账户主要通过t_account_template来生成账户t_account和t_sub_account，一个外部账户必有冻结和可用两个子账户。内部账户通过t_account_subject科目表配置来自动生成企业内部账户。
-- ========================================

-- 外部客户账户开户模板表
CREATE TABLE t_account_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_name VARCHAR(32) NOT NULL COMMENT '模板名称',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    customer_type TINYINT NOT NULL COMMENT '客户类型：1-个人,2-企业,99-其他',
    auto_open TINYINT NOT NULL DEFAULT '0' COMMENT '是否支持自动开户：0-否,1-是',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-待启用；2-启用，3-停用',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_type VARCHAR(32) NOT NULL COMMENT '账户类型(字典CODE)，如：BASIC-基本户,PEND_SET-待结算户,LOAN_PRI-贷款本金账户,LOAN_INT-贷款利息账户,LOAN_GUA-贷款担保费账户',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    balance_direction TINYINT NOT NULL COMMENT '余额方向：1-借,2-贷',
    acct_no_rule VARCHAR(32) NOT NULL COMMENT '账户编号生成规则（如：机构+日期+序号）',
    acct_name_rule VARCHAR(32) NOT NULL COMMENT '账户名称生成规则（如：客户名-账户类型）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_business_code (business_code,customer_type,subject_code,is_delete),
    KEY idx_subject_code (subject_code,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部客户账户开户模板表';

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

-- ========================================
-- 业务流水与事务处理，业务方法驱动记账，通过调用记账接口发起记账，账务系统将接口请求参数结构化处理后存储到 t_business_record、t_business_detail表，然后执行记账逻辑。t_transaction主要记录记账的事务过程。
-- ========================================

-- 业务记账流水表
CREATE TABLE t_business_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL DEFAULT 0 COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
    trade_type TINYINT NOT NULL COMMENT '交易类别：1-正常,2-调账,3-红,4-蓝',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    trade_time DATETIME NOT NULL COMMENT '交易时间',
    accounting_date DATE NOT NULL COMMENT '会计日期',
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

-- 业务记账流水明细表（t_business_record的从表）
CREATE TABLE t_business_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL DEFAULT 0 COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    customer_type TINYINT NOT NULL COMMENT '客户类型：1-个人,2-企业,99-其他',
    customer_id VARCHAR(64) NOT NULL COMMENT '客户ID',
    funds_type VARCHAR(32) NOT NULL COMMENT '交易款项类型(字典CODE),如：支付金额、充值金额、本金、利息、罚息、担保费、提前结清费等',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_trace_no (trace_no,trace_seq,customer_id,item_code),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记账流水明细表';

-- 事务表（V2 修复：简化状态枚举，删除统计字段）
CREATE TABLE t_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    relate_account_count INT NOT NULL DEFAULT 0 COMMENT '本次事务涉及的账户总数',
    amount DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '事务总金额（元）',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '交易币种(字典CODE)，如：CNY-人民币,USD-美元等',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-处理中(PROCESSING),2-成功(SUCCESS),3-失败(FAILED)',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '事务/记账失败原因',
    finish_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '事务最终完成时间（全部提交/回滚/失败）',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_trace_no (trace_no),
    KEY idx_accounting_date_status (accounting_date, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务表';

-- ========================================
-- 凭证与分录
-- 通过"业务记账流水"信息匹配t_accounting_rule、t_accounting_rule_detail、t_accounting_rule_auxiliary，然后根据规则配置信息找到账户信息，然后自动生成t_accounting_voucher、t_accounting_voucher_entry、t_accounting_voucher_auxiliary、t_accounting_voucher_attachment
-- ========================================

-- 记账规则表（已删除 accounting_mode 字段）
CREATE TABLE t_accounting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    voucher_type VARCHAR(32) NOT NULL COMMENT '凭证类型(字典CODE)，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证、冻结解冻凭证',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
	is_open_account TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许自动开户：0-否；1-是',
    freeze_duration INT NOT NULL DEFAULT 0 COMMENT '冻结时长，单位：秒',
    pre_rule_id BIGINT NOT NULL DEFAULT 0 COMMENT '前置入账规则ID（如提现,前置入账规则必须有提现预冻结）',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-待启用；2-启用，3-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_accounting_rule (business_code,trading_code,pay_channel,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则表';

-- 记账规则明细表
CREATE TABLE t_accounting_rule_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '记账凭证规则ID(关联t_accounting_rule.id)',
    row_num INT NOT NULL COMMENT '凭证分录行号',
    funds_type VARCHAR(32) NOT NULL COMMENT '交易款项类型(字典CODE),如：支付金额、充值金额、本金、利息、罚息、担保费、提前结清费等',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
	account_scope TINYINT NOT NULL COMMENT '账户作用域：1-内部分户；2-外部分户',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
    is_unilateral TINYINT NOT NULL DEFAULT 0 COMMENT '资金单边处理(是否实时更新账户余额)：0-否；1-是',
    extend_script longtext NOT NULL COMMENT '扩展脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_rule_id (rule_id, row_num, is_delete),
	UNIQUE KEY uk_subject_code (rule_id, subject_code, funds_type, is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则明细表';

-- 记账规则辅助核算项表
CREATE TABLE t_accounting_rule_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '记账凭证规则ID(关联t_accounting_rule.id,冗余字段)',
    rule_detail_id BIGINT NOT NULL COMMENT '记账凭证规则明细ID(关联t_accounting_rule_detail.id)',
    aux_type VARCHAR(32) NOT NULL COMMENT '辅助核算类型(字典CODE)，如 DEPT/CUSTOMER/PROJECT',
    aux_code VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码(字典CODE)',
    allocation_method TINYINT NOT NULL COMMENT '分摊方式：1-不分摊,2-固定金额,3-按比例',
    allocation_value DECIMAL(18,6) NOT NULL COMMENT '分摊值（如固定金额、比例等）',
    extend_script longtext NOT NULL COMMENT '扩展脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_accounting_rule_auxiliary (rule_detail_id,aux_code,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则辅助核算项表';

-- 记账凭证表（status 枚举值已调整：4-已冲销）
CREATE TABLE t_accounting_voucher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    txn_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '事务编号(凭证先行，事务编号在凭证生成后补充)',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    trace_seq TINYINT NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    voucher_type VARCHAR(32) NOT NULL COMMENT '凭证类型(字典CODE)，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证',
    posting_type TINYINT NOT NULL COMMENT '入账类型：1-手工凭证,2-机制凭证',
	business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
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
	bookkeeper_name varchar(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
	reviewer_name varchar(32) NOT NULL DEFAULT '' COMMENT '复核人姓名',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no),
	UNIQUE KEY uk_trace_no (trace_no,trace_seq),
    KEY idx_orig_voucher_no (orig_voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证表';

-- 记账凭证附件表（是t_accounting_voucher的从表）
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

-- 分录流水表
CREATE TABLE t_accounting_voucher_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    row_num INT NOT NULL COMMENT '分录行号',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    account_no VARCHAR(32) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
	debit_credit TINYINT NOT NULL COMMENT '借贷方向：1-借,2-贷',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种(字典CODE)，如：CNY-人民币',
	exchange_rate DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '记账汇率',
	unit_price DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '单价',
    quantity INT NOT NULL DEFAULT 0 COMMENT '数量',
    pricing_unit VARCHAR(32) NOT NULL DEFAULT '' COMMENT '计价单位(例如：mg/g/kg/m/l/ml/个/条等)',
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

-- 记账凭证辅助核算项明细
CREATE TABLE t_accounting_voucher_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(32) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(32) NOT NULL COMMENT '分录流水号',
    subject_code VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    aux_type VARCHAR(32) NOT NULL COMMENT '辅助核算类型(字典CODE)，如 DEPT/CUSTOMER/PROJECT',
    aux_code VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码(字典CODE)',
    aux_name VARCHAR(64) NOT NULL COMMENT '辅助核算项目名称',
    change_direction TINYINT NOT NULL COMMENT '增减方向：1-增,2-减（后续做数据分析时能明确知道是支出还是收入）',
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
