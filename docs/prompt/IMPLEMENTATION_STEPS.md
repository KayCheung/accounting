\# 阶段开发指令集 (Implementation Phases)



\## Phase 0: 背景初始化 (Project Initialization)

请阅读项目中的 `init\_db.sql` 和 `CORE\_DESIGN\_SPEC.md`。理解系统的表结构关联和会计记账准则。

\*\*要求\*\*：在开始前，简要陈述你对本项目“镜像红冲”和“代码层余额计算”逻辑的理解，以确保共识对齐。



---



\## Phase 1: 领域模型与枚举 (Domain Models \& Enums)

请生成以下核心组件：

1\. \*\*枚举类\*\*：`AccountClass` (资产/负债等), `DrCrFlag` (借/贷), `TransStatus`, `BalanceType` (可用/冻结)。

2\. \*\*实体类\*\*：根据 DDL 生成 `Account`, `SubAccount`, `AccountingVoucher`, `VoucherEntryDetail` 等实体。

3\. \*\*要求\*\*：使用 JPA 或 MyBatis-Plus 注解，为 `VoucherEntryDetail` 增加 `generateEntryId()` 方法（拼接凭证号与行号）。



---



\## Phase 2: 计算逻辑与安全沙箱 (Calculator \& Sandbox)

1\. \*\*BalanceCalculator\*\*:

&nbsp;  - 实现方法：`BigDecimal calculate(AccountClass clazz, DrCrFlag direction, BigDecimal amount, BigDecimal currentBalance, boolean isIncrease)`。

2\. \*\*SpEL Sandbox\*\*:

&nbsp;  - 创建 `AccountingRuleEvaluator`，配置 `SimpleEvaluationContext` 只允许 `BigDecimal` 运算和安全工具类，严禁反射和系统调用。



---



\## Phase 3: 基础主数据 CRUD (Master Data)

1\. \*\*t\_dictionary\*\*: 实现通用字典查询，支持按 `dict\_type` 分组缓存。

2\. \*\*t\_account\_subject\*\*: 实现树形科目维护。校验：非末级科目禁止记账。

3\. \*\*t\_account\_template\*\*: 实现开户模板管理。

4\. \*\*AccountFactory\*\*: 当模板 `auto\_open=1` 时，实现自动创建 `t\_account` 及两个 `t\_sub\_account` 的逻辑。



---



\## Phase 4: 规则引擎实现 (Rule Engine)

实现 `RuleEngineService`：

1\. 输入业务流水，匹配 `t\_voucher\_rule`。

2\. 支持方案 A（固定值）和方案 B（SpEL 从 JSON 提取）获取辅助核算项。

3\. 生成 `AccountingVoucher` 及其分录，并校验借贷平衡。



---



\## Phase 5: 实时入账服务 (Real-Time Posting)

实现 `RealTimePostingService`：

1\. 开启事务，使用 `FOR UPDATE` 锁定账户。

2\. 调用 `BalanceCalculator` 计算新余额。

3\. 同步写入 `t\_account\_detail`。

4\. 必须通过 `trace\_no` 实现前置幂等校验。



---



\## Phase 6: 红冲处理器 (Storno Processor)

实现 `StornoService`：

1\. 输入 `orig\_trace\_no`，定位原凭证。

2\. 镜像生成分录：方向不变，金额 = 原金额 \* -1。

3\. 联动处理：若涉及冻结扣款，需自动执行反向冻结回滚。



---



\## Phase 7: 缓冲入账与 Job (Buffer Posting)

1\. 实现分录写入 `t\_buffer\_posting\_detail`。

2\. 实现 `BufferPostingJob`：按账户分组汇总金额，使用乐观锁（version）批量更新账户余额。



---



\## Phase 8: 日终核算与平衡检查 (Day-End Processing)

1\. \*\*AccountingDateRoll\*\*: 切换会计日期，校验前日事务是否闭环。

2\. \*\*Reconciliation\*\*: 

&nbsp;  - 检查当日借贷发生额合计是否相等。

&nbsp;  - 检查总账余额变动是否等于分户明细变动之和。

3\. 生成 `t\_account\_balance` 记录。



---



\## Phase 9: 接口设计 (API Design)

1\. \*\*入账接口\*\*: `POST /v1/accounting/post`，支持复杂明细。

2\. \*\*冻结接口\*\*: `POST /v1/account/freeze`，同步平移子账户余额。

3\. \*\*查询接口\*\*: 实时余额查询与带索引的分页流水查询。



---



\## Phase 10: 单元测试 (Testing)

1\. 编写并发压力测试，验证 50 个线程竞争同一账户时悲观锁的有效性。

2\. 编写幂等测试，模拟相同 `trace\_no` 的并发请求。

3\. 编写红冲全链路测试。

