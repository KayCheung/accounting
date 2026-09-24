USE `accounting`;

-- ========================================
-- 科目域：t_account_subject / t_account_subject_auxiliary / t_account_template
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
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT '0' COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT '-1' COMMENT '租户ID',
    UNIQUE KEY uk_subject_code (subject_code, is_delete),
    KEY idx_parent_subject_id (parent_subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会计科目表';

-- 会计科目辅助核算项（t_account_subject 的从表）
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
