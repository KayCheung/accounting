---
inclusion: always
---

# 技术栈与编码规范 (Technical Stack & Coding Standards)

本文件定义了账务核心系统的技术栈选型和强制编码规范。

## 财务核心律法

### 绝对值计算法则 (Absolute Value Logic)

这是金融系统的核心原则，必须严格遵守：

1. **禁止 SQL 计算**
   - 严禁 `UPDATE t_account SET balance = balance + ?`
   - 必须在 Java 内存中计算并记录 Pre/Post 余额快照
   - 示例：
     ```java
     // 错误 ❌
     mapper.updateBalance(accountNo, amount);
     
     // 正确 ✅
     BigDecimal preBalance = account.getBalance();
     BigDecimal postBalance = calculateNewBalance(preBalance, amount, direction);
     account.setBalance(postBalance);
     mapper.updateById(account);
     // 记录明细时保存 preBalance 和 postBalance
     ```

2. **全过程无负数**
   - 严禁存储负数金额
   - 严禁通过负数冲正
   - 所有金额字段必须 >= 0

3. **借贷方向驱动算法**
   - 同向相加：`newBalance = oldBalance + amount`
   - 反向相减：必须前置校验 `oldBalance >= amount`，否则抛出 `ServiceException(ResultCode.INSUFFICIENT_BALANCE)`
   - 示例：
     ```java
     // 借方账户，借方发生额（同向）
     if (accountDirection == debitCredit) {
         newBalance = oldBalance.add(amount);
     } 
     // 借方账户，贷方发生额（反向）
     else {
         if (oldBalance.compareTo(amount) < 0) {
             throw new ServiceException(ResultCode.INSUFFICIENT_BALANCE);
         }
         newBalance = oldBalance.subtract(amount);
     }
     ```

4. **红冲逻辑**
   - 采用"方向对调"原则
   - 原凭证"借 A 贷 B"，红冲凭证生成"借 B 贷 A"
   - 金额始终保持正数
   - 确保科目总账借贷发生额统计真实准确

### 精度保障

1. **强制类型**
   - 金额必须使用 `BigDecimal`
   - 初始化必须用 `String` 构造：`new BigDecimal("100.00")`
   - 禁止使用 `double` 或 `float`

2. **比较规约**
   - 必须使用 `compareTo() == 0`
   - 严禁使用 `.equals()`
   - 示例：
     ```java
     // 错误 ❌
     if (amount.equals(BigDecimal.ZERO)) { }
     
     // 正确 ✅
     if (amount.compareTo(BigDecimal.ZERO) == 0) { }
     ```

## 核心规约

### 编码风格 (遵循阿里巴巴编码规范)

1. **链式调用**
   - POJO/DTO/Entity 强制使用 `@Accessors(chain = true)`
   - 示例：
     ```java
     @Data
     @Accessors(chain = true)
     public class Account {
         private String accountNo;
         private BigDecimal balance;
     }
     // 使用：account.setAccountNo("123").setBalance(new BigDecimal("100"));
     ```

2. **严禁魔法值**
   - 状态、类型、方向强制使用枚举
   - MyBatis-Plus 映射使用 `@EnumValue`
   - Jackson 序列化使用 `@JsonValue`
   - 示例：
     ```java
     @Getter
     @AllArgsConstructor
     public enum DebitCredit {
         DEBIT(1, "借"),
         CREDIT(2, "贷");
         
         @EnumValue
         @JsonValue
         private final Integer code;
         private final String desc;
     }
     ```

3. **日期规约**
   - API 层 LocalDate/LocalDateTime 强制标注 `@JsonFormat`
   - 示例：
     ```java
     @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
     private LocalDateTime createTime;
     ```

4. **POJO 命名规约**
   - 布尔类型字段在 PO 类中**禁止添加 `is` 前缀**
   - 数据库 `is_leaf` 对应 Java 字段为 `Boolean leaf`（不是 `isLeaf`）
   - 包装类使用：所有 POJO 属性必须使用包装类（`Integer`, `Long`, `Boolean` 等）
   - 禁止使用基本数据类型（`int`, `long`, `boolean` 等）

### 事务与一致性

1. **编程式事务**
   - 严禁使用 `@Transactional`
   - 必须显式使用 `TransactionTemplate`
   - 示例：
     ```java
     @Autowired
     private TransactionTemplate transactionTemplate;
     
     public void posting() {
         transactionTemplate.execute(status -> {
             try {
                 // 业务逻辑
                 return true;
             } catch (Exception e) {
                 status.setRollbackOnly();
                 throw e;
             }
         });
     }
     ```

2. **分布式一致性**
   - 涉及 RocketMQ 必须实现"本地消息表"模式
   - 确保业务操作与消息发送的原子性

### 锁与幂等 (Redisson Strategy)

1. **分布式锁深植**
   - **入口幂等锁**：锁 Key 为 `accounting:lock:idempotent:trace:{trace_no}`，拦截 API 重复请求
   - **事务执行锁**：在 Posting Engine 执行前，针对 `t_transaction` 级别加锁，锁 Key 为 `accounting:lock:posting:trx:{voucher_no}`，防止异步重试与实时路径的竞态冲突
   - 示例：
     ```java
     RLock lock = redissonClient.getLock("accounting:lock:idempotent:trace:" + traceNo);
     try {
         if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
             // 业务逻辑
         }
     } finally {
         if (lock.isHeldByCurrentThread()) {
             lock.unlock();
         }
     }
     ```

2. **锁升序**
   - 涉及多账户操作，必须按 `account_no` 升序执行 `SELECT ... FOR UPDATE`
   - 防止死锁

3. **唯一索引**
   - `trace_no + trace_seq`、`entry_id`、`voucher_no`、`account_no`、`txn_no` 为终极幂等防线

### 逻辑删除规范

1. **唯一索引冲突解决**
   - 带有唯一索引的表，索引字段必须包含 `is_delete`
   - 示例：`UNIQUE KEY uk_subject_code (subject_code, is_delete)`

2. **动态删除值**
   - 逻辑删除后的 `is_delete` 值必须是动态唯一的（如时间戳）
   - 严禁固定为 1
   - 示例：
     ```java
     // 错误 ❌
     entity.setIsDelete(1L);
     
     // 正确 ✅
     entity.setIsDelete(System.currentTimeMillis());
     ```

## API 文档与标注规范

### Swagger/OpenAPI 3.0 标注

1. **Controller 标注**
   - 类级别：`@Tag(name = "...", description = "...")`
   - 方法级别：`@Operation(summary = "...")`
   - 示例：
     ```java
     @Tag(name = "账户管理", description = "账户开户、查询、状态管理")
     @RestController
     @RequestMapping("/api/account")
     public class AccountController {
         
         @Operation(summary = "客户账户开户")
         @PostMapping("/open")
         public ApiResponse<AccountResponse> openAccount(@RequestBody AccountRequest request) {
             // ...
         }
     }
     ```

2. **出入参 DTO 标注**
   - 类级别：`@Schema(description = "...")`
   - 字段级别：`@Schema(description = "...", example = "...")`
   - 示例：
     ```java
     @Data
     @Schema(description = "账户开户请求")
     public class AccountRequest {
         @Schema(description = "客户ID", example = "C123456")
         private String customerId;
         
         @Schema(description = "账户类型", example = "BASIC")
         private String accountType;
     }
     ```

## 技术栈约束

### 核心技术栈
- **Java**：Java 17
- **框架**：Spring Boot 3.x
- **ORM**：MyBatis-Plus
- **分布式**：Redisson（分布式锁）、Nacos（配置中心）
- **消息队列**：Aliyun ONS-Client (RocketMQ)
- **任务调度**：XXL-JOB
- **限流熔断**：Sentinel
- **链路追踪**：Skywalking
- **API 文档**：Swagger (OpenAPI 3.0)
- **工具库**：Hutool、Lombok
- **监控**：Prometheus
- **日志**：Logback

### 数据库
- **MySQL**：5.7+
- **字符集**：utf8mb4
- **排序规则**：utf8mb4_unicode_ci

### 测试
- **框架**：JUnit 5 + Mockito
- **断言**：AssertJ（强制使用，禁止使用 JUnit 自带断言）
- 示例：
  ```java
  // 错误 ❌
  assertEquals(expected, actual);
  
  // 正确 ✅
  assertThat(actual).isEqualTo(expected);
  ```

## 审计与注释要求

### Javadoc 要求
Service 方法必须注明：
1. 业务含义
2. 是否记账
3. 异常处理策略

示例：
```java
/**
 * 客户账户开户
 * 
 * 业务说明：基于开户模板自动创建客户账户及子账户（可用/冻结）
 * 记账影响：不直接记账，仅初始化账户结构
 * 异常处理：开户失败抛出 ServiceException，事务回滚
 * 
 * @param request 开户请求
 * @return 账户信息
 * @throws ServiceException 开户失败
 */
public AccountResponse openAccount(AccountRequest request) {
    // ...
}
```

### 核心算法必注
余额计算公式、借贷方向切换逻辑必须标注财务背景：
```java
// 财务逻辑：借方账户收到借方发生额，余额增加（同向相加）
if (account.getBalanceDirection() == DebitCredit.DEBIT 
    && entry.getDebitCredit() == DebitCredit.DEBIT) {
    newBalance = oldBalance.add(amount);
}
```

### 状态机注释
所有状态流转必须在代码邻近位置注明触发条件：
```java
// 状态机：PENDING → PROCESSING（开始记账）
transaction.setStatus(TransactionStatus.PROCESSING);
```

### TODO 与异常注释
在 `catch` 块中，除了记录日志，必须注释说明该异常：
```java
try {
    // ...
} catch (InsufficientBalanceException e) {
    // 余额不足异常：账户可用余额小于交易金额，拒绝交易
    log.error("余额不足，accountNo={}, amount={}", accountNo, amount, e);
    throw new ServiceException(ResultCode.INSUFFICIENT_BALANCE);
}
```
