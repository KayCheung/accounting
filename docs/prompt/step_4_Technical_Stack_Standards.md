# Step 4. Technical Stack & Coding Standards (技术栈与编码规范)

## 1. 任务目标 (Mission)
本阶段旨在确立严苛的财务级编码准则。通过“绝对值计算法则”、编程式事务规约、以及“Redisson 分布式锁 + 数据库唯一索引”的多重幂等机制，确保系统在高并发下余额准确、审计链路清晰。同时适配阿里云 ONS 消息服务实现最终一致性。

## 2. 项目骨架 (Project Structure)
严格遵循 `docs/design/项目骨架.md` 进行代码生成。保持 `accounting-api` 纯净性，严禁引入持久层依赖。

## 3. 财务核心算法规约 (Financial Logic)

### 3.1 余额计算法则 (Absolute Value Logic)
1. **禁止 SQL 计算**：严禁在 SQL 中执行 `balance = balance + ?`。所有计算必须在 Java 内存中完成，以便记录 Pre/Post 余额快照。
2. **全过程无负数**：严禁通过金额取负数的方式进行冲正。严禁存储负数金额。
3. **方向驱动算法**：
    - **入账方向与余额方向相同**：执行加法，$new = old + amount$。
    - **入账方向与余额方向相反**：执行减法。**强制前置校验**：若 $old < amount$，必须抛出 `ServiceException(ResultCode.INSUFFICIENT_BALANCE)`；否则执行 $new = old - amount$。
4. **红冲逻辑**：采用“方向对调”原则。原凭证“借 A 贷 B”，红冲凭证生成“借 B 贷 A”，金额始终保持正数，确保科目总账借贷发生额统计真实准确。

### 3.2 精度保障
- **强制类型**：所有金额必须使用 `BigDecimal`，初始化必须使用 `String` 构造。
- **比较操作**：必须使用 `compareTo() == 0`，严禁使用 `.equals()`。

## 4. 并发与幂等控制 (Concurrency & Idempotency)

### 4.1 多重幂等防护网
1. **前置防重 (Redisson)**：利用 `Redisson` 分布式锁，锁 Key 为 `lock:accounting:idempotent:{trace_no}`。
    - 请求进入时申请锁，若无法获取则判定为“重复请求”或“正在处理中”。
2. **终极防线 (DB)**：利用 `t_business_record` 表中 `trace_no + trace_seq` 的唯一索引确保强幂等。
3. **分录防重**：生成的 `entry_id` 需满足 `voucher_no + line_no` 的唯一性约束。

### 4.2 并发锁策略
- **实时路径**：必须按账户主键（`account_no/sub_account_no`）升序执行 `SELECT ... FOR UPDATE` 悲观锁。
- **缓冲路径**：优先使用 `version` 乐观锁；若失败，重试逻辑应升级为悲观锁。

## 5. 编码规范 (Alibaba Java Standards)

### 5.1 事务与一致性
- **禁止声明式事务**：禁止使用 `@Transactional`，必须使用 `TransactionTemplate` 编程式事务。
- **双层更新**：涉及余额变动的业务必须在一个事务内同步更新 `Account` (总账) 与 `SubAccount` (分户)，并记录双层明细。
- **分布式一致性**：使用阿里云 **ONS SDK**。涉及 RocketMQ 异步场景，必须实现“本地消息表”模式。

### 5.2 严禁魔法值
- **枚举映射**：所有状态、方向、类型必须使用 `Enum`。数据库 `TINYINT` 映射为 Java 枚举，利用 MyBatis-Plus 的 `@EnumValue` 持久化。

### 5.3 逻辑删除规范
- **唯一索引冲突解决**：带有唯一索引的表，索引字段必须包含 `is_delete`。
- **动态删除值**：逻辑删除后的 `is_delete` 值必须是动态唯一的（如 ID 或时间戳），严禁固定为 1。

## 6. 技术栈约束 (Tech Stack)
- **核心组件**：Java 17, Spring Boot 3.x, MyBatis-Plus, Redisson, **Aliyun ONS-Client**, Nacos, XXL-JOB, Swagger。
- **中间件**：Spring Cloud Alibaba Nacos（配置中心）、Redis、XXL-JOB、Sentinel、Skywalking。
- **数据库**：MySQL 5.7（配置托管于 Nacos）。
- **测试框架**：JUnit 5 + AssertJ (断言必须使用 AssertJ)。

## 7. 审计与注释要求
- **Javadoc 要求**：Service 方法必须注明：1.业务含义 2.是否记账 3.异常处理策略。
- **状态机注释**：所有状态流转必须在代码邻近位置注明触发条件。