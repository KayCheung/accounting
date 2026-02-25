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
    account_class TINYINT NOT NULL COMMENT '账类：1-资产类,2-负债类,3-权益类,4-共同类,5-成本类,6-损益类,0-表外科目',
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
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-处理中,2-成功,3-失败',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_trace_no (trace_no,trace_seq),
    KEY idx_trade_time (trade_time, status)
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

-- 事务表
CREATE TABLE t_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    txn_no VARCHAR(32) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(64) NOT NULL COMMENT '系统跟踪号(支付系统或其他外部系统的请求流水号,机制凭证为系统生成)',
    -- business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    -- trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    total_entry_count INT NOT NULL DEFAULT 0 COMMENT '本次事务总记账明细条数',
    success_entry_count INT NOT NULL DEFAULT 0 COMMENT '已成功记账的明细条数',
    pending_entry_count INT NOT NULL DEFAULT 0 COMMENT '处理中/未提交的明细条数',
    fail_entry_count INT NOT NULL DEFAULT 0 COMMENT '记账失败的明细条数',
    relate_account_count INT NOT NULL DEFAULT 0 COMMENT '本次事务涉及的账户总数',
    amount DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '事务总金额（元）',
    currency VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '交易币种(字典CODE)，如：CNY-人民币,USD-美元等',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-未提交,2-部分提交,3-全部提交,4-部分回滚,5-全部回滚,6-失败',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '事务/记账失败原因',
    finish_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '事务最终完成时间（全部提交/回滚/失败）',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务表';

-- ========================================
-- 凭证与分录
-- 通过“业务记账流水”信息匹配t_accounting_rule、t_accounting_rule_detail、t_accounting_rule_auxiliary，然后根据规则配置信息找到账户信息，然后自动生成t_accounting_voucher、t_accounting_voucher_entry、t_accounting_voucher_auxiliary、t_accounting_voucher_attachment
-- ========================================

-- 记账规则表
CREATE TABLE t_accounting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    voucher_type VARCHAR(32) NOT NULL COMMENT '凭证类型(字典CODE)，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证',
    accounting_mode TINYINT NOT NULL DEFAULT '1' COMMENT '记账模式：1-实时,2-异步',
    business_code VARCHAR(32) NOT NULL COMMENT '业务线编码(字典CODE)',
    trading_code VARCHAR(32) NOT NULL COMMENT '交易编码(字典CODE)',
    pay_channel VARCHAR(32) NOT NULL COMMENT '支付渠道(字典CODE)，如：ALIPAY-支付宝,WECHAT-微信',
	is_open_account TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许自动开户：0-否；1-是',
    pre_rule_id BIGINT NOT NULL DEFAULT 0 COMMENT '前置入账规则ID（如提现,前置入账规则必须有提现预冻结）',
    -- rule_script longtext NOT NULL COMMENT '规则脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '状态：1-待启用；2-启用，3-停用',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
	create_id varchar(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
	create_name varchar(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
	update_id varchar(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
	update_name varchar(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_rule (business_code,trading_code,pay_channel,is_delete)
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
    UNIQUE KEY uk_voucher_rule_auxiliary (rule_detail_id,aux_code,is_delete)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账规则辅助核算项表';

-- 记账凭证表
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
    status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败', 
    post_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '过账时间',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary VARCHAR(64) NOT NULL DEFAULT '' COMMENT '摘要',
    attachment_count INT NOT NULL DEFAULT 0 COMMENT '附件数',
    orig_voucher_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '原凭证号(红冲/蓝补/调账时，记录被冲销的原凭证号或原纸质或电子凭证号)',
    version INT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
	bookkeeper_name varchar(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
	reviewer_name varchar(32) NOT NULL DEFAULT '' COMMENT '复核人姓名',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
	tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_voucher_no (voucher_no),
	UNIQUE KEY uk_trace_no (trace_no,trace_seq)
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
	-- 用于超时冻结记录扫描
	KEY idx_account_expire (expire_time, status),
	KEY idx_trade_time (`trade_time`,`status`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户资金冻结明细表（以凭证为粒度，账户信息通过分录关联，目的在于处理异常冻结资金）';

-- ========================================
-- 缓冲处理，通过“记账凭证”信息匹配缓冲入账规则，生成缓冲入账明细 t_buffer_posting_detail，后台任务定期扫描t_buffer_posting_detail表中待处理、失败明细记录，执行缓冲入账记账程序代码，成功记为已处理、失败记为失败，更新执行次数和完成时间。
-- ========================================

-- 缓冲入账规则表
CREATE TABLE t_buffer_posting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(32) NOT NULL COMMENT '规则名称',
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条入账,2-日间批量入账,3-日终批量汇总入账',
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
    buffer_mode TINYINT NOT NULL COMMENT '缓冲入账模式：1-异步逐条入账,2-日间批量入账,3-日终批量汇总入账',
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
    KEY idx_accounting_date (accounting_date,account_no),
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
    group_key VARCHAR(32) NOT NULL DEFAULT '' COMMENT '分组键，用于前端分组展示（如“支付渠道”下分“线上/线下”）',
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
    -- 唯一约束：确保同一业务事件只生成一条消息
    UNIQUE KEY uk_message_id (message_id),
    UNIQUE KEY uk_business_key (business_key),
    -- 查询索引
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