# Step 4. Technical Stack & Coding Standards (技术栈与编码规范)

## 1. 财务核心律法 (Financial Logic)
### 1.1 绝对值计算法则 (Absolute Value Logic)
- **禁止 SQL 计算**：严禁 `set balance = balance + ?`，必须在 Java 内存中计算并记录 Pre/Post 余额快照。
- **全过程无负数**：严禁存储负数金额，严禁通过负数冲正。
- **借贷方向驱动算法**：
   - 同向相加：$new = old + amount$。
   - 反向相减：必须前置校验 $old >= amount$，否则抛出 `ServiceException(ResultCode.INSUFFICIENT_BALANCE)` 异常。
- **红冲逻辑**：采用“方向对调”原则。原凭证“借 A 贷 B”，红冲凭证生成“借 B 贷 A”，金额始终保持正数，确保科目总账借贷发生额统计真实准确。

### 1.2 精度保障
- **强制类型**：金额必须使用 `BigDecimal`，初始化必须用 `String` 构造。
- **比较规约**：必须使用 `compareTo() == 0`，严禁使用 `.equals()`。

## 2. 核心规约 (Mandatory Standards)
### 2.1 编码风格
- **链式调用**：POJO/DTO/Entity 强制使用 `@Accessors(chain = true)`。
- **严禁魔法值**：状态、类型、方向强制使用枚举，MyBatis-Plus 映射使用 `@EnumValue`，Jackson 序列化使用 `@JsonValue`。
- **日期规约**：API 层 LocalDate/LocalDateTime 强制标注 `@JsonFormat(pattern = "...", timezone = "GMT+8")`。

### 2.2 事务与一致性
- **编程式事务**：严禁使用 `@Transactional`，必须显式使用 `TransactionTemplate`。
- **分布式一致性**：涉及 RocketMQ 必须实现“本地消息表”模式。

### 2.3 锁与幂等 (Redisson Strategy)
- **分布式锁深植**：不仅在入口层，在 `Posting Engine` 执行引擎前，必须针对 `t_transaction` 级别加锁。
  - **入口幂等锁**：锁 Key 为 `accounting:lock:idempotent:trace:{trace_no}`。拦截 API 重复请求。
  - **事务执行锁**：在 `Posting Engine` 执行前，针对 `t_transaction` 级别加锁，锁 Key 为 `accounting:lock:posting:trx:{voucher_no}`。防止异步重试与实时路径的竞态冲突。
- **锁升序**：涉及多账户操作，必须按 AccountNo 升序执行 `SELECT ... FOR UPDATE`。
- **唯一索引**：`trace_no + trace_seq`、`entry_id`、`voucher_no`、`account_no`、`txn_no` 为终极幂等防线。

### 2.4 逻辑删除规范
- **唯一索引冲突解决**：带有唯一索引的表，索引字段必须包含 `is_delete`。
- **动态删除值**：逻辑删除后的 `is_delete` 值必须是动态唯一的（如时间戳），严禁固定为 1。

## 3. 技术栈约束 (Tech Stack)
- **核心**：Java 17, Spring Boot 3.x, MyBatis-Plus, Redisson, **Aliyun ONS-Client**, Nacos, XXL-JOB, Sentinel, Skywalking, Swagger, Hutool、Prometheus、Logback、Lombok。
- **数据库**：MySQL 5.7
- **测试**：JUnit 5 + AssertJ + Mockito（断言必须使用 AssertJ）。

## 4. 审计与注释要求
- **Javadoc 要求**：Service 方法必须注明：1.业务含义 2.是否记账 3.异常处理策略。
- **核心算法必注**：余额计算公式、借贷方向切换逻辑必须标注财务背景。
- **状态机注释**：所有状态流转必须在代码邻近位置注明触发条件。
- **TODO 与异常注释**：在 `catch` 块中，除了记录日志，必须注释说明该异常。



本阶段旨在确立严苛的财务级编码准则。通过“绝对值计算法则”、编程式事务规约、以及“Redisson 分布式锁 + 数据库唯一索引”的多重幂等机制，确保系统在高并发下余额准确、审计链路清晰。同时适配阿里云 ONS 消息服务实现最终一致性。



## 3. API 文档与标注规范 (Swagger/OpenAPI 3.0)

### 3.1 Controller 标注
- **类级别**：必须标注 `@Tag(name = "...", description = "...")` 描述接口模块。
- **方法级别**：必须标注 `@Operation(summary = "...")` 描述具体的业务功能点。

### 3.2 出入参 DTO 标注
- **类级别**：必须标注 `@Schema(description = "...")` 定义对象含义。
- **字段级别**：必须标注 `@Schema(description = "字段含义", example = "示例值")`，确保 API 文档与代码同步更新。


