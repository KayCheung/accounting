-- 03_xxl_job.sql
-- XXL-JOB 调度中心数据库表结构与初始数据

USE `xxl_job`;

CREATE TABLE IF NOT EXISTS `xxl_job_info` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_desc` varchar(255) NOT NULL,
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  `author` varchar(64) DEFAULT NULL COMMENT '作者',
  `alarm_email` varchar(255) DEFAULT NULL COMMENT '报警邮件',
  `schedule_type` varchar(50) NOT NULL DEFAULT 'NONE' COMMENT '调度类型',
  `schedule_conf` varchar(128) DEFAULT NULL COMMENT '调度配置，值含义取决于调度类型',
  `misfire_strategy` varchar(50) NOT NULL DEFAULT 'DO_NOTHING' COMMENT '调度过期策略',
  `executor_route_strategy` varchar(50) DEFAULT NULL COMMENT '执行器路由策略',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '执行器任务handler',
  `executor_param` varchar(512) DEFAULT NULL COMMENT '执行器任务参数',
  `executor_block_strategy` varchar(50) DEFAULT NULL COMMENT '阻塞处理策略',
  `executor_timeout` int(11) NOT NULL DEFAULT '0' COMMENT '任务执行超时时间，单位秒',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `glue_type` varchar(50) NOT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) DEFAULT NULL COMMENT 'GLUE备注',
  `glue_updatetime` datetime DEFAULT NULL COMMENT 'GLUE更新时间',
  `child_jobid` varchar(255) DEFAULT NULL COMMENT '子任务ID，多个逗号分隔',
  `trigger_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '调度状态：0-停止，1-运行',
  `trigger_last_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '上次调度时间',
  `trigger_next_time` bigint(13) NOT NULL DEFAULT '0' COMMENT '下次调度时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `job_group` int(11) NOT NULL COMMENT '执行器主键ID',
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `executor_address` varchar(255) DEFAULT NULL COMMENT '执行器地址，本次执行的地址',
  `executor_handler` varchar(255) DEFAULT NULL COMMENT '执行器任务handler',
  `executor_param` varchar(512) DEFAULT NULL COMMENT '执行器任务参数',
  `executor_sharding_param` varchar(20) DEFAULT NULL COMMENT '执行器任务分片参数，格式如 1/2',
  `executor_fail_retry_count` int(11) NOT NULL DEFAULT '0' COMMENT '失败重试次数',
  `trigger_time` datetime DEFAULT NULL COMMENT '调度-时间',
  `trigger_code` int(11) NOT NULL COMMENT '调度-结果',
  `trigger_msg` text COMMENT '调度-日志',
  `handle_time` datetime DEFAULT NULL COMMENT '执行-时间',
  `handle_code` int(11) NOT NULL COMMENT '执行-状态',
  `handle_msg` text COMMENT '执行-日志',
  `alarm_status` tinyint(4) NOT NULL DEFAULT '0' COMMENT '告警状态：0-默认、1-无需告警、2-告警成功、3-告警失败',
  PRIMARY KEY (`id`),
  KEY `I_trigger_time` (`trigger_time`),
  KEY `I_handle_code` (`handle_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_log_report` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `trigger_day` datetime DEFAULT NULL COMMENT '调度-时间',
  `running_count` int(11) NOT NULL DEFAULT '0' COMMENT '运行中-计数',
  `suc_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行成功-计数',
  `fail_count` int(11) NOT NULL DEFAULT '0' COMMENT '执行失败-计数',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_trigger_day` (`trigger_day`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_logglue` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `job_id` int(11) NOT NULL COMMENT '任务，主键ID',
  `glue_type` varchar(50) DEFAULT NULL COMMENT 'GLUE类型',
  `glue_source` mediumtext COMMENT 'GLUE源代码',
  `glue_remark` varchar(128) NOT NULL COMMENT 'GLUE备注',
  `add_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_registry` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `registry_group` varchar(50) NOT NULL,
  `registry_key` varchar(255) NOT NULL,
  `registry_value` varchar(255) NOT NULL,
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `i_g_k_v` (`registry_group`,`registry_key`(191),`registry_value`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_group` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `app_name` varchar(64) NOT NULL COMMENT '执行器AppName',
  `title` varchar(64) NOT NULL COMMENT '执行器名称',
  `address_type` tinyint(4) NOT NULL DEFAULT '0' COMMENT '执行器地址类型：0=自动注册、1=手动录入',
  `address_list` text COMMENT '执行器地址列表，多地址逗号分隔',
  `update_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_user` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(50) NOT NULL COMMENT '密码',
  `role` tinyint(4) NOT NULL COMMENT '角色：0-普通用户、1-管理员',
  `permission` varchar(255) DEFAULT NULL COMMENT '权限：执行器IDs，多ID逗号分隔',
  PRIMARY KEY (`id`),
  UNIQUE KEY `i_username` (`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `xxl_job_lock` (
  `lock_name` varchar(50) NOT NULL COMMENT '锁名称',
  PRIMARY KEY (`lock_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始基础数据
INSERT IGNORE INTO `xxl_job_group`(`id`, `app_name`, `title`, `address_type`, `address_list`, `update_time`) VALUES
  (1, 'accounting-core', '金融账务核心执行器', 0, NULL, NOW());

INSERT IGNORE INTO `xxl_job_user`(`id`, `username`, `password`, `role`, `permission`) VALUES
  (1, 'admin', 'e10adc3949ba59abbe56e057f20f883e', 1, NULL);

INSERT IGNORE INTO `xxl_job_lock` (`lock_name`) VALUES ('schedule_lock');

-- 预置账务核心 12 个 XXL-JOB 定时任务（开箱即用，无需登录 admin 手动新建）
INSERT IGNORE INTO `xxl_job_info` (`id`, `job_group`, `job_desc`, `add_time`, `update_time`, `author`, `alarm_email`, `schedule_type`, `schedule_conf`, `misfire_strategy`, `executor_route_strategy`, `executor_handler`, `executor_param`, `executor_block_strategy`, `executor_timeout`, `executor_fail_retry_count`, `glue_type`, `glue_source`, `glue_remark`, `glue_updatetime`, `child_jobid`, `trigger_status`, `trigger_last_time`, `trigger_next_time`) VALUES
  (1, 1, '资金超时自动解冻任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/5 * * * ?', 'DO_NOTHING', 'FIRST', 'autoUnfreezeJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (2, 1, '日间批量入账过账任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/5 * * * ?', 'DO_NOTHING', 'FIRST', 'batchPostingJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (3, 1, '缓冲记账-异步逐条过账削峰任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/1 * * * ?', 'DO_NOTHING', 'FIRST', 'bufferPostingAsyncJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (4, 1, '缓冲记账-日间批量缓冲过账任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/30 * * * ?', 'DO_NOTHING', 'FIRST', 'bufferPostingBatchJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (5, 1, '缓冲记账-日终清算强制过账任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 50 23 * * ?', 'DO_NOTHING', 'FIRST', 'bufferPostingEodJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (6, 1, '日切日终清算与试算平衡任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0 0 * * ?', 'DO_NOTHING', 'FIRST', 'eodJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (7, 1, '财务报表生成与预热调度任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0 1 * * ?', 'DO_NOTHING', 'FIRST', 'financialReportJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (8, 1, '流水挂账与悬挂流水补偿任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/2 * * * ?', 'DO_NOTHING', 'FIRST', 'hangingJournalCompensationJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (9, 1, '事务发件箱本地消息重试补偿任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/1 * * * ?', 'DO_NOTHING', 'FIRST', 'localMessageRetryJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (10, 1, '期末损益与成本结转调度任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 30 23 * * ?', 'DO_NOTHING', 'FIRST', 'periodEndTransferJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (11, 1, '批量红冲冲销补偿任务', NOW(), NOW(), 'accounting', '', 'NONE', '', 'DO_NOTHING', 'FIRST', 'reversalJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0),
  (12, 1, '缓冲记账-失败自动补偿自愈任务', NOW(), NOW(), 'accounting', '', 'CRON', '0 0/5 * * * ?', 'DO_NOTHING', 'FIRST', 'bufferPostingCompensationJob', '', 'SERIAL_EXECUTION', 0, 1, 'BEAN', '', 'GLUE代码初始化', NOW(), '', 0, 0, 0);

