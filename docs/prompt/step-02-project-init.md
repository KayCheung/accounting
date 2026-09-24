# step-02-project-init · 工程从零初始化

## 资源声明
| 类型 | 文件 | 说明 |
|------|------|------|
| DDL | `docs/sql/0-database-schema.sql` | 库初始化，Flyway V1 |
| DDL | `docs/sql/1-account.sql` | 账户域，Flyway V2 |
| DDL | `docs/sql/2-voucher.sql` | 凭证域，Flyway V3 |
| DDL | `docs/sql/3-rule.sql` | 规则域，Flyway V4 |
| DDL | `docs/sql/4-subject.sql` | 科目域，Flyway V5 |
| DDL | `docs/sql/5-journal.sql` | 流水域，Flyway V6 |
| DDL | `docs/sql/6-infra.sql` | 支撑域，Flyway V7 |
| 工程结构 | `docs/design/project_structure.md` | 模块划分与包路径规范 |

---

## 1. 任务目标（Mission）

完成工程从零初始化，搭建多模块 Maven 骨架，配置异常、返回体、日志、
持久层等基础组件，验证工程启动及 CI 流水线正常，为后续开发提供稳定工程基础。

---

## 2. 详细子任务列表

### 2.1 生成多模块 Maven 工程骨架
- 参考 `docs/design/project_structure.md`，通过 Vibe Coding 生成符合规范的工程结构
- 模块划分：`accounting-api` / `accounting-core` / `accounting-job` / `accounting-admin`
- `accounting-api` POM 只允许引入：`swagger-annotations` / `jackson-annotations` / `jakarta.validation-api`

### 2.2 配置统一异常体系
- 定义异常基类 `GenericException`（含 `errorCode`）
- 继承关系：`ServiceException` / `AccountException` / `AsyncRetryException`
- 实现 `GlobalExceptionHandler`，统一解析 `ResultCode` 枚举并包装为 `ApiResponse` 返回

### 2.3 配置统一返回体和分页结构
- 封装 `ApiResponse<T>`（含 `code` / `message` / `data` / `traceId` / `timestamp`）
- 封装 `PageResponse<T>`（含 `total` / `pages` / `current` / `list`）

### 2.4 配置日志规范
- 配置 `logback-spring.xml`，区分 local（Console）和 test/prod（File + RollingPolicy）
- 通过 MDC 注入 `traceId`，日志格式包含 `[%X{traceId}]`，实现全链路追踪

### 2.5 配置 Flyway 及版本管理
- 配置 Flyway，将 `docs/sql/` 下六个域文件按顺序纳入版本管理
- 命名规范：`V1__init_schema.sql` → `V7__infra.sql`（对应六域文件顺序）
- 禁止生产环境配置 `flyway.clean-on-validation-error=true`

### 2.6 配置 MyBatis-Plus 基础设施
- 乐观锁插件（`OptimisticLockerInnerInterceptor`）
- 分页插件（`PaginationInnerInterceptor`，指定 MySQL 类型）
- 逻辑删除全局配置：`logic-delete-value` 使用时间戳，`logic-not-delete-value=0`
- 公共字段自动填充（`MetaObjectHandler`：`createTime` / `updateTime`）

### 2.7 验证工程启动及 CI 流水线
- 本地启动无报错，`/actuator/health` 返回 200
- Flyway 执行建表成功，27 张表全部创建
- CI 流水线绿色，多模块工程 PR 合并完成

---

## 3. 完成标准（Checklist）

- [ ] 多模块 Maven 骨架生成，`accounting-api` POM 无持久层依赖
- [ ] 异常体系、`ApiResponse`、`PageResponse`、日志规范配置完成
- [ ] Flyway 执行成功，27 张表全部建表完成
- [ ] MyBatis-Plus 乐观锁 / 分页 / 逻辑删除 / 自动填充配置完成
- [ ] 本地启动无报错，`/actuator/health` 返回 200
- [ ] CI 流水线绿色，PR 合并完成

---

## 4. 下一步行动

进入 **Step 3 · Code Generation**，详见 `docs/prompt/step-03-codegen.md`。