-- 创建指定字符集和排序规则的数据库
CREATE DATABASE IF NOT EXISTS `accounting`
CHARACTER SET = utf8mb4  -- 字符集（utf8mb4 是 utf8 的超集，支持所有中文/特殊字符）
COLLATE = utf8mb4_unicode_ci;  -- 排序规则（ci 表示不区分大小写）
