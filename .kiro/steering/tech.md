# 技术栈与构建规范

## 技术栈

| 层次 | 技术 |
|------|------|
| 语言 / 框架 | Java 17、Spring Boot 3.x、MyBatis-Plus |
| 分布式锁 | Redisson |
| 消息队列 | Aliyun ONS（RocketMQ） |
| 配置中心 | Nacos |
| 定时任务 | XXL-JOB |
| 限流熔断 | Sentinel |
| 可观测性 | Skywalking、Prometheus、Logback |
| 工具库 | Hutool、Lombok、SpringDoc/Swagger |
| 数据库 | MySQL 5.7（按年 RANGE 分区） |
| 缓存 | Redis（Redisson）+ Caffeine 二级缓存 |
| 测试 | JUnit 5 + AssertJ + Mockito |
| 构建工具 | Maven（多模块聚合 pom） |

## 关键依赖约束

- `accounting-api` 模块**严禁**引入 MyBatis-Plus / JDBC / 数据库驱动
  - 仅允许：`swagger-annotations`、`jackson-annotations`、`jakarta.validation-api`

## 常用命令

```bash
# 编译整个项目
mvn clean compile

# 跳过测试打包
mvn clean package -DskipTests

# 运行单元测试
mvn test

# 运行指定模块测试
mvn test -pl accounting-core

# 安装到本地仓库
mvn clean install -DskipTests
```

## DDL 基准

所有 PO / Mapper 生成必须以 `docs/sql/` 下的 DDL 文件为唯一基准，禁止自行推断字段。

| 文件 | 内容 |
|------|------|
| `docs/sql/0-database-schema.sql` | 数据库初始化（字符集/排序规则） |
| `docs/sql/1-account.sql` | 账户域（7 张表） |
| `docs/sql/2-voucher.sql` | 凭证域（4 张表） |
| `docs/sql/3-rule.sql` | 规则域（5 张表） |
| `docs/sql/4-subject.sql` | 科目域（3 张表） |
| `docs/sql/5-journal.sql` | 流水域（3 张表） |
| `docs/sql/6-infra.sql` | 支撑域（5 张表） |
