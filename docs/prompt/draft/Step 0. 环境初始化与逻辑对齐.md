# Step 0: 环境初始化与逻辑对齐 (Environment Setup & Logic Baseline)

## 一、 角色与上下文 (Role & Context)
你现在是一名资深 JAVA 金融账务架构师。我们将基于已对齐的 DDL 和设计共识开发一套账务核心系统。
**本次迭代暂不做 UI，仅提供后端服务与单元测试。**
**语言约束：Claude 必须全程使用【中文】进行交互、代码表述及代码注释。**

## 二、 总体任务描述
请基于本项目根目录下的设计文档进行开发。你现在的任务是执行 **Step 0：环境初始化与逻辑对齐**。
在开始前，请务必阅读并参考以下路径的文件以对齐理解：
1. **数据库契约**：`docs/sql/1-init-schema.sql`（已在 MySQL 数据库服务器执行，严禁私自变更）。
2. **核心业务流程**：请务必深度解析以下位于 `docs/design/flowchart/` 的 Mermaid 文件：
   - **静态模型 (system_architecture.mmd)**：理解科目、模板、账户的三层嵌套关系，以及子账户（可用/冻结）的设计。
   - **动态路径 (accounting_flow.mmd)**：理解单边记账与全路径记账的区别，确保事务边界在 `TX_MANAGE` 处正确开启。
   - **日终核算 (end_of_day_process.mmd)**：理解总分平衡和借贷平衡的校验逻辑，这决定了生成的 DTO 和 Entity 必须支撑复杂的汇总统计。
3. **功能范围**：参考 `docs/design/prototypes/`（如有），以明确本次迭代的后端边界。
4. **项目骨架**：严格遵循 `docs/design/项目骨架.md` 生成相应的代码。

## 三、 核心逻辑基准 (Logic Baseline)
1. **余额计算**：禁止在 SQL 中使用 `balance = balance + ?`。必须在 Java 代码层执行。
2. **计算法则（绝对值逻辑）**：
   - 全过程禁止出现负数运算。
   - **入账方向与余额方向相同**：执行加法，$new = old + amount$。
   - **入账方向与余额方向相反**：执行减法，必须先校验 $old >= amount$，然后 $new = old - amount$。若余额不足，必须抛出业务异常。
3. **红冲逻辑（反向红冲）**：
   - 取原凭证分录，**借贷方向对调（借变贷，贷变借），金额保持正数**。
   - 严禁通过金额取负数的方式进行冲正。
4. **并发控制**：
   - 实时路径：`SELECT ... FOR UPDATE` 悲观锁。
   - 缓冲路径：`version` 乐观锁。
5. **幂等设计**：
   - 入口幂等：`trace_no + trace_seq`。
   - 分录防重：`entry_id = voucher_no + line_no`。
6. **事务管理**：禁止使用 `@Transactional`，必须使用 `TransactionTemplate` 编程式事务。
7. **一致性保障**：涉及 RocketMQ 场景必须实现“本地消息表”模式。

## 四、 技术栈约束
- **核心**：Java 17 / Spring Boot 3.x / MyBatis-Plus / Redisson / Hutool / Prometheus / Logback / Lombok。
- **中间件**：Spring Cloud Alibaba Nacos（配置中心）、Redis、RocketMQ、XXL-JOB、Sentinel、Skywalking。
- **数据库**：MySQL 5.7（配置托管于 Nacos）。
- **测试**：JUnit 5 + AssertJ + Mockito（断言必须使用 AssertJ）。
- **分层规范**：controller -> service -> domain -> repository。

## 五、 编码规范 (包含《阿里 Java 开发手册》约束)
1. **精度保障**：所有金额必须使用 `BigDecimal`，初始化用 `String` 构造，比较操作必须使用 `compareTo()`，严禁使用 `.equals()`。
2. **审计追踪**：利用 MyBatis-Plus 自动填充 `create_time` 和 `update_time`；所有核心操作必须记录 Logback 日志，包含 `trace_no` 以及 Skywalking 的 `traceId`。
3. **严禁魔法值**：
   - **枚举映射规范**：状态、方向、类型必须使用 `Enum`；数据库 `TINYINT` 字段必须映射为 Java 枚举类。利用 MyBatis-Plus 的 `@EnumValue` 实现持久化，严禁在业务层直接读写数字；同时，枚举类应包含 `code` 和 `desc` 属性，并支持 Jackson 的序列化转换。
   - **配置管理**：所有的配置、阈值必须通过 Nacos 注入。
   - **异常定义**：抛出异常必须使用统一的 `ResultCode` 对象。
4. **异常处理**：定义全局异常拦截器，区分 `SystemException`（系统级错误）、`AsyncRetryException`（异步流程可重试错误）和 `ServiceException`（业务级错误，如余额不足，不重试）。
5. **架构设计与复用**：
   - **拒绝面向过程**：禁止编写“面条方法”，单个方法原则上不超过 80 行。
   - **设计模式驱动**：针对多场景入账规则，必须使用策略模式、工厂模式或模板方法模式来提高代码复用率。
   - **消除 if-else**：优先使用多态、策略模式或枚举映射来替代复杂的嵌套 `if-else` 分支。
6. **逻辑删除与唯一索引规范**：
   - 所有带有唯一索引的表，索引字段必须包含 `is_delete`。
   - 逻辑删除后的值必须是动态唯一的（如 ID、当前时间戳），严禁固定为 1。
   - 在 MyBatis-Plus 配置中，需明确指定 `logic-delete-value` 和 `logic-not-delete-value`。
7. **API 文档规范**： 
   - 使用 Swagger/OpenAPI 3.0 生成 RESTful API 文档，所有 Controller 层接口必须添加 @Operation 注解描述业务功能，DTO 对象必须使用 @Schema 注解定义字段含义，确保 API 文档与代码同步更新。

## 六、 注释规范
- **核心算法必注**：余额计算公式、借贷方向切换逻辑必须标注其财务背景。
- **幂等与锁必注**：在执行 `FOR UPDATE` 和 `trace_no` 校验处注明防护目的。
- **Javadoc 要求**：Service 层方法必须注明：1.业务含义 2.是否记账 3.异常处理策略。
- **状态机与枚举注释**：所有的状态流转必须在代码邻近位置注明触发条件。
- **TODO 与异常注释**：在 `catch` 块中，除了记录日志，必须注释说明该异常是否会导致“账务不平”以及是否需要人工介入。

## 七、 当前指令
请先不要编写具体的业务 Service，请先执行以下操作：
1. **确认共识**：请用中文简要说明你将如何确保在“方向对调”红冲逻辑下，**科目总账**的借贷发生额统计依然准确？以及你如何保证计算过程中完全不涉及负数？
2. **项目骨架**：
   - 规划工程核心目录结构（仅列出关键 package）。
   - 输出核心 `pom.xml`（包含上述所有技术栈依赖）。
3. **输出配置文件**：
   - 输出项目的 `application.yml`，配置指向 Nacos 服务地址。
   - 输出一个 Nacos 配置示例（DataID: accounting-service.yaml），包含数据源、Redis 及 MyBatis-Plus 的配置模板，关键凭据使用占位符表示。
   - **注意**：所有敏感信息（IP、账号、密码）必须使用占位符，我会在运行环境注入。

**请在输出上述内容并得到我的确认后，再进入 Step 1**