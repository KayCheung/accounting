-- V21: 流水表扩展业务参数与辅助核算动态SpEL支持
ALTER TABLE `t_business_record` ADD COLUMN `extra_attrs` TEXT NULL COMMENT '扩展业务参数JSON' AFTER `summary`;
ALTER TABLE `t_business_detail` ADD COLUMN `extra_attrs` TEXT NULL COMMENT '明细扩展业务参数JSON' AFTER `amount`;
