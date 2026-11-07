-- Flyway migration V13__manual_voucher_apply.sql
-- 1. 手工凭证申请流转表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '申请单号 (MVA+日期+序号)',
    `voucher_type` VARCHAR(32) NOT NULL DEFAULT '记账凭证' COMMENT '凭证类型(字典CODE/名称)',
    `trade_type` TINYINT NOT NULL DEFAULT 2 COMMENT '交易类别：1-正常, 2-调账',
    `accounting_date` DATE NOT NULL COMMENT '会计日期',
    `summary` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '申请摘要',
    `attachment_count` INT NOT NULL DEFAULT 0 COMMENT '附件张数',
    `total_debit_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '借方合计金额',
    `total_credit_amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '贷方合计金额',
    `apply_status` TINYINT NOT NULL DEFAULT 1 COMMENT '审批状态：1-草稿, 2-待初审, 3-初审驳回, 4-待复核, 5-复核驳回, 6-待记账, 7-已记账, 8-已作废',
    `maker_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '制单人姓名',
    `auditor_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '初审人姓名',
    `audit_time` DATETIME NULL DEFAULT NULL COMMENT '初审时间',
    `audit_opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '初审意见/驳回原因',
    `reviewer_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '复核人姓名(会计主管)',
    `review_time` DATETIME NULL DEFAULT NULL COMMENT '复核时间',
    `review_opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '复核意见/驳回原因',
    `bookkeeper_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '记账人姓名',
    `bookkeeping_time` DATETIME NULL DEFAULT NULL COMMENT '记账时间',
    `voucher_no` VARCHAR(32) NULL DEFAULT NULL COMMENT '正式入账凭证号(终审复核通过并记账后回填)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY `uk_apply_no` (`apply_no`),
    KEY `idx_apply_status` (`apply_status`),
    KEY `idx_voucher_no` (`voucher_no`),
    KEY `idx_accounting_date` (`accounting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请流转表';

-- 2. 手工凭证申请分录明细表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_entry` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `row_num` INT NOT NULL COMMENT '分录行号',
    `subject_code` VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    `account_no` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '账户编号',
    `debit_credit` TINYINT NOT NULL COMMENT '借贷方向：1-借, 2-贷',
    `amount` DECIMAL(18,6) NOT NULL COMMENT '分录金额(绝对值法则，正数)',
    `currency` VARCHAR(32) NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `summary` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '分录摘要',
    `is_unilateral` TINYINT NOT NULL DEFAULT 1 COMMENT '资金单边处理(1-实时过账, 0-普通)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请分录明细表';

-- 3. 手工凭证申请辅助核算分摊表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_auxiliary` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `entry_row_num` INT NOT NULL COMMENT '关联分录行号',
    `subject_code` VARCHAR(32) NOT NULL COMMENT '会计科目编码',
    `aux_type` VARCHAR(32) NOT NULL COMMENT '辅助核算类型(如 DEPT/PROJECT/CUSTOMER/SUPPLIER)',
    `aux_type_name` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '辅助核算类型名称',
    `aux_code` VARCHAR(32) NOT NULL COMMENT '辅助核算项目编码',
    `aux_name` VARCHAR(64) NOT NULL COMMENT '辅助核算项目名称',
    `change_direction` TINYINT NOT NULL DEFAULT 1 COMMENT '增减方向：1-增, 2-减',
    `amount` DECIMAL(18,6) NOT NULL DEFAULT 0.000000 COMMENT '核算金额',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请辅助核算分摊表';

-- 4. 手工凭证申请附件表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_apply_attachment` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `file_name` VARCHAR(128) NOT NULL DEFAULT '' COMMENT '附件文件名称',
    `file_path` VARCHAR(255) NOT NULL COMMENT '附件存储地址/URL',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '附件大小(字节)',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证申请附件表';

-- 5. 手工凭证审批流转审计日志表
CREATE TABLE IF NOT EXISTS `t_manual_voucher_audit_log` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `apply_no` VARCHAR(32) NOT NULL COMMENT '关联申请单号',
    `action` VARCHAR(32) NOT NULL COMMENT '审批流动作',
    `action_desc` VARCHAR(64) NOT NULL DEFAULT '' COMMENT '动作描述',
    `operator_name` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作人姓名',
    `operator_role` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '操作角色',
    `pre_status` TINYINT NULL DEFAULT NULL COMMENT '流转前状态',
    `post_status` TINYINT NULL DEFAULT NULL COMMENT '流转后状态',
    `opinion` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '审批意见/驳回原因',
    `operate_time` DATETIME NOT NULL COMMENT '操作时间',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    KEY `idx_apply_no` (`apply_no`),
    KEY `idx_operate_time` (`operate_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='手工凭证审批流转审计日志表';
