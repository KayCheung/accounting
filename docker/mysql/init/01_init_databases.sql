-- 01_init_databases.sql
-- 初始化系统所需的所有核心数据库

CREATE DATABASE IF NOT EXISTS `accounting` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `xxl_job` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS `nacos` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON `accounting`.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON `xxl_job`.* TO 'root'@'%';
GRANT ALL PRIVILEGES ON `nacos`.* TO 'root'@'%';
FLUSH PRIVILEGES;
