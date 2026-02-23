-- ========================================
-- 会计科目相关表，t_account_subject企业会计科目表，树形结构。t_account_subject_auxiliary为科目默认的辅助核算项，用于记账时自动生成 t_voucher_auxiliary_detail 辅助核算项明细记录。
-- ========================================

-- 会计科目表
CREATE TABLE t_account_subject (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(50) NOT NULL COMMENT '科目编码',
    subject_name VARCHAR(100) NOT NULL COMMENT '科目名称',
    subject_level INT NOT NULL COMMENT '科目级别',
    subject_path VARCHAR(300) NOT NULL COMMENT '科目路径，如1001.01.001 方便做 like 查询',
    parent_subject_id BIGINT COMMENT '父科目ID',
    account_class ENUM('表外科目', '资产类', '负债类', '共同类', '权益类', '成本类', '损益类') NOT NULL COMMENT '账类',
    nature VARCHAR(50) NOT NULL COMMENT '科目性质，如：非特殊性科目、销账类科目、贷款类科目、现金类科目',
    direction ENUM('借', '贷') NOT NULL COMMENT '借贷方向',
    is_leaf TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否末级科目',
    allow_post TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否允许记账',
    allow_open_account TINYINT(1) DEFAULT 0 COMMENT '是否允许建明细账户',
    status ENUM('启用', '停用') DEFAULT '启用' COMMENT '状态',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_subject_code (subject_code),
    KEY idx_parent_subject_id (parent_subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目表';

-- 会计科目辅助核算项（是t_account_subject的从表）
CREATE TABLE t_account_subject_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    auxiliary_type VARCHAR(32) NOT NULL COMMENT '辅助核算项类别',
    required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '必填/可选',
    default_aux_code VARCHAR(100) COMMENT '默认辅助核算项目',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_subject_auxiliary (subject_code, auxiliary_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目辅助核算项';

-- ========================================
-- 内部账户与外部客户账户，外部账户主要通过t_account_template来生成账户t_account和t_sub_account，一个外部账户必有冻结和可用两个子账户。内部账户通过t_account_subject科目表配置来自动生成企业内部账户。
-- ========================================

-- 外部客户账户开户模板表
CREATE TABLE t_account_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    template_name VARCHAR(50) NOT NULL COMMENT '模板名称',
    business_code VARCHAR(50) NOT NULL COMMENT '业务线编码',
    customer_type ENUM('个人', '企业', '其他') NOT NULL COMMENT '客户类型',
    auto_open TINYINT(1) NOT NULL DEFAULT '0' COMMENT '是否支持自动开户：0-否，1-是', 
    status ENUM('启用', '禁用') DEFAULT '启用' COMMENT '状态',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    account_type VARCHAR(50) NOT NULL COMMENT '账户类型，如：基本户、待结算户、贷款本金账户、贷款利息账户、贷款担保费账户',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    balance_direction ENUM('借', '贷') DEFAULT '借' COMMENT '余额方向',
    account_rule VARCHAR(50) NOT NULL COMMENT '账号生成规则',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_business_code (business_code,customer_type,status),
    KEY idx_subject_code (subject_code,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部客户账户开户模板表';


-- 账户表
CREATE TABLE t_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    owner_id VARCHAR(50) NOT NULL COMMENT '所有者ID，如果时内部账户，默认为 INNER',
    owner_type ENUM('内部账户', '个人', '企业', '其他') NOT NULL COMMENT '所有者类型',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    account_name VARCHAR(100) NOT NULL COMMENT '账户名称',
    account_type VARCHAR(50) NOT NULL COMMENT '账户类型，如：基本户、待结算户、贷款本金账户、贷款利息账户、贷款担保费账户',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    balance_direction ENUM('借', '贷') DEFAULT '借' COMMENT '当前余额方向',
    opening_balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '期初余额',
    balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '余额',
    status ENUM('正常', '冻结', '注销') DEFAULT '正常' COMMENT '状态',
    risk_status ENUM('正常', '止入', '止出', '止入止出') DEFAULT '正常' COMMENT '风控状态',
    request_no VARCHAR(100) NOT NULL DEFAULT '' COMMENT '开户请求号',
    open_date DATE NOT NULL COMMENT '开户日期',
    inactive_date DATE COMMENT '动止日期',    
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_account_no (account_no),
    KEY idx_owner_id (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户表';

-- 子账户表
CREATE TABLE t_sub_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号（等于t_account表中的account_no）',
    balance_type ENUM('可用余额', '冻结余额') NOT NULL COMMENT '余额类型',
    balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '余额',
    remark TEXT COMMENT '备注',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '版本号（乐观锁）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_account_no (account_no,balance_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子账户表';

-- ========================================
-- 业务流水与事务处理，业务方法驱动记账，通过调用记账接口发起记账，账务系统将接口请求参数结构化处理后存储到 t_business_record、t_business_detail表，然后执行记账逻辑。t_transaction主要记录记账的事务过程。
-- ========================================

-- 业务记账流水表
CREATE TABLE t_business_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    business_code VARCHAR(50) NOT NULL COMMENT '业务线编码',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    trace_seq TINYINT(4) NOT NULL COMMENT '预留字段，如一个 trace_no 里多次记账，结合trace_no实现幂等',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    trans_type ENUM('正常', '调账', '红', '蓝') NOT NULL COMMENT '交易类别',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    trans_time DATETIME NOT NULL COMMENT '交易时间',
    summary TEXT COMMENT '摘要',
    status ENUM('成功', '失败', '处理中') DEFAULT '处理中' COMMENT '状态',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_trace_no (trace_no,trace_seq),
    KEY idx_trans_time (trans_time, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记账流水表';

-- 业务记账流水明细表（t_business_record的从表）
CREATE TABLE t_business_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    customer_type ENUM('个人', '企业', '其他') NOT NULL COMMENT '客户类型',
    customer_id VARCHAR(50) NOT NULL COMMENT '客户ID',
    item_code VARCHAR(50) NOT NULL COMMENT '交易项编码',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_transaction_no (trace_no),
    KEY idx_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务记账流水明细表';

-- 事务表
CREATE TABLE t_transaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    txn_no VARCHAR(50) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    trans_status ENUM('未提交', '已提交', '已确认', '已回滚') DEFAULT '未提交' COMMENT '事务状态',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_txn_no (txn_no),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事务表';

-- ========================================
-- 凭证与分录
-- 通过“业务记账流水”信息匹配t_voucher_rule、t_voucher_rule_detail、t_voucher_rule_auxiliary，然后根据规则配置信息找到账户信息，然后自动生成t_accounting_voucher、t_voucher_entry_detail、t_voucher_auxiliary_detail、t_voucher_attachment
-- ========================================

-- 记账凭证规则表
CREATE TABLE t_voucher_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
    voucher_type VARCHAR(50) NOT NULL COMMENT '凭证类型，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证',
    accounting_mode ENUM('实时', '异步') NOT NULL DEFAULT '实时' COMMENT '记账模式',
    business_code VARCHAR(50) NOT NULL COMMENT '业务线编码',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    rule_script longtext NOT NULL COMMENT '规则脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    status ENUM('启用', '停用') DEFAULT '启用' COMMENT '状态',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_voucher_rule (business_code,trans_code,pay_channel,voucher_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证规则表';

-- 记账凭证规则明细表
CREATE TABLE t_voucher_rule_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id BIGINT NOT NULL COMMENT '记账凭证规则ID',
    line_no INT NOT NULL COMMENT '行号',
    funds_type VARCHAR(50) NOT NULL COMMENT '交易款项类型（如：支付金额、充值金额、本金、利息、罚息、担保费、提前结清费等）',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    is_unilateral tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否单边记账',
    rule_script longtext NOT NULL COMMENT '规则脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    summary TEXT COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_voucher_rule_detail (rule_id, line_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证规则明细表';


-- 记账凭证规则辅助核算项表
CREATE TABLE t_voucher_rule_auxiliary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_detail_id BIGINT NOT NULL COMMENT '记账凭证规则明细ID',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    aux_type VARCHAR(50) NOT NULL COMMENT '辅助核算类型，如 DEPT/CUSTOMER/PROJECT',
    aux_code VARCHAR(50) NOT NULL COMMENT '辅助核算项目编码',
    aux_rule ENUM('固定金额', '按比例') NOT NULL COMMENT '辅助核算规则',
    aux_value DECIMAL(18,6) NOT NULL COMMENT '辅助核算规则值（如固定金额、按比例等）',
    rule_script longtext NOT NULL COMMENT '规则脚本（可以通过类似SpEL表达式来精确匹配，启动时预加载规则变更时同步更新）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_voucher_rule_auxiliary (rule_detail_id,subject_code,aux_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证规则辅助核算项表';

-- 记账凭证表
CREATE TABLE t_accounting_voucher (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    txn_no VARCHAR(50) NULL COMMENT '事务编号',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    voucher_type VARCHAR(50) NOT NULL COMMENT '凭证类型，如：付款凭证、收款凭证、转帐凭证、汇总凭证、结账凭证、提现凭证',
    posting_type ENUM('手工凭证', '机制凭证') NOT NULL COMMENT '入账类型',
    business_code VARCHAR(50) NOT NULL COMMENT '业务线编码',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    trans_type ENUM('正常', '调账', '红', '蓝') NOT NULL COMMENT '交易类别',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    amount DECIMAL(18,6) NOT NULL COMMENT '金额',
    status ENUM('未过账','已过账') DEFAULT '未过账' COMMENT '凭证状态', 
    post_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '过账时间',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary TEXT COMMENT '摘要',
    attachment_count INT DEFAULT 0 COMMENT '附件数',
    orig_voucher_no VARCHAR(50) NOT NULL DEFAULT '' COMMENT '原凭证号(红冲/蓝补/调账时，记录被冲销的原凭证号)',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    UNIQUE KEY uk_voucher_no (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证表';

-- 记账凭证附件表（是t_accounting_voucher的从表）
CREATE TABLE t_voucher_attachment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    file_path VARCHAR(255) NOT NULL COMMENT '附件地址',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_voucher_no (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证附件表';

-- 分录流水表
CREATE TABLE t_voucher_entry_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    entry_id VARCHAR(50) NOT NULL COMMENT '分录流水号',
    line_no INT NOT NULL COMMENT '分录行号',
    txn_id VARCHAR(50) NOT NULL COMMENT '事务编号',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    account_type ENUM('INNER','OUTER') COMMENT '账户类型',
    account_no VARCHAR(50) COMMENT '账户编码',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    summary TEXT COMMENT '摘要',
    status ENUM('未过账','已过账') DEFAULT '未过账' COMMENT '状态',
    accounting_date DATE NOT NULL COMMENT '会计日',
    balance_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '余额更新时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_txn_id (txn_id),
    KEY idx_entry_id (entry_id),
    KEY idx_voucher_no (voucher_no),
    KEY idx_accounting_date (accounting_date,account_no,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分录流水表';

-- 记账凭证辅助核算项明细
CREATE TABLE t_voucher_auxiliary_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    entry_id VARCHAR(50) NOT NULL COMMENT '分录流水号',
    aux_type VARCHAR(50) NOT NULL COMMENT '辅助核算类型，如 DEPT/CUSTOMER/PROJECT',
    aux_code VARCHAR(50) NOT NULL COMMENT '辅助核算项目编码',
    aux_name VARCHAR(100) NOT NULL COMMENT '辅助核算项目名称',
    amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '金额',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_entry_id (entry_id),
    KEY idx_voucher_no (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记账凭证辅助核算项目';


-- ========================================
-- 明细账，基于记账凭证生成t_account_detail记账明细记录，并更新账户余额。t_sub_account_detail 为独立记账流程，主要是冻结账户余额和解冻账户余额时记录变更明细。
-- ========================================

-- 外部客户账户明细表
CREATE TABLE t_account_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    entry_id VARCHAR(50) NOT NULL COMMENT '分录编号',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    txn_id VARCHAR(50) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    trans_type ENUM('正常', '调账', '红', '蓝') NOT NULL COMMENT '交易类别',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    change_direction ENUM('增', '减') DEFAULT '增' COMMENT '增减方向',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    pre_balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '交易前余额',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    post_balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '交易后余额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary TEXT COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_account_no (account_no,accounting_date),
    KEY idx_entry_id (entry_id),
    KEY idx_voucher_no (voucher_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户明细表';

-- 子账户明细表
CREATE TABLE t_sub_account_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    balance_type ENUM('可用余额', '冻结余额') NOT NULL COMMENT '余额类型',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    trans_type ENUM('正常', '调账', '红', '蓝') NOT NULL COMMENT '交易类别',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    change_direction ENUM('增', '减') DEFAULT '增' COMMENT '增减方向',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    pre_balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '交易前余额',
    amount DECIMAL(18,6) NOT NULL COMMENT '交易金额',
    post_balance DECIMAL(18,6) DEFAULT 0.00 COMMENT '交易后余额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary TEXT COMMENT '摘要',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_account_no (account_no),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='子账户明细表';


-- ========================================
-- 缓冲处理，通过“记账凭证”信息匹配缓冲入账规则，生成缓冲入账明细 t_buffer_posting_detail，后台任务定期扫描t_buffer_posting_detail表中待处理、失败明细记录，执行缓冲入账记账程序代码，成功记为已处理、失败记为失败，更新执行次数和完成时间。
-- ========================================

-- 缓冲入账规则表
CREATE TABLE t_buffer_posting_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_name VARCHAR(100) NOT NULL COMMENT '规则名称',
   buffer_mode ENUM('异步逐条入账','日间批量入账','日终批量汇总入账') NOT NULL COMMENT '缓冲入账模式',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    account_type ENUM('INNER','OUTER') COMMENT '账户类型',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    effective_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '生效时间',
    expiration_time DATETIME NOT NULL DEFAULT '2099-12-31 23:59:59' COMMENT '失效时间',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_trans_code (trans_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓冲入账规则表';

-- 缓冲记账明细表（异步流程）
CREATE TABLE t_buffer_posting_detail (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    entry_id VARCHAR(50) NOT NULL COMMENT '分录编号',
    voucher_no VARCHAR(50) NOT NULL COMMENT '凭证号',
    txn_id VARCHAR(50) NOT NULL COMMENT '事务编号',
    trace_no VARCHAR(50) NOT NULL COMMENT '系统跟踪号',
    account_type ENUM('INNER','OUTER') COMMENT '账户类型',
    account_no VARCHAR(50) NOT NULL COMMENT '账户编号',
    rule_id BIGINT NOT NULL COMMENT '缓冲入账规则ID',
    trans_code VARCHAR(50) NOT NULL COMMENT '交易编码',
    pay_channel VARCHAR(50) NOT NULL COMMENT '支付渠道，如：支付宝, 微信, 银行快捷支付, 其他三方支付机构,内部记账',
    trans_type ENUM('正常', '调账', '红', '蓝') NOT NULL COMMENT '交易类别',
    dr_cr_flag ENUM('借', '贷') NOT NULL COMMENT '借贷标识',
    amount DECIMAL(18,6) NOT NULL COMMENT '金额',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    summary TEXT COMMENT '摘要',
    buffer_status ENUM('待处理', '已处理', '失败') DEFAULT '待处理' COMMENT '缓冲入账状态',
    retry_count INT DEFAULT 0 COMMENT '执行次数',
    fail_reason TEXT COMMENT '失败原因',
    complete_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间（成功/失败时填充）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    KEY idx_entry_id (entry_id),
    KEY idx_voucher_no (voucher_no),
    KEY idx_trace_no (trace_no),
    KEY idx_buffer_complete_time (complete_time, buffer_status, retry_count)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='缓冲记账明细表';


-- ========================================
-- 日终或日切（异步流程）
-- ========================================

-- 账户日余额表
CREATE TABLE t_account_balance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    accounting_date DATE NOT NULL COMMENT '会计日（按日汇总；如按月则用period_id）',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    account_type ENUM('INNER','OUTER') NOT NULL COMMENT '账户类型',
    account_no VARCHAR(50) NOT NULL COMMENT '账户号（内部/外部账户）',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    balance_direction ENUM('借','贷') NOT NULL COMMENT '余额方向（当日期末余额的方向）',
    begin_balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '期初余额（当日0点）',
    debit_amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '当日借方发生额',
    credit_amount DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '当日贷方发生额',
    end_balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '期末余额（当日24点）',
    business_code VARCHAR(50) COMMENT '业务线编码（可选）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_balance_dim (accounting_date, subject_code, account_type, account_no, currency),
    KEY idx_subject_date (subject_code, accounting_date),
    KEY idx_account_date (account_no, accounting_date)
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
    snapshot_type VARCHAR(20) NOT NULL COMMENT '快照类型：DAY/MONTH/YEAR/CUSTOM 等',
    snapshot_time DATETIME NOT NULL COMMENT '快照生成时间（系统实际生成时间）',
    subject_code VARCHAR(50) NOT NULL COMMENT '会计科目编码',
    account_type ENUM('INNER','OUTER') NOT NULL COMMENT '账户类型',
    account_no VARCHAR(50) NOT NULL COMMENT '账户号（内部/外部账户）',
    currency VARCHAR(10) DEFAULT 'CNY' COMMENT '币种',
    balance_direction ENUM('借','贷') NOT NULL COMMENT '快照时余额方向',
    balance DECIMAL(18,6) NOT NULL DEFAULT 0 COMMENT '快照时账户余额',
    ext_json JSON COMMENT '扩展统计信息，如按交易类型汇总的金额/笔数等',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_snapshot_dim (snapshot_date, snapshot_type, subject_code, account_type, account_no, currency),
    KEY idx_snapshot_account (snapshot_date, account_no),
    KEY idx_snapshot_subject (snapshot_date, subject_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='账户余额快照表（周期快照）';


-- ========================================
-- 字典表
-- ========================================

-- 字典表：统一管理系统中所有枚举值、分类码、配置项等静态数据
CREATE TABLE t_dictionary (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    dict_type VARCHAR(50) NOT NULL COMMENT '字典类型编码，如：auxiliary_type, trans_code, pay_channel, funds_type 等',
    dict_code VARCHAR(50) NOT NULL COMMENT '字典项编码，如：ASSET, LIABILITY, WECHAT, LOAN_DISBURSE 等',
    dict_name VARCHAR(100) NOT NULL COMMENT '字典项名称（中文默认）',
    dict_name_en VARCHAR(100) COMMENT '英文名称（用于国际化）',
    sort_order INT DEFAULT 0 COMMENT '排序序号，越小越靠前',
    group_key VARCHAR(50) COMMENT '分组键，用于前端分组展示（如“支付渠道”下分“线上/线下”）',
    status ENUM('启用', '停用') NOT NULL DEFAULT '启用' COMMENT '状态',
    is_system TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否系统内置：1-是（禁止删除），0-否（可维护）',
    ext_json JSON COMMENT '扩展属性，如：{"color":"#FF0000", "icon":"loan", "rule_script":"..."}',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除，1-已删除',
    UNIQUE KEY uk_dict_type_code (dict_type, dict_code),
    KEY idx_dict_type (dict_type, status),
    KEY idx_group_key (group_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典表';