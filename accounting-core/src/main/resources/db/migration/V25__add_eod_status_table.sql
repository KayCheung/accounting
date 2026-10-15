-- accounting-core/src/main/resources/db/migration/V25__add_eod_status_table.sql
-- Flyway V25：补充日切状态表 t_eod_status
-- 对应 docs/sql/6-infra.sql

USE `accounting`;

CREATE TABLE IF NOT EXISTS `t_eod_status` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `accounting_date` DATE NOT NULL COMMENT '会计日期（T日）',
    `eod_status` TINYINT NOT NULL DEFAULT 1 COMMENT '日切状态：1-未开始,2-切日中,3-清理中,4-快照中,5-试算中,6-结转中,7-归档中,8-完成,9-失败',
    `switch_date_time` DATETIME COMMENT '切日完成时间',
    `archive_date_time` DATETIME COMMENT '归档完成时间',
    `failed_stage` VARCHAR(32) NOT NULL DEFAULT '' COMMENT '失败阶段',
    `fail_reason` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    `total_duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_delete` BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    `tenant_id` INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY `uk_eod_date` (`accounting_date`, `is_delete`),
    KEY `idx_eod_status` (`eod_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日切状态表';
