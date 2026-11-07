-- V22: 财务报表定时归档快照表与规则逻辑删除存量冲突治理
-- 解决 BUG261010-003: FinancialReportJobHandler 数据持久化归档
-- 解决 BUG261010-005: 存量 is_delete=1 历史数据平滑升级消除唯一索引冲突

-- 1. 创建财务报表归档快照表
CREATE TABLE IF NOT EXISTS `t_financial_report_snapshot` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `report_type` VARCHAR(32) NOT NULL COMMENT '报表类型: BALANCE_SHEET-资产负债表, INCOME_STATEMENT-利润表',
    `report_name` VARCHAR(64) NOT NULL COMMENT '报表名称',
    `accounting_date` DATE NOT NULL COMMENT '会计日期/报告日期',
    `period_type` VARCHAR(16) NOT NULL DEFAULT 'DAY' COMMENT '期间类型: DAY-日报, MONTH-月报, YEAR-年报',
    `report_content` MEDIUMTEXT NOT NULL COMMENT '报表完整JSON内容',
    `total_asset` DECIMAL(18,2) DEFAULT NULL COMMENT '资产总计/营业收入(核心财务指标)',
    `total_liability_equity` DECIMAL(18,2) DEFAULT NULL COMMENT '负债及所有者权益总计/净利润(核心财务指标)',
    `is_balanced` TINYINT NOT NULL DEFAULT 1 COMMENT '是否平衡: 1-平衡/正常, 0-不平衡',
    `tenant_id` BIGINT NOT NULL DEFAULT -1 COMMENT '租户ID',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识: 0-未删除, 非0为已删除(主键ID)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_tenant_report_date` (`tenant_id`, `report_type`, `accounting_date`, `period_type`, `is_delete`),
    KEY `idx_accounting_date` (`accounting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='财务报表归档快照表';

-- 2. 治理 t_accounting_rule_detail 与 t_accounting_rule_auxiliary 存量 is_delete=1 的历史脏数据
-- 将其规范化为自身主键 id，彻底消除 uk_rule_id 与 uk_subject_code 唯一索引冲突隐患
UPDATE `t_accounting_rule_detail` SET `is_delete` = `id` WHERE `is_delete` = 1;
UPDATE `t_accounting_rule_auxiliary` SET `is_delete` = `id` WHERE `is_delete` = 1;
