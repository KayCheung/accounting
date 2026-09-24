# Java 编码规范（java.md）

## 一、分层架构规范

```
interfaces/     Controller 仅做参数校验和格式转换，禁止包含业务逻辑
application/    用例编排层，负责事务边界和跨领域协调
domain/         核心业务逻辑，不依赖框架，不直接操作数据库
infrastructure/ 持久化实现，Mapper/Repository/中间件，不包含业务判断
```

- `accounting-api` 模块**严禁**引入 MyBatis-Plus / JDBC / 数据库驱动依赖
  - 允许依赖：`swagger-annotations`、`jackson-annotations`、`jakarta.validation-api`
- Controller 层禁止直接调用 Mapper，必须经过 Application 或 Domain Service

---

## 二、异常体系

```java
GenericException          // 基类，含 errorCode（绑定 ResultCode 枚举）
  ├── ServiceException    // 业务异常（余额不足、规则未找到等可预期异常）
  ├── AccountException    // 账务专项（账户冻结、借贷不平衡、非法账户状态）
  └── AsyncRetryException // 触发异步重试
```

**使用规范**：

- 捕获非业务异常（`SQLException`、`NullPointerException` 等）必须二次封装后抛出
- 抛出时必须指定 `ResultCode` 枚举，**严禁传入魔法数字或硬编码字符串**
- `catch` 块必须打印 `error` 级别日志，携带关键上下文（`accountNo` / `traceNo` 等）
- 全局拦截器 `GlobalExceptionHandler` 统一解析 `ResultCode`，包装为 `ApiResponse` 返回

---

## 三、事务规范

- **严禁使用 `@Transactional`**，必须显式使用 `TransactionTemplate`
- 事务边界：从"按升序加锁账户"开始，到"更新凭证状态"结束
- 事务回滚时必须同步更新 `t_transaction.status = FAILED`，记录 `fail_reason`
- 涉及 RocketMQ 必须实现**本地消息表模式**（Outbox Pattern）：
  - 消息与业务数据**同一事务**写入 `t_local_message`
  - 定时 Job 扫描补偿，重试间隔指数退避（10s / 30s / 60s），最大重试 3 次
  - 超限标记 `status=FAILED` 并触发告警

---

## 四、POJO 规范

- 强制使用 `@Accessors(chain = true)` 支持链式调用
- 所有属性必须使用包装类（`Integer`、`Long`），**禁止基本数据类型**
- PO 类中布尔字段**禁止 `is` 前缀**（数据库 `is_leaf` → Java 字段 `leaf`）
- 枚举映射：
  - MyBatis-Plus 持久化使用 `@EnumValue`
  - Jackson 序列化使用 `@JsonValue`
  - 状态、类型、方向等**严禁魔法值**，必须定义枚举

---

## 五、金额与精度

- 金额必须使用 `BigDecimal`，初始化必须用 **String 构造**

  ```java
  // ✅ 正确
  BigDecimal amount = new BigDecimal("100.000000");
  // ❌ 错误（精度丢失）
  BigDecimal amount = new BigDecimal(100.0);
  ```

- 比较必须使用 `compareTo() == 0`，**严禁 `.equals()`**
- API 层 `LocalDate` / `LocalDateTime` 必须标注：

  ```java
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
  ```

---

## 六、逻辑删除

- `is_delete` 删除值必须是**动态时间戳**（`System.currentTimeMillis()`），禁止固定为 `1`
- 带唯一索引的表，唯一索引字段**必须包含 `is_delete`**，解决逻辑删除后的唯一冲突

---

## 七、注释规范

Service 方法 Javadoc 必须包含：

```java
/**
 * [方法功能：一句话说清楚做什么]
 *
 * 是否记账：是 / 否
 * 异常处理：
 *   - [异常类型] → [处理策略]
 *
 * @param xxx [参数说明]
 */
```

- 余额计算、借贷方向切换逻辑必须标注财务背景
- 状态流转必须在代码邻近位置注明触发条件
- `catch` 块除记录日志外，必须注释说明该异常的业务含义

---

## 八、分页封装规范

```java
Page<XxxPO> page = repository.page(
    new Page<>(request.getPageNo(), request.getPageSize()), wrapper);
return PageResponse.<XxxResponse>builder()
        .total(page.getTotal())
        .pages(page.getPages())
        .current(page.getCurrent())
        .list(page.getRecords().stream()
                .map(converter::toResponse)
                .collect(Collectors.toList()))
        .build();
```
