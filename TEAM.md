# FIN-Core · 团队开发进度与分工

> 本文件供团队协作使用，记录完整的 25 个 Step 任务、角色分工与完成标准。
> AI 编程助手规范见 `docs/ai-rules/`，进度追踪见 `docs/prompt/FIN-Core_Blueprint.md`。

---

## 角色说明

| 标识 | 角色 | 职责 |
|------|------|------|
| TL | Tech Lead | 架构决策、高风险模块主攻、Code Review 把关 |
| BE-A | 后端工程师 A | 账户侧、冻结、余额查询 |
| BE-B | 后端工程师 B | 凭证侧、规则、中间件、MQ |
| VC | Vibe Coder | 前端页面、Prompt 管理、低风险代码生成 |

---

## Phase 1：契约、规范与架构
> 周期：Week 1 ｜ 目标：Rules 就位，VC 能写规范 Prompt

### Step 1 · Governance & Constraints｜开发契约与规范
> 📁 `docs/prompt/step-01-governance.md` ｜ ⏱ 1天 ｜ TL 主导

- [ ] **1.1** TL：制定 Rules 三层规则文件
  - `docs/ai-rules/general.md`：角色定义、技术栈、输出格式约束
  - `docs/ai-rules/java.md`：分层架构、异常处理、事务边界、BigDecimal 强制
  - `docs/ai-rules/accounting.md`：幂等键、借贷平衡、锁顺序、领域术语速查
- [ ] **1.2** TL：为 VC 制作《Vibe Coding 一页纸快速上手》
  - Prompt 四段式模板（任务 / 输入 / 输出 / 不要做）
  - 会话拆分原则 + 上下文管理规则
  - 「复述确认」技巧：要求 AI 先复述任务再执行
- [ ] **1.3** VC：按照规范初始化 `docs/prompt/` 目录骨架，创建全部 Step 占位文件
- [ ] **1.4** 全员：30 分钟对齐会，确认核心约束；VC 完成第一个 Prompt 练习

**完成标准**：`docs/ai-rules/` 三文件就位；VC 能独立写出符合规范的 Prompt

---

## Phase 2：工程基础
> 周期：Week 2~4 ｜ 目标：工程启动、持久层、中间件、领域对齐

### Step 2 · Project Initialization｜工程从零初始化
> 📁 `docs/prompt/step-02-project-init.md` ｜ ⏱ 3天 ｜ TL 主导，BE-A 协同

- [ ] **2.1** TL：用 Vibe Coding 生成多模块 Maven 工程骨架
- [ ] **2.2** TL：配置统一异常体系（`ServiceException` / `ResultCode` / `GlobalExceptionHandler`）
- [ ] **2.3** TL：配置统一返回体 `Result<T>` 和分页结构 `PageResult<T>`
- [ ] **2.4** TL：配置日志规范（MDC 注入 `traceNo`）
- [ ] **2.5** BE-A：配置 Flyway，将 `init-schema.sql` 纳入版本管理
- [ ] **2.6** BE-A：配置 MyBatis-Plus 基础设施（乐观锁 / 分页 / 逻辑删除）
- [ ] **2.7** TL：验证工程启动，`/actuator/health` 返回 200，CI 流水线绿色

**完成标准**：多模块工程 PR 合并；本地启动无报错；Flyway 建表成功

---

### Step 3 · Code Generation｜持久层批量生成
> 📁 `docs/prompt/step-03-codegen.md` ｜ ⏱ 2天 ｜ BE-A + BE-B 并行

- [ ] **3.1** BE-A：用 Vibe Coding 基于 DDL 批量生成账户侧 PO / Mapper（8 张表）
- [ ] **3.2** BE-B：用 Vibe Coding 批量生成凭证+规则侧 PO / Mapper（17 张表）
- [ ] **3.3** TL：Review 全部生成代码（分区表注解 / @Version / @TableLogic / 唯一索引）

**完成标准**：全部 PO / Mapper 生成完毕；基础 CRUD 单测通过；TL Review 无遗留

---

### Step 4 · Middleware Integration｜中间件集成
> 📁 `docs/prompt/step-04-middleware.md` ｜ ⏱ 3天 ｜ BE-B 主攻，TL Review

- [ ] **4.1** BE-B：集成 RocketMQ，封装 `MqProducer` / `AbstractMqConsumer`（含幂等校验）
- [ ] **4.2** BE-B：实现本地消息表机制（Outbox Pattern），定时补偿 Job，最大重试 3 次
- [ ] **4.3** BE-B：集成 Redis，封装分布式锁（含自动续期）和字典缓存
- [ ] **4.4** TL：配置 Prometheus + Actuator，P0/P1 告警规则

**完成标准**：MQ 收发 Demo 通过；本地消息表补偿重试单测通过；Redis 锁单测通过

---

### Step 5 · Domain Alignment｜领域模型对齐
> 📁 `docs/prompt/step-05-alignment.md` ｜ ⏱ 2天 ｜ TL 输出，全员对齐

- [ ] **5.1** TL：输出精简版领域模型速查表（25 张表核心字段 + 四张状态机 + 主流程泳道图）
- [ ] **5.2** VC：将 5.1 产出录入 AI 工具的 Projects 作为全局背景上下文
- [ ] **5.3** BE-A：补充账户侧自定义 Mapper（`selectForUpdate` / `selectWithSubAccounts`）
- [ ] **5.4** BE-B：补充凭证侧自定义 Mapper（联查 / 统计 / 缓冲扫描）
- [ ] **5.5** TL：`EXPLAIN` 验证所有自定义查询走索引，无全表扫描

**完成标准**：背景文档就位；所有自定义 Mapper 有单测；查询全部命中索引

---

## Phase 3：配置管理模块
> 周期：Week 5~7 ｜ 目标：F-1~F-5 后端接口就绪

### Step 6 · Dict & Subject API｜字典与科目接口
> 📁 `docs/prompt/step-06-dict-subject.md` ｜ ⏱ 4天 ｜ BE-A

- [ ] **6.1** BE-A：字典管理接口（F-1），含 Redis 缓存 TTL 10 分钟 + 手动刷新
- [ ] **6.2** BE-A：会计科目接口（F-2），含树形查询、层级校验、末级自动开户联动

**完成标准**：接口单测全通；停用联动校验覆盖；树形查询 < 200ms

---

### Step 7 · Template & Rule API｜模板与规则接口
> 📁 `docs/prompt/step-07-template-rule.md` ｜ ⏱ 5天 ｜ BE-B

- [ ] **7.1** BE-B：开户模板接口（F-4），含联合唯一校验
- [ ] **7.2** BE-B：记账规则接口（F-3），含 SpEL 脚本 dry-run 校验 + 规则缓存热更新
- [ ] **7.3** BE-B：缓冲规则接口（F-5）

**完成标准**：规则 CRUD 单测全通；SpEL 错误脚本被拒绝；规则缓存热更新单测通过

---

## Phase 4：记账核心引擎
> 周期：Week 8~11 ｜ ⚠️ 最高风险阶段，严格串行，前一 Step 验收后才开下一 Step

### Step 8 · Account Auto-Opening｜自动化开户
> 📁 `docs/prompt/step-08-account-opening.md` ｜ ⏱ 3天 ｜ BE-A

- [ ] **8.1** BE-A：账户存在性检查 + 匹配开户模板（`business_code + customer_type + subject_code`）
- [ ] **8.2** BE-A：自动开户（创建 `t_account` + 可用 / 冻结两个 `t_sub_account`）
- [ ] **8.3** BE-A：并发开户幂等（`SELECT FOR UPDATE` + 唯一键冲突降级查询）

**完成标准**：100 并发同账户开户只生成一条记录；幂等单测通过

---

### Step 9 · Journaling｜业务流水入库
> 📁 `docs/prompt/step-09-journaling.md` ｜ ⏱ 2天 ｜ BE-B

- [ ] **9.1** BE-B：幂等校验（`trace_no + trace_seq`）
- [ ] **9.2** BE-B：入参校验（金额 > 0、必填字段、`business_code` 合法性）
- [ ] **9.3** BE-B：持久化流水（`t_business_record` 确定会计日期 + `t_business_detail`）

**完成标准**：100 并发同 `trace_no` 只写入一条；幂等返回结果完全一致

---

### Step 10 · Vouchering｜凭证生成引擎
> 📁 `docs/prompt/step-10-vouchering.md` ｜ ⏱ 6天 ｜ TL 主攻，BE-B 协助测试

- [ ] **10.1** TL：规则匹配（缓存优先，未命中查 DB 回填）
- [ ] **10.2** TL：SpEL 脚本解析引擎（预加载 + 热更新 + 异常捕获）
- [ ] **10.3** TL：借贷平衡校验（不平衡则阻断，不写库）
- [ ] **10.4** TL：辅助核算项分摊（前 N-1 按比例，最后一条补差）
- [ ] **10.5** TL：缓冲规则匹配与路径分流
- [ ] **10.6** TL：凭证号生成（雪花算法）
- [ ] **10.7** BE-B：编写 12 类集成测试场景

**完成标准**：12 类场景全通；借贷不平衡 / SpEL 错误 / 规则未找到均被阻断

---

### Step 11 · Transaction Management｜事务管理
> 📁 `docs/prompt/step-11-transaction.md` ｜ ⏱ 2天 ｜ TL

- [ ] **11.1** TL：`t_transaction` 记录创建（初始 `status=PROCESSING`）
- [ ] **11.2** TL：凭证与事务关联（先创建凭证，事务创建后回填 `txn_no`）
- [ ] **11.3** TL：配置 `TransactionTemplate`，明确边界，回滚时更新事务状态

**完成标准**：事务与凭证关联正确；回滚场景下事务状态更新单测通过

---

### Step 12 · Posting Engine｜过账引擎
> 📁 `docs/prompt/step-12-posting.md` ｜ ⏱ 7天 ｜ TL 主攻，BE-A 协同

- [ ] **12.1** TL：单边记账分流（`is_unilateral=1` 实时 / `is_unilateral=0` 发 MQ）
- [ ] **12.2** TL：悲观锁控制（`account_no` 升序 `SELECT FOR UPDATE`）
- [ ] **12.3** TL：余额计算（绝对值法则，含前置余额不足校验）
- [ ] **12.4** TL：余额不足处理（事务回滚 + 事务状态 FAILED）
- [ ] **12.5** BE-A：余额变动记录（更新 `t_account` / `t_sub_account` + 写 Pre/Post 快照明细）
- [ ] **12.6** TL：凭证状态收尾（全部分录过账 → POSTED，否则 POSTING）
- [ ] **12.7** TL：异步分录失败回滚（反向操作余额，写反向明细，更新凭证 FAILED）
- [ ] **12.8** TL：端到端全链路联调（Step 9→10→11→12 串联）

**完成标准**：200 并发压测余额零误差；死锁场景测试通过；异步失败后余额正确回滚

---

## Phase 5：账户与冻结模块
> 周期：Week 12~13 ｜ BE-A 主攻

### Step 13 · Account Status Control｜账户状态管理
> 📁 `docs/prompt/step-13-account-status.md` ｜ ⏱ 2天 ｜ BE-A

- [ ] **13.1** BE-A：账户状态机（正常 → 冻结 → 注销，非法转换返回明确错误）
- [ ] **13.2** BE-A：风控状态管理（止入 / 止出 / 止入止出）

**完成标准**：止出账户出款被拦截；非法状态转换返回明确错误码

---

### Step 14 · Freeze & Unfreeze｜资金冻结与解冻
> 📁 `docs/prompt/step-14-freeze.md` ｜ ⏱ 4天 ｜ BE-A

- [ ] **14.1** BE-A：冻结（可用子账户减，冻结子账户增，主账户余额不变）
- [ ] **14.2** BE-A：解冻（反向操作）
- [ ] **14.3** BE-A：冻结扣款（冻结子账户减 + 主账户余额减）
- [ ] **14.4** BE-A：超时自动解冻 Job（扫描 `expire_time < NOW()` + 自动触发解冻）

**完成标准**：冻结+解冻后主账户余额不变；超时自动解冻单测通过

---

### Step 15 · Balance Query API｜余额查询接口
> 📁 `docs/prompt/step-15-balance-query.md` ｜ ⏱ 2天 ｜ BE-A

- [ ] **15.1** BE-A：聚合余额查询（主账户 + 可用 + 冻结 + 缓冲预估）
- [ ] **15.2** BE-A：账户明细分页查询（按日期范围 / 类型过滤，走分区索引）
- [ ] **15.3** BE-A：冻结记录查询

**完成标准**：聚合查询 P99 < 100ms；明细查询确认分区裁剪生效

---

## Phase 6：缓冲记账、日切与红冲
> 周期：Week 14~15 ｜ BE-B 主攻缓冲+红冲，TL 主攻日切

### Step 16 · Buffer Posting｜缓冲记账
> 📁 `docs/prompt/step-16-buffer-posting.md` ｜ ⏱ 4天 ｜ BE-B

- [ ] **16.1** BE-B：逐条缓冲（buffer_mode=1）
- [ ] **16.2** BE-B：日间批量（buffer_mode=2，按账户汇总更新）
- [ ] **16.3** BE-B：Running Balance 计算（末条 `post_balance` 必须等于账户余额，否则告警）
- [ ] **16.4** BE-B：锁升级策略（乐观锁 → 失败 3 次 → 悲观锁 → 仍失败 → 告警）
- [ ] **16.5** BE-B：Job 分片策略（按 `account_no` 哈希取模）

**完成标准**：1 万条缓冲明细批处理后 Running Balance 末条与账户余额完全一致

---

### Step 17 · EOD & Trial Balance｜日切与试算平衡
> 📁 `docs/prompt/step-17-eod.md` ｜ ⏱ 5天 ｜ TL 主攻，BE-A 协同

- [ ] **17.1** TL：逻辑日切（全局会计日期更新为 T+1，刷新缓存）
- [ ] **17.2** TL：存量清理（失败则 P0 阻断，不得强制推进）
- [ ] **17.3** BE-A：余额快照（写 `t_account_balance_snapshot`）
- [ ] **17.4** TL：试算平衡三项校验（借贷平衡 + 总分核对 + 余额核对，失败则阻断）
- [ ] **17.5** TL：期末结转（可选，按 `t_period_end_transfer_rule` 执行）
- [ ] **17.6** BE-A：日切 Job 五阶段编排

**完成标准**：100 账户模拟日切三项校验全通；存量清理不遗漏

---

### Step 18 · Reversal & Red Offset｜冲账与红冲
> 📁 `docs/prompt/step-18-reversal.md` ｜ ⏱ 2天 ｜ BE-B

- [ ] **18.1** BE-B：红冲前置校验（原凭证已过账 + 无重复红冲）
- [ ] **18.2** BE-B：红冲凭证生成（借贷方向对调，金额保持正数）
- [ ] **18.3** BE-B：走标准过账流程，原凭证更新 `status=5`（已冲销）

**完成标准**：红冲后科目借贷发生额均增加；重复红冲被拦截

---

## Phase 7：MCP 接入
> 周期：Week 16 ｜ BE-B 主攻，TL 把关权限设计

### Step 19 · MCP Server Integration｜MCP 接入
> 📁 `docs/prompt/step-19-mcp.md` ｜ ⏱ 5天 ｜ BE-B + TL

- [ ] **19.1** TL：MCP Tool 权限分级设计（查询类直接开放 / 写入类需确认 / 管控类限角色）
- [ ] **19.2** BE-B：基于 Spring AI 搭建 MCP Server 骨架（`accounting-mcp` 模块）
- [ ] **19.3** BE-B：查询类 Tool 实现（余额 / 凭证 / 冻结 / 试算结果）
- [ ] **19.4** BE-B：写入类 Tool 实现（含确认步骤）
- [ ] **19.5** BE-B：MCP 审计日志
- [ ] **19.6** TL：MCP Inspector 本地调试验证
- [ ] **19.7** BE-B：对接企业级 Agent 平台，完成集成测试

**完成标准**：Agent 通过 MCP 完成一次真实记账；未授权 Tool 调用被拒绝

---

## Phase 8：存量数据迁移
> 周期：Week 17 ｜ BE-B 主攻脚本，TL + BE-A 核对

### Step 20 · Data Migration｜存量数据迁移
> 📁 `docs/prompt/step-20-migration.md` ｜ ⏱ 1周执行 + 核对缓冲 ｜ BE-B + TL

- [ ] **20.1** BE-B：输出字段映射表（高风险字段标红）
- [ ] **20.2** BE-B：用 Vibe Coding 生成数据清洗脚本
- [ ] **20.3** BE-B：迁移沙箱试跑，记录异常
- [ ] **20.4** TL：设计三级核对机制（L1 记录数 / L2 余额 / L3 流水抽样 1%）
- [ ] **20.5** BE-B：分批迁移执行
- [ ] **20.6** TL + BE-A：执行三级核对，差异归档
- [ ] **20.7** 全员：业务方与财务确认签收，输出《迁移完整性报告》

**完成标准**：三级核对通过率 ≥ 99.9%；业务方签收

---

## Phase 9：前端集中交付（与 Phase 6~8 并行）
> 周期：Week 15~18 ｜ VC 主导

### Step 21 · Frontend Infrastructure｜前端工程初始化
> 📁 `docs/prompt/step-21-frontend-init.md` ｜ ⏱ 2天 ｜ VC

- [ ] **21.1~21.5** VC：Vue 3 + Vite + Element Plus + Pinia 工程初始化，含 axios 封装、路由骨架、布局组件、金额展示组件

**完成标准**：前端工程启动，金额组件渲染正确

---

### Step 22 · Config Pages｜配置管理页面
> 📁 `docs/prompt/step-22-config-pages.md` ｜ ⏱ 5天 ｜ VC

- [ ] **22.1~22.5** VC：字典 / 科目 / 开户模板 / 记账规则 / 缓冲规则 5 个配置页面

**完成标准**：5 个配置页面交付，接口对接正确

---

### Step 23 · Business Pages｜业务功能页面
> 📁 `docs/prompt/step-23-business-pages.md` ｜ ⏱ 5天 ｜ VC

- [ ] **23.1~23.6** VC：账户管理 / 余额查询 / 资金冻结 / 凭证管理 / 日切管理 / 缓冲监控 6 个业务页面

**完成标准**：6 个业务页面交付，金额千分位 / 小数位验证通过

---

## Phase 10：联调、性能验收与上线
> 周期：Week 18 ｜ 全员

### Step 24 · Integration Testing｜全链路联调
> 📁 `docs/prompt/step-24-integration.md` ｜ ⏱ 3天 ｜ 全员

- [ ] **24.1** TL：编排 10 类端到端测试场景
- [ ] **24.2** BE-A + BE-B：逐场景联调
- [ ] **24.3** VC：前端全页面回归（金额 / 状态标签 / 异常提示）
- [ ] **24.4** 全员：修复后二次回归确认

**完成标准**：10 类场景全通；前端无金额显示错误

---

### Step 25 · Performance & Go Live｜性能验收与上线
> 📁 `docs/prompt/step-25-golive.md` ｜ ⏱ 3天 ｜ TL 主导

- [ ] **25.1** TL：压测（实时入账 P99 ≤ 500ms / 余额查询 P99 ≤ 100ms / 并发冻结无死锁）
- [ ] **25.2** TL：瓶颈优化 + 二次压测确认
- [ ] **25.3** TL：制定上线方案（灰度策略 / 回滚预案 / 监控大盘）
- [ ] **25.4** BE-B：混沌工程演练（MQ 断连 / DB 主从切换，RTO ≤ 30s）
- [ ] **25.5** 全员：上线日值守
- [ ] **25.6** TL：上线后 72 小时观察期，无 P0/P1 宣布上线成功

**完成标准**：P99 全部达标；72 小时无 P0/P1；监控大盘全绿
