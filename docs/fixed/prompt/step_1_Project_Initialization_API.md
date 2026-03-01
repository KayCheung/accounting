# Step 1. Project Initialization & API (工程初始化与 API 基础设施)

## 1. 任务目标 (Mission)
按照 `docs/design/project_structure.md` 初始化物理工程并实现“一键启动”。
1. **主权隔离**：`accounting-api` 严禁引入任何持久层（MyBatis-Plus/JDBC）或数据库驱动，保持纯净性，仅包含 DTO、Facade 和枚举。
2. **基建完备**：包含主启动类、基础配置、Web 拦截器及全局异常处理，确保项目可运行。
3. **标准化输出**：API 标准化封装，集成 Swagger (SpringDoc)，展现可用接口文档。

## 2. 工程骨架与 POM 管理
- **根 POM**: 锁定依赖版本，定义 `accounting-api` 与 `accounting-core` 模块。
- **API 模块**: 依赖 `swagger-annotations`, `jackson-annotations`, `jakarta.validation-api`。
- **Core 模块**: 引入 Spring Boot Web, SpringDoc, Lombok, Hutool 等核心运行依赖。

## 3. 基础契约定义 (accounting-api)

### 3.1 统一响应 (response)
- **ResultCode**: 统一定义 SUCCESS(200), PARAM_ERROR(400), UNAUTHORIZED(401), SYSTEM_ERROR(500) 以及业务级的 INSUFFICIENT_BALANCE 等。
- **ApiResponse<T>**: 包含 `code`, `message`, `data`, `traceId`, `timestamp`。
  - `traceId` 通过 `Skywalking` 自动获取。
  - 提供`success()`, `success(T data)`, `fail(ResultCode resultCode)`, `fail(String code, String message)`, `isSuccess()` 等静态方法

### 3.2 分页模型 (request/response)
- **PageRequest**: 包含 `pageNo` (default 1), `pageSize` (default 10)。
- **PageResponse<T>**: 包含 `total`, `pages`, `list`, `current`。

## 4. 启动与基础配置 (accounting-core)
### 4.1 主启动类
- **AccountingApplication**: 放置在 `com.kltb.accounting.core` 包下，配置 `@SpringBootApplication`。

### 4.2 基础配置文件 (bootstrap.yml / application.yml / logback-spring.xml)
- 配置端口 (8080)、应用名称 (`accounting-service`)。
- **上下文路径**：`server.servlet.context-path: /accounting`。
- **序列化规约**：
   - Long 转 String（防精度丢失）。
   - 日期格式化：`yyyy-MM-dd HH:mm:ss`，时区 `GMT+8`。
- 配置 `logback-spring.xml`：
  - 区分 `local` (Console), `test/prod` (File + RollingPolicy)。
  - 日志格式包含 `[%X{traceId}]`（需配合拦截器在 MDC 中注入 traceId）。
- 配置 Swagger/SpringDoc 访问路径。

### 4.3 异常体系
- 定义业务异常基类 `GenericException`，包含 `errorCode`。
- 定义业务异常类 `ServiceException`（继承自 GenericException）。
- 定义异步重试异常类 `AsyncRetryException`（继承自 GenericException）。
- 定义账务专项异常 `AccountException`（继承自 GenericException），用于：余额不足、账户冻结、非法账户状态、借贷不平衡等场景。

### 4.4 Try-Catch 封装规约
- **未知异常转换**：捕获到非业务异常（如 `SQLException`, `NullPointerException`）时，必须通过 `ServiceException` 或 `AccountException` 进行二次封装抛出。
- **errorCode 绑定**：抛出异常时必须指定 `ResultCode` 中的枚举项，严禁直接传入魔术数字或硬编码字符串。
- **日志处理契约**：
  - **Catch 块职责**：仅负责打印 `error` 级别日志（需携带关键上下文参数，如 `accountNo` 或 `traceNo`），禁止吞掉堆栈。
  - **自动补充逻辑**：若捕获到的异常未包含 `errorCode`，封装时应默认补充 `ResultCode.SYSTEM_ERROR` 或 `ResultCode.UNKNOWN_ERROR`。
- **全局拦截**：所有 `GenericException` 及其子类由全局异常处理器（GlobalExceptionHandler）拦截，解析其内部的 `ResultCode` 并包装进 `ApiResponse` 返回前端。

### 4.5 全局异常处理 (GlobalExceptionHandler)
- 捕获 `MethodArgumentNotValidException` (参数校验)、`IllegalArgumentException`、`BindException`、`ConstraintViolationException`、`DataIntegrityViolationException` 等常见异常。
- 捕获 `GenericException` (自定义业务异常)。
- 捕获 `Exception` (兜底系统异常)，统一返回 `ApiResponse`，并通过 log.error 打印异常堆栈，且日志中需包含当前请求的 traceId。

## 5. 下一步行动
- 进入 **Step 2** 接入中间件环境配置（Nacos, Redisson 封装, ONS 初始化）。