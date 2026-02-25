# Step 3: Infrastructure & Common Utilities
请为账务系统生成以下基础支撑类代码：

1. **异常体系**：
    - 定义业务异常基类 `GenericException`，包含 `errorCode`。
    - 定义业务异常类 `ServiceException`（继承自 GenericException）。
    - 定义账务专项异常 `AccountException`（继承自 GenericException），用于：余额不足、账户冻结、非法账户状态、借贷不平衡等场景。
    - 编写 Spring 统一异常处理器 `GlobalExceptionHandler` (@RestControllerAdvice)。
    - **要求**：捕获 `GenericException` 返回业务错误码；捕获 `IllegalArgumentException`、`BindException`、`ConstraintViolationException`、`DataIntegrityViolationException`等常见异常；捕获 `Exception` 返回系统通用错误。并记录 Error 级别日志。
    - 在 `GlobalExceptionHandler` 捕获异常时，除了返回 ApiResponse，必须通过 log.error 打印异常堆栈，且日志中需包含当前请求的 traceId。
    - **Prometheus 联动**：针对不同类型的异常进行计数（使用 Counter），需要包含 `errorCode`、``、`url` 等属性。

2. **财务计算与校验封装**：
    - **注意**：本项目已引入 Hutool 依赖，请优先使用 `NumberUtil` 处理 `BigDecimal`。
    - 额外封装一个简单的 `AmountValidator`：
        - `isPositive(BigDecimal)`：判断金额是否大于 0。
        - `validateBalance(BigDecimal balance, BigDecimal amount)`：校验余额是否足够扣减，不足则抛出 `AccountException`。

3. **树形结构工具类 (TreeUtil)**：
    - **注意**：优先参考 Hutool 的 `TreeUtil` 实现，如果满足不了需求，再自定义泛型静态方法 `buildTree()`。
    - 要求：采用“双层循环 + Map”方式实现。

4. **日志与常量**：
    - 配置 `logback-spring.xml`：
        - 区分 `local` (Console), `test/prod` (File + RollingPolicy)。
        - 日志格式包含 `[%X{traceId}]`（需配合拦截器在 MDC 中注入 traceId）。
    - 定义常量类 `Constant`：如 `CURRENCY_CNY = "CNY"`, `OWNER_INNER = "INNER"`。

5. **Web 统一返回体 (ApiResponse)**：
    - 定义泛型包装类 `ApiResponse<T>`，包含字段：`code`, `data`, `traceId`, `message`, `timestamp`。
    - `traceId` 对应的get方法中通过skywalking提供的TraceContext类获取，不需要提供set方法。
    - 提供静态工厂方法：`success(T data)`, `fail(String code, String msg)`, `isSuccess()`。

6. **MyBatis-Plus 配置**：
    - 编写 `MybatisPlusConfig` 类。
    - 注册 `OptimisticLockerInnerInterceptor`（确保 @Version 生效）。
    - 注册 `PaginationInnerInterceptor`。
