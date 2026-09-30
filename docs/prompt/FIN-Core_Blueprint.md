# FIN-Core · 开发进度锚点（Execution Blueprint）

> 本文件为项目唯一执行进度的事实来源，也是 `docs/` 目录下**唯一允许修改**的文件。
> 每完成一个 Step，将对应项标记为 `[X]`，并引导进入下一个 Step。

---

## Phase 1：契约、规范与架构

- [X] **Step 1** · Governance & Constraints｜开发契约与规范
  → 详见 `docs/prompt/step-01-governance.md`

---

## Phase 2：工程基础

- [X] **Step 2** · Project Initialization｜工程从零初始化
  → 详见 `docs/prompt/step-02-project-init.md`

- [X] **Step 3** · Code Generation｜持久层批量生成
  → 详见 `docs/prompt/step-03-codegen.md`
  → Java-A（账户域 7 张表）：`docs/prompt/tasks/step-03-java-a.md`
  → Java-B（凭证域 + 规则域 9 张表）：`docs/prompt/tasks/step-03-java-b.md`
  → Java-C（科目域 + 流水域 + 支撑域 11 张表）：`docs/prompt/tasks/step-03-java-c.md`

- [X] **Step 4** · Middleware Integration｜中间件集成
  → 详见 `docs/prompt/step-04-middleware.md`
  → task-1（RocketMQ 封装）：`docs/prompt/tasks/step-04-task-1-mq.md`
  → task-2（本地消息表）：`docs/prompt/tasks/step-04-task-2-outbox.md`
  → task-3（Redis / 分布式锁 / 字典缓存）：`docs/prompt/tasks/step-04-task-3-redis.md`
  → task-4（Prometheus + 告警）：`docs/prompt/tasks/step-04-task-4-monitor.md`

- [X] **Step 5** · Domain Alignment｜领域模型对齐
  → 详见 `docs/prompt/step-05-alignment.md`
  → Java-A（账户域自定义 Mapper）：`docs/prompt/tasks/step-05-java-a.md`
  → Java-B（凭证域 + 规则域自定义 Mapper）：`docs/prompt/tasks/step-05-java-b.md`
  → Java-C（科目域 + 字典域自定义 Mapper）：`docs/prompt/tasks/step-05-java-c.md`
  → 完成内容：20 个自定义 Mapper 方法 + 5 个 Repository 封装 + 10 个 XML SQL + 3 个单测文件
  → commit: `Step 5: 领域模型对齐 — 补充自定义 Mapper、Repository 与单测`

---

## Phase 3：配置管理模块

- [X] **Step 6** · Dict & Subject API｜字典与科目接口
  → 详见 `docs/prompt/step-06-dict-subject.md`
  → Java-A（字典管理接口 F-1）：`docs/prompt/tasks/step-06-java-a.md`
  → Java-B（科目基础 CRUD 接口）：`docs/prompt/tasks/step-06-java-b.md`
  → Java-C（科目树形查询 + 辅助核算项接口）：`docs/prompt/tasks/step-06-java-c.md`
  → 完成内容：6 个 Request DTO + 3 个 Response DTO + 3 个 Converter + 3 个 ApplicationService + 3 个 Controller
  → 完成内容（Java-A）：字典 CRUD + 二级缓存管理 + 手动刷新缓存 + is_system 拦截
  → 完成内容（Java-B）：科目 CRUD + 编码前缀校验 + 停用联动校验（子科目/模板/规则） + 预留 Step 8 开户检查
  → 完成内容（Java-C）：科目树形查询（懒加载+全量） + 辅助核算项 CRUD + 必填项保护
  → 完成内容（持久层补充）：AccountSubjectMapper.existsByParentCode + AccountTemplateMapper.countBySubjectCode + AccountingRuleDetailMapper.countBySubjectCode + 对应 XML + SubjectRepository 补充分页/校验方法
  → commit: `Step 6: 配置管理模块 — 字典与科目接口 + 树形查询 + 辅助核算项`

- [X] **Step 7** · Template & Rule API｜模板与规则接口
  → 详见 `docs/prompt/step-07-template-rule.md`
  → 完成内容：5 个 Request DTO + 3 个 Response DTO + 3 个 Converter + 3 个 ApplicationService + 3 个 Controller
  → 完成内容（Java-A）：开户模板 CRUD + 关联科目末级校验 + 唯一键校验 + 停用联动校验
  → 完成内容（Java-B）：记账规则 CRUD + 明细借贷双方校验 + SpEL预加载校验 + 辅助核算分摊比例校验 + 停用凭证联动校验 + 全量替换明细
  → 完成内容（Java-C）：缓冲规则 CRUD + 时间区间重叠校验 + 科目/账户非空校验 + 生效/失效时间合法性校验
  → 完成内容（持久层补充）：SubjectRepository 补 template 分页方法 + AccountingRuleRepository 补 rule 分页/删除方法 + AccountingVoucherRepository 补 countByBusinessKey + BufferPostingRuleRepository 新建 + BufferPostingRuleMapper.selectOverlappingRules + AccountMapper.countBySubjectCodeAndOwnerType + 对应 XML SQL + BufferPostingRulePO 补 status 字段
  → commit: `Step 7: 配置管理模块 — 模板与规则接口`

---

## Phase 4：记账核心引擎（⚠️ 最高风险，严格串行）

- [X] **Step 8** · Account Auto-Opening｜自动化开户
  → 详见 `docs/prompt/step-08-account-opening.md`
  → 完成内容（Java-A）：P0-2/P0-3/P0-4 补充 + AccountNoGenerator（Redis 外部每日重置/内部永久递增） + AccountOpeningDomainService
  → 完成内容（Java-B）：openInternalAccount 内部开户 + scanAndOpenInternalAccounts 批量扫描（非事务性逐账户 try-catch） + createSubAccountsInternal 子账户自动创建
  → 完成内容（Java-C）：AccountOpenRequest/InternalAccountOpenRequest/AccountOpenResponse/BatchOpenResultResponse DTO + AccountOpeningAssembler + AccountOpeningApplicationService + AccountOpeningController（4 个接口）
  → 完成内容（持久层补充）：AccountRepository.existsByOwnerIdAndSubjectCode + SubjectRepository.selectAllowOpenAccountLeafSubjects + TransactionConfig TransactionTemplate Bean + AccountTemplateMapper.selectFirstEnabledByBusinessAndCustomer + BalanceDirectionEnum.fromCode + CustomerTypeEnum.fromValue + OwnerTypeEnum.fromCode
  → 完成内容（规范补充）：AccountOpeningDomainService 使用 TransactionTemplate 管理事务 + DistributedLockTemplate 防并发开户 + 双重检查

- [X] **Step 9** · Journaling｜业务流水入库
  → 详见 `docs/prompt/step-09-journaling.md`
  → 完成内容（Java-A）：P0-1/P0-2/P0-3/P0-4 补充 + BusinessRecordMapper/TransactionMapper 方法补充 + BusinessRecordRepository/BusinessDetailRepository/TransactionRepository 新建 + JournalingDomainService（幂等校验 + 会计日期确定 + 流水事务写入） + JournalSubmitResult + TransactionNoGenerator
  → 完成内容（Java-B）：AccountPreCheckDomainService 独立领域服务（记账规则解析 + 预开户检查 + 自动开户集成） + AccountMapper/AccountRepository 补充 selectByOwnerIdAndSubjectCode
  → 完成内容（Java-C）：JournalSubmitRequest/JournalDetailRequest/JournalSubmitResponse DTO + JournalingAssembler + JournalingApplicationService（入口幂等锁 + 用例编排 + FAILED 状态更新） + JournalingController（3 个接口）
  → 完成内容（持久层补充）：AccountMapper.selectByOwnerIdAndSubjectCode + AccountRepository.selectByOwnerIdAndSubjectCode + AccountingRuleMapper.xml status=2 过滤 + TradeTypeEnum.fromCode/isValid + CustomerTypeEnum.isValid
  → Code Review 修复（3 P0 + 7 P1）：
    - P0-1: TransactionNoGenerator 竞态条件 → Lua 脚本原子化初始化（exists+set+incr 一气呵成）
    - P0-2: 幂等无 DB 唯一约束兜底 → catch DuplicateKeyException 返回已有结果
    - P0-3: FAILED 更新在事务外 → 独立 TransactionTemplate 标记 FAILED
    - P1-1: String[] txnNoRef 闭包反模式 → TransactionTemplate.execute() 直接返回 JournalSubmitResult
    - P1-2: 分布式锁固定 30s → 常量 IDEMPOTENT_LOCK_LEASE_SECONDS
    - P1-3: CustomerType 验证失败返回 500 → 预校验 isValid() 返回 400
    - P1-4: 非 AccountException 不触发 FAILED → catch RuntimeException 也标记 FAILED
    - P1-5: updateStatusByTraceNo 忽略返回值 → 返回 affectedRows + 日志告警
    - P1-6: TradeTypeEnum.fromCode 未知码中断事务 → 预校验 isValid() 在进入事务前拦截
    - P1-7: requestNo 用 currentTimeMillis 可能碰撞 → traceNo + 循环序号 + nanoTime
  → commit: `Step 9: 记账核心引擎 — 业务流水入库`

- [X] **Step 10** · Vouchering｜凭证生成引擎
  → 详见 `docs/prompt/step-10-vouchering.md`
  → 完成内容（Java-A）：P0-1/P0-2/P0-3/P0-4/P0-5 补充 + VoucherNoGenerator/EntryIdGenerator（Lua 原子化）+ RuleScriptExecutor（SpEL 执行器）+ VoucheringDomainService（规则匹配 + SpEL计算 + 分录生成 + 借贷平衡校验 + 凭证持久化）
  → 完成内容（Java-B）：BufferPostingRuleMapper.selectMatchingRules + XML（NULL/空串判断）+ AuxiliaryItemData/BufferPostingDetailData DTO + BufferPostingDomainService（辅助核算按比例/固定金额分摊 + 缓冲规则匹配 + 持久化 + sharding 计算）
  → 完成内容（Java-C）：VoucherGenerateRequest/VoucherGenerateResponse/VoucherEntryResponse DTO + VoucheringAssembler + VoucheringApplicationService（统一事务边界 + txnNo回填）+ VoucheringController（3 个接口）
  → 完成内容（持久层补充）：AccountingVoucherMapper.updateTxnNoByVoucherNo + AccountingVoucherEntryMapper.updateStatusByVoucherNo + AccountingVoucherRepository.updateTxnNoByVoucherNo + BusinessRecordMapper.selectByTraceNoOnly + BusinessRecordRepository.selectByTraceNo(traceNo) + ResultCode 补充 7 个新错误码
  → Key Fixes: P0-1/P0-2 Lua 原子化编号生成 + P1-1 统一事务边界 + P1-2 resolveAccountNo fallback + P1-3 auxMap 映射 + P1-5 voucherType 从规则获取 + P1-6 缓冲规则 NULL/空串处理

- [X] **Step 11** · Transaction Management｜事务管理
  → 详见 `docs/prompt/step-11-transaction.md`
  → 完成内容（Java-A）：P0-1~P0-7 补充（已完成确认）+ AccountBalanceCalculator + PostingDomainService（实时过账 + 账户状态检查 + 余额计算 + 明细快照 + 分录状态更新）+ AccountDetailRepository + SubAccountDetailRepository
  → 完成内容（Java-B）：AsyncPostingDomainService（本地消息写入 + MQ消费端过账 + 幂等检查 + 状态联动 + executeRollbackOnAsyncFailure）+ RollbackDomainService（markTransactionFailed + executeRollbackForAsyncFailure 反向凭证回滚 + isRetryable）+ PostingMessagePayload + PostingMessageConsumer（含 TransactionTemplate 独立事务 + 指数退避重试）
  → 完成内容（Java-C）：PostingApplicationService（过账编排 + 分布式锁 + 分录分流 + 状态联动 + 重试机制）+ PostingController（3 个接口）+ PostingExecuteRequest/Response/EntryResponse DTO + PostingAssembler
  → 完成内容（持久层补充）：ResultCode 补充 POSTING_STATUS_INVALID/POSTING_FAILED 错误码
  → Key Fixes: P0-1~P0-7 确认已完成 + ACCOUNT_STATUS_INVALID→ILLEGAL 修复 + subOldBalance 作用域修复 + preBalance 反向计算修复 + MessageReceiptPO 类型修复 + TransactionTemplate 消费端事务控制 + markTransactionFailed txnNo/traceNo 解析修复

- [X] **Step 12** · Posting Engine｜过账引擎
  → 详见 `docs/prompt/step-12-posting.md`
  → 完成内容（Java-A）：P0-1~P0-8 补充（Mapper/Repository/PO/DDL）+ PostingEngineDomainService（批量过账）+ BatchPostingJobHandler（XXL-JOB）
  → 完成内容（Java-B）：PostingMonitorDomainService（进度监控 + 异常治理 + 统计报表 + 僵尸检测 + 重试/跳过）
  → 完成内容（Java-C）：PostingEngineApplicationService（编排层）+ PostingEngineController（7 个接口）+ 7 个 DTO + PostingEngineAssembler
  → 完成内容（持久层补充）：AccountingVoucherMapper.selectByStatusAndDateRange/countByStatusGroup + XML + TransactionMapper.countByStatusAndDateRange/selectDurationStats + XML + LocalMessageMapper.selectByStatusAndRetryLimit/updateRetryInfo + XML + LocalMessageService.selectPendingMessages/updateRetryInfo
  → Key Features: 批量过账（单笔失败不中断）+ 本地消息扫描（已有 LocalMessageRetryJob 覆盖）+ 进度监控（凭证/事务/批次维度）+ 异常治理（重试限次/跳过标记）+ 统计报表（多维度分组）+ 僵尸凭证检测
  → commit: `Step 12: 过账引擎 — 批量过账 + 进度监控 + 异常治理 + 统计报表`

---

## Phase 5：账户与冻结模块

- [X] **Step 13** · Account Status Control｜账户状态管理
  → 详见 `docs/prompt/step-13-account-status.md`
  → 完成内容（Java-A）：P0-1/P0-2/P0-3 补充 + AccountStatusChangeDomainService（冻结/解冻/注销 + 状态机校验 + 分布式锁 + 双重检查 + 乐观锁）
  → 完成内容（Java-B）：changeRiskStatus 风控状态变更 + SLF4J MDC 操作日志记录
  → 完成内容（Java-C）：4 个 Request DTO + 2 个 Response DTO + AccountStatusAssembler + AccountStatusApplicationService + AccountStatusController（5 个接口）
  → 完成内容（持久层补充）：AccountMapper.updateStatusByAccountNo/updateRiskStatusByAccountNo + XML + AccountRepository.updateStatus/updateRiskStatus
  → 完成内容（错误码）：ResultCode 补充 ACCOUNT_STATUS_TRANSITION_INVALID(3014) + ACCOUNT_BALANCE_NOT_ZERO(3015)
  → Key Features: 状态机校验（NORMAL↔FROZEN→CANCELLED）+ 同状态幂等 + 注销余额校验（主账户+子账户）+ inactiveDate 默认值处理 + 风控独立变更

- [X] **Step 14** · Freeze & Unfreeze｜资金冻结与解冻
  → 详见 `docs/prompt/step-14-freeze.md`
  → 完成内容（Java-A）：P0-1~P0-6 补充（SubAccountMapper updateBalance/selectByAccountNoAndType + XML, SubAccountRepository, AccountFreezeDetailMapper insert/selectByVoucherNo/updateStatus + XML, FreezeDetailRepository）+ FreezeIdGenerator（Lua + Redis INCR）+ FreezeDomainService（资金冻结/解冻/扣款 + 子账户余额转移 + 明细记录 + 双重检查 + 乐观锁）+ AutoUnfreezeJobHandler（XXL-JOB 每 5 分钟扫描过期记录）
  → 完成内容（Java-B）：deductFromFreeze 补充主账户余额减少（AccountMapper.updateBalance + XML + AccountRepository）+ 双重检查 + 主账户明细写入 t_account_detail
  → 完成内容（Java-C）：3 个 Request DTO + 1 个 Response DTO + FreezeAssembler + FreezeApplicationService（5 个用例编排）+ FreezeController（5 个接口：/fund /unfreeze /deduct /{freezeId} /list）
  → 完成内容（持久层补充）：SubAccountMapper.updateBalance/selectByAccountNoAndType + XML + SubAccountRepository.updateBalance（含乐观锁拦截）+ AccountFreezeDetailMapper.selectByVoucherNo/updateStatus + XML + FreezeDetailRepository + selectByCondition + AccountMapper.updateBalance + XML + AccountRepository.updateBalance + insertAccountDetail
  → 完成内容（错误码）：ResultCode 补充 3016~3020（FREEZE_AMOUNT_INVALID/FREEZE_RECORD_NOT_FOUND/FREEZE_STATUS_INVALID/FREEZE_AMOUNT_EXCEEDED/ACCOUNT_FROZEN_CANNOT_FREEZE）
  → Key Features: 资金冻结（可用→冻结余额转移）+ 资金解冻（冻结→可用）+ 冻结扣款（冻结余额减少 + 主账户余额减少 + 主/子账户明细完整记录）+ 超时自动解冻 Job + 分布式锁（account:fund:{accountNo}）+ 双重检查 + 乐观锁 + 严禁负数运算

- [X] **Step 15** · Balance Query API｜余额查询接口
  → 详见 `docs/prompt/step-15-balance-query.md`
  → 完成内容（Java-A）：P0-1~P0-6 补充（AccountMapper/SubAccountMapper/AccountDetailMapper/BufferPostingDetailMapper/AccountFreezeDetailMapper 查询方法 + AccountRepository/SubAccountRepository/AccountDetailRepository/FreezeDetailRepository/BufferPostingDetailRepository 封装）+ BalanceQueryDomainService（聚合余额 + 明细分页 + 冻结分页）+ DDL（8-balance-query-alter.sql）
  → 完成内容（Java-C）：3 个 Request DTO + 3 个 Response DTO + BalanceQueryAssembler + BalanceQueryApplicationService + BalanceQueryController（3 个接口）
  → 完成内容（枚举补充）：AccountStatusEnum/RiskStatusEnum 补充 fromCode 方法 + ResultCode 补充 ACCOUNT_NO_REQUIRED(3021)
  → Key Features: 聚合余额查询（主账户 + 可用子账户 + 冻结子账户 + 缓冲预估，3 次 SQL + 应用层组装）+ 账户明细分页查询（日期/类型/借贷方向过滤）+ 冻结记录分页查询（按 business_code 关联 + MyBatis-Plus 分页）

---

## Phase 6：缓冲记账、日切与红冲

- [X] **Step 16** · Buffer Posting｜缓冲记账
  → 详见 `docs/prompt/step-16-buffer-posting.md`
  → 完成内容（基础设施）：BufferPostingDetailMapper 补充 3 个 default 方法（selectPendingByCondition/selectLastDetailByAccountAndDate/selectByShardingRange）+ 1 个 XML 聚合（sumByAccountNo）+ BufferPostingDetailRepository 扩展（查询 + 状态更新）
  → 完成内容（Java-B）：BufferPostingEngineDomainService（executeSinglePosting/executeBatchPosting/executeShardedPosting/processSingleDetail/processAccountSummary）+ RunningBalanceValidator（validateRunningBalance）
  → 完成内容（错误码）：ResultCode 补充 2023~2025（BUFFER_POSTING_BALANCE_MISMATCH/BUFFER_POSTING_LOCK_UPGRADE_FAILED/BUFFER_POSTING_RETRY_EXHAUSTED）
  → 完成内容（Java-C）：BufferPostingAsyncJobHandler（每1分钟）/ BufferPostingBatchJobHandler（每30分钟，支持分片）/ BufferPostingEodJobHandler（23:50 + Running Balance 校验）
  → 完成内容（Java-E）：4 个 Response DTO + 1 个 Request DTO + BufferPostingAssembler + BufferPostingApplicationService + BufferPostingController（3 个接口：POST /execute, GET /pending-stats, GET /monitor）
  → Key Features: 三种缓冲模式（逐条/日间批量/日终批量）+ 余额方向计算（balance_direction + debit_credit → changeDirection）+ 锁升级（乐观 3 次 → 悲观 FOR UPDATE → 告警）+ 子账户明细快照（preBalance/postBalance）+ 凭证状态联动（全部POSTED → 3, 否则 → 2）+ 分布式锁（account:buffer:{accountNo}）+ TransactionTemplate 事务边界 + 分片扫描（按 sharding 字段）

- [X] **Step 17** · EOD & Trial Balance｜日切与试算平衡（主体）
  → 详见 `docs/prompt/step-17-eod.md`
  → 完成内容（P0-1~P0-8）：4 个 Mapper 方法补充 + 4 个 XML SQL + DDL 确认
  → 完成内容（Java-A）：EodCheckDomainService（5项前置检查）+ TrialBalanceDomainService（科目汇总+差额阈值0.000001）
  → 完成内容（Java-B）：EodDomainService（日余额计算+批量Upsert+日/月快照）+ PeriodEndTransferDomainService（通配符解析+结转凭证+幂等控制）
  → 完成内容（Java-C）：EodApplicationService（编排层）+ EodController（3个接口）+ EodJobHandler（XXL-JOB 23:55）
  → 完成内容（DTO）：4 个 DTO + 1 个 Assembler
  → 完成内容（错误码）：ResultCode 新增 2026~2029
  → Key Features: 5项前置检查（缓冲/事务/凭证/流水/冻结）+ 日余额计算（期初+借方-贷方=期末，按余额方向）+ 试算平衡（0.000001阈值）+ 期末结转（通配符匹配+凭证生成+单条失败不中断）+ 日/月快照生成 + XXL-JOB 总调度 + 幂等控制
  → commit: `d1eeb8f` through `6bd1ccb`（共14 commits，含文档/设计/实现计划）

- [x] **Step 17S** · EOD Supplement｜日切补充（阶段1瞬间切日 + 阶段5归档 + 总分/余额核对 + 状态追踪）
  → 详见 `docs/prompt/step-17-supplement-eod-gaps.md`
  → 补全原始设计 `eod_five_phases.mmd` 中遗漏的阶段 1（全局会计日期切换）和阶段 5（归档）
  → 新增：`t_eod_status` 表 DDL + Redis+Caffeine 二级缓存 + 5 个 DomainService + 2 个新接口
  → 完成内容（DDL）：`t_eod_status` 表（`docs/sql/6-infra.sql`）
  → 完成内容（Mapper/XML）：`EodStatusMapper` + XML（状态 CRUD + updateSwitchDateTime）+ `AccountMapper.sumBalancesBySubject` + `AccountMapper.selectBalancesByAccountNos` + `AccountBalanceMapper.sumBalancesBySubject` + `AccountDetailMapper.selectLastPostBalance`
  → 完成内容（Repository）：`EodStatusRepository`
  → 完成内容（缓存）：`AccountingDateCache`（Caffeine L1 + Redis L2 + Redis Pub/Sub 多实例同步）
  → 完成内容（Domain）：`AccountingDateSwitchDomainService`（瞬间切日 + 幂等控制）+ `EodArchiveDomainService`（归档）+ `EodStatusDomainService`（状态追踪）+ `TrialBalanceDomainService` 增强（总分核对 + 余额核对）
  → 完成内容（接口）：`GET /accounting/eod/status` + `POST /accounting/eod/switch-date`
  → 完成内容（DTO）：`DateSwitchRequest` + `DateSwitchResponse` + `EodStatusResponse`
  → 完成内容（错误码）：`ResultCode` 新增 2030~2034
  → 完成内容（Job 编排）：`EodJobHandler` 8 步流程（切日→清理→余额→快照→试算→总分核对→余额核对→结转→归档）
  → 完成内容（集成）：`JournalingDomainService.determineAccountingDate` 改用缓存；`EodApplicationService` 编排全流程 + 状态追踪
  → CR 修复：12 项（P0×3: 总分核对语义/switchDateTime 持久化/switchedAt 字段 + P1×5: N+1 查询/冗余变量/异常捕获/日期注释/缓存注释 + P2×4: 魔法数字/Pub-Sync/日志格式/注释）
  → commit: `ab7bcac`

- [X] **Step 18** · Reversal & Red Offset｜冲账与红冲
  → 详见 `docs/prompt/step-18-reversal.md`
  → 完成内容（持久层）：`AccountingVoucherMapper` 补充 `updateStatusAndBookkeeper` + `AccountingVoucherEntryMapper` 补充 `batchInsert` + `AccountingVoucherAuxiliaryMapper` 补充 `batchInsert` + XML SQL + `AccountingVoucherRepository` 封装
  → 完成内容（Java-A）：`ReversalDomainService`（分布式锁 + 双重检查 + 前置校验 + 凭证号生成 + 分录反转 + 辅助项复制 + 过账 + 状态联动）+ `ResultCode` 补充 2035~2039
  → 完成内容（Java-B）：`ReversalJobHandler`（XXL-JOB 批量红冲，单条失败不中断）
  → 完成内容（Java-C）：3 个 DTO（ReversalRequest/Response/RecordResponse）+ `ReversalApplicationService`（编排层）+ `ReversalController`（3 个接口：/execute, /records, /check）+ `ReversalAssembler`（转换器）
  → 修复已有编译错误：`EodStatusResponse` 循环依赖解除 + `AccountBalanceMapper` 缺省 import 修复 + `AccountingDateSwitchDomainService` 缺省 import 修复 + `EodController` ResultCode 调用修复
  → Key Features: 红冲不可删除原凭证 + 借贷方向对调金额保持正数 + 分布式锁防并发红冲 + 红冲凭证复用标准过账链路 + 状态联动（原凭证 REVERSED + 红冲凭证 POSTED）

---

## Phase 7：MCP 接入

- [ ] **Step 19** · MCP Server Integration｜MCP 接入
  → 详见 `docs/prompt/step-19-mcp.md`

---

## Phase 8：存量数据迁移

- [ ] **Step 20** · Data Migration｜存量数据迁移
  → 详见 `docs/prompt/step-20-migration.md`

---

## Phase 9：前端集中交付

- [X] **Step 21** · Frontend Infrastructure｜前端工程初始化
  → 详见 `docs/prompt/step-21-frontend-init.md`
  → 工程创建：`accounting-ui/` 独立前端工程（Vue 3 + Vite 6 + TypeScript + Element Plus + Pinia + Axios）
  → 端口与代理：可配置端口（默认 3000），反向代理 `/accounting` 转发至后端
  → 完整公共组件库：
    - AmountDisplay（等宽千分位、保留 2 位小数、负数标红、右对齐）
    - AmountInput（金融数值输入限制、防负数、失焦自动补齐千分位与小数位）
    - StatusTag（状态机颜色与字典标签映射）
    - DictSelect（字典远程加载/本地枚举下拉）
    - BaseCheckboxGroup（复选框组与全选/半选联动）
    - BaseDialog（通用模态框、全屏、操作底栏）
    - ConfirmDialog / useConfirm（二次确认弹窗与高危危险操作警示）
    - ActionButton（集成二次确认与防重复提交 loading）
    - BaseTable（斑马纹、边框、空状态、金额列对齐、分页器集成）
    - BasePagination（标准分页参数 pageNo/pageSize/total 联动）
    - toast（统一消息与通知防抖封装）
    - request（Axios 统一实例、租户/追踪头、错误码弹窗拦截与 Result<T> 解包）
  → 布局与路由：完整后台框架（Header 会计日期展示、Sidebar 折叠菜单、Breadcrumb、路由树骨架）
  → 组件展台：`/component-demo` 提供全套公共组件交互演示
  → 验证通过：`type-check` 0 错误，`vite build` 产物打包正常，开发服务器启动耗时 600ms

- [X] **Step 22** · Config Pages｜配置管理页面
  → 详见 `docs/prompt/step-22-config-pages.md`
  → 完成内容（配置页面）：
    - 数据字典管理（`accounting-ui/src/views/config/dict/index.vue`）：字典项分页/类型查询、新增/编辑/删除、手动刷新二级缓存、系统内置字典保护
    - 会计科目管理（`accounting-ui/src/views/config/subject/index.vue`）：树形表格懒加载与跨层级检索、科目新增/编辑/停用校验、辅助核算项抽屉动态加载字典与必填维护
    - 开户模板管理（`accounting-ui/src/views/config/template/index.vue`、`accounting-ui/src/api/template.ts`）：
      - 支持单模板配置多个会计科目账户，排版优化为规整栅格与统一行高表格，置顶规则变量参考提示条
      - 业务线编码（`business_code`）、账户类型（`account_type`）、币种（`currency`）全部接入字典表动态加载（集成 Flyway V9 字典迁移脚本），并支持过滤搜索与自定义录入
      - 移除多余引导文案，规范错误与校验提示
      - 后端提供模板组批量保存能力（`POST /accounting/config/template/group`）与平铺聚合呈现
      - 业务线/客户类型/状态多维筛选、模板组状态启用/停用联动校验与详情查看
  → 完成内容（后端协同与基础设施）：
    - 全局动态条件查询范式重构：基于 MyBatis-Plus 原生 `condition` 条件机制与 `@EnumValue` 自动映射特性，统一重构全部配置类 ApplicationService 分页与查询逻辑（`DictApplicationService`、`SubjectApplicationService`、`SubjectTreeApplicationService`、`TemplateApplicationService`、`RuleApplicationService`、`BufferRuleApplicationService`），全面消除冗余 if-else 判空
    - 统一枚举安全转换方法：各状态与类别枚举统一补充 `fromCode(Integer code)` 安全转义
    - `SubjectTreeApplicationService` / `SubjectApplicationService`：科目树懒加载与全层级检索、父子关系级联维护（父级自动取消末级/记账/开户）、上级科目编码与名称自动装配
    - `DictionaryMapper` / `AccountSubjectAuxiliaryMapper`：逻辑删除更新原生 SQL 兼容（解决 `@TableLogic` 忽略时间戳问题）
    - 初始化辅助核算字典项（`CUSTOMER` 客户、`SUPPLIER` 供应商、`DEPARTMENT` 部门、`PROJECT` 项目、`EMPLOYEE` 员工）
    - 开户模板更新契约扩展：`TemplateUpdateRequest` 补充 `templateName` 支持模板名称修改
    - MyBatis-Plus 租户插件顺序调整：将 `TenantLineInnerInterceptor` 前置于 `PaginationInnerInterceptor` 之前，解决 COUNT 缺少租户条件导致分页统计不一致缺陷
    - 逻辑删除条件瘦身：全面移除 ApplicationService 与 Mapper 中冗余手写的 `wrapper.eq(isDelete, 0)`
    - 开户模板弹窗栅格优化：基础信息重构为双列规范布局，彻底消除自动开户与状态控件折行拥挤
    - 账号与户名规则引擎深度升级：
      - 账号生成规则（`acctNoRule`）：剔除 `bizCode`、`ownerId`，全面支持 `{accountType}`（账户类型）、`{currency}`（币种）、`{balanceDirection}` / `{direction}`（借贷方向）、`{ownerType}` / `{customerType}`（所有者类型）、`{subjectCode}`（科目）、`{yyyyMMdd}` / `{yyyyMM}` / `{yyyy}` 及序号变量；
      - 账户名称生成规则（`acctNameRule`）：剔除 `bizCode`，全面支持 `{accountTypeName}`（账户类型名称）、`{currencyName}`（币种名称）、`{directionName}`（节点/借贷方向名称）、`{ownerTypeName}`（所有者类型名称）、`{subjectName}`、`{ownerName}`、`{ownerId}` 等变量；
      - 动态长度语法支持：所有变量全面支持指定长度截取或补零格式化（如 `{seq1}`、`{seq2}`、`{seq3}`、`{subjectCode4}`、`{accountType3}`、`{ownerName2}`），超过 32 位自动安全截断；
      - 前端规则变量参考栏优化：开户模板弹窗内规则变量参考条重构为两行独立展示（第一行账号规则可用，第二行户名规则可用），并更新 placeholder 与默认规则为新变量体系；
    - 记账规则管理（`accounting-ui/src/views/config/rule/index.vue`、`accounting-ui/src/api/rule.ts`）：
      - 主表格展开行（Expand Row）：支持预览该规则下完整的借贷分录明细、科目、账户作用域、单边更新标识、SpEL 计算脚本与辅助核算项汇总
      - 操作列全面改造成紧凑型 `MoreFilled` “...” 下拉菜单（列宽 70px），包含详情档案、编辑规则、启用规则、停用规则（含关联凭证防呆校验）、复制新建
      - 规则新建/编辑弹窗：2 列规整栅格录入规则核心属性；动态借贷分录明细表格，支持添加分录行与“预设一借一贷”快捷功能
      - 借贷平衡实时校验指示灯：动态计算借方行数与贷方行数，实时标红警示与借贷平衡提示，防范单边分录
      - 辅助核算配置子弹窗：支持按固定金额或按比例分摊，实时计算并校验分摊比例和必须精确等于 1.000000
      - 后端轻量级启用接口增强（`POST /accounting/config/rule/{ruleId}/enable`），采用 `TransactionTemplate` 保证事务原子性与数据一致性
      - 规则字典与样本迁移脚本（Flyway V11：`V11__rule_dicts.sql`）：初始化凭证类型、支付渠道、交易编码、款项类型、辅助核算类型等字典
    - 缓冲入账规则配置（`accounting-ui/src/views/config/buffer-rule/index.vue`、`accounting-ui/src/api/buffer-rule.ts`）：
      - 检索与表格：支持业务线、缓冲模式、状态多维检索；完整展示三种缓冲模式（逐条/日间批量/日终批量汇总）、作用对象（科目编码+名称，或账号+复制按钮）、借贷方向与生效时间区间
      - 操作列：统一使用 `MoreFilled` “...” 下拉菜单（列宽 70px），集成详情档案、编辑规则、启用规则、停用规则、复制新建
      - 新建/编辑规则弹窗：双列栅格排版，集成时间范围选择器，强校验会计科目与账户编号至少填其一，时间区间合法性校验与防时间冲突拦截
      - 后端独立启用接口与事务控制（`POST /accounting/config/buffer-rule/{ruleId}/enable`），并补充 `BufferRuleApplicationServiceTest` 单元测试通过
      - 数据库迁移脚本（Flyway V12：`V12__add_status_to_buffer_posting_rule.sql`）：幂等补齐 `status` 字段并初始化演示缓冲规则
      - 路由切换：`config/buffer-rule` 由占位符成功切换绑定至正式页面
  → 配置管理页面（Step 22）四大模块（字典、科目树、开户模板、记账规则、缓冲入账规则）全部竣工。

- [ ] **Step 23** · Business Pages｜业务功能页面
  → 详见 `docs/prompt/step-23-business-pages.md`
  → 完成内容（Step 23.1 账户管理模块 — 客户分户账户 + 内部分户账户）：
    - 后端与契约：
      - 账户综合分页检索契约设计（`AccountPageQueryRequest`、`AccountPageResponse`）；
      - 高性能批量装配（`SubAccountRepository.selectByAccountNos`、`AccountRepository.selectPage`）：单次批量预取子账户余额，彻底规避 N+1 查询，自动整合返回账面总余额、可用子账户余额及冻结子账户余额；
      - 会计科目与账户类型名称自动映射；
      - 接口服务暴露：`GET /accounting/account/page` 控制器（`AccountQueryController`）；
      - 自动化测试：`AccountQueryApplicationServiceTest` 单元测试覆盖并通过。
    - 前端交互与页面（`accounting-ui/src/views/business/account/index.vue`、`src/api/account.ts`）：
      - 客户分户账户（`CUSTOMER`）与内部分户账户（`INTERNAL`）双视图 Tab 自由切换；
      - 多维组合检索表单（账号、户名、客户号、客户类型、会计科目、账户类型、状态、风控状态、开户日期区间）；
      - 数据表格：主账户总余额、可用余额、冻结余额标准金融千分位与等宽字体格式化呈现，账号一键复制，状态与风控颜色徽标；
      - 账户全景档案抽屉（`el-drawer`）：账户元数据、双子账户资产 KPI 看板（总余额/可用/冻结/缓冲预估）、交易流水明细分页子标签页、资金冻结记录分页子标签页；
      - 客户开户弹窗：支持根据业务线+客户类型一键批量开立模板下全科目账户（推荐），或指定单一科目开户，自动生成幂等请求号；
      - 内部账户开户弹窗与全科目一键扫描补齐内部账户（`batchScanInternalAccounts`）；
      - 账户状态管控弹窗：冻结、解冻、注销（严格校验零余额与无在途交易）、风控状态等级（止入/止出/双向）调整；
      - 路由更新：`/business/account` 绑定至正式账户管理页面；
      - 验证通过：TypeScript 0 错误，`vite build` 打包 100% 成功。
  → 完成内容（Step 23.2 余额与明细查询模块 — `business/balance`）：
    - 后端服务与测试：
      - 验证 `BalanceQueryController` 三大查询接口（聚合余额、明细分页、冻结记录分页）；
      - 补充 `BalanceQueryApplicationServiceTest` 单元测试（聚合余额组装、明细分页组装、冻结记录组装），单测覆盖并通过。
    - 前端交互与页面（`accounting-ui/src/views/business/balance/index.vue`、`src/api/balance.ts`）：
      - 顶部智能搜索卡片：支持账户编号输入与联想模糊建议、快速填入演示账号、支持从 URL `route.query.accountNo` 自动触发查询；
      - 4 维关键资产看板（总余额、可用子账户余额、冻结子账户余额、缓冲待入账预估），结合账户状态与风控状态标签、数据更新时效展示；
      - 交易变动明细多维检索：支持会计日期区间、交易类别（正常/调账/红/蓝）、借贷方向组合筛选，表格清晰展示交易前余额、交易金额（借方主色蓝/贷方琥珀橙）、交易后余额、凭证编号及摘要；
      - 资金冻结记录检索：支持按冻结状态（冻结中/已解冻）筛选，清晰展示冻结编号、冻结金额、到期时间、交易时间与创建时间；
      - 交易流水深层审计档案抽屉（`el-drawer`）：卡片式金额变动对账（前余额 ± 变动金额 = 后余额）、凭证分录元数据（凭证号、分录流水号、事务编号、系统跟踪号、业务线编码、交易编码）、原始 JSON 报文一键复制；
      - 操作列统一采用 `MoreFilled` “...” 紧凑下拉菜单（列宽 70px）；
      - 跨页面联动：账户管理模块（`business/account`）操作菜单中支持一键直达“余额与明细”并自动加载对应账户；
      - 路由切换：`/business/balance` 成功切换绑定至正式查询页面；
      - 验证通过：TypeScript 0 错误，`vite build` 打包 100% 成功，后端单测 100% 通过。
  → 完成内容（Step 23.3 资金冻结与扣款模块 — `business/freeze`）：
    - 后端服务与契约：
      - 新增综合分页查询契约 `FreezePageQueryRequest`（继承 `PageRequest`，支持按账号、单号、状态、时间范围多维过滤）；
      - 增强 `FundFreezeRequest` 的 `expireTime` 注解支持 `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")`；
      - `FreezeDetailRepository` 补充通用分页查询能力 `selectPage`；
      - `FreezeApplicationService` 扩展 `queryFreezePage` 分页编排与 DTO 装配；
      - `FreezeController` 暴露 `GET /accounting/account/freeze/page` 分页接口，并优化 `/list` 接口允许账户编号选填；
      - 自动化单测覆盖：补充 `FreezeApplicationServiceTest`，测试用例全部通过（0 失败 0 错误）。
    - 前端交互与页面（`accounting-ui/src/views/business/freeze/index.vue`、`src/api/freeze.ts`）：
      - 检索卡片：支持账户编号联想建议、冻结单号精确检索、状态筛选、创建时间范围过滤；
      - 数据表格：清晰展示冻结编号（一键复制）、关联账户（点击直达查余额）、冻结金额（金融千分位等宽呈现）、状态 Tag、失效时间（过期智能提示）、创建时间与原因摘要；
      - 核心操作列：统一采用 `MoreFilled` “...” 紧凑下拉菜单（列宽 70px）；
      - 资金冻结弹窗：支持账户输入联想、即时获取展示账户户名与可用余额看板、金额防超可用校验、失效时间选择与原因录入；
      - 资金解冻弹窗：回显冻结单号与当前金额、指定解冻金额（支持一键“全额解冻”快捷回填）、解冻原因；
      - 冻结扣款弹窗：醒目红字风险警示横幅（告知不可逆并自动生成正式财务记账凭证）、回显单号与账户、指定扣款金额（支持一键全部扣除）、扣款原因/司法执行文号；
      - 全景档案抽屉（`el-drawer`）：查看单笔冻结核心属性、摘要与快捷解冻/扣款/查余额动作；
      - 路由更新：`/business/freeze` 成功绑定至正式业务页面；
      - 验证通过：TypeScript 0 错误，`vite build` 打包 100% 成功，后端单测 100% 通过。
  → 完成内容（Step 23.4 记账凭证管理模块 — `business/voucher`）：
    - 后端服务与契约：
      - 新增综合分页查询契约 `VoucherPageQueryRequest`（支持凭证号模糊、跟踪号、事务号、状态、类型、业务线、会计日期区间、制单时间多维过滤）；
      - 新增分页列表响应契约 `VoucherPageItemResponse`（含借贷分录数、借贷合计、平衡校验标识及分录预取列表）；
      - 新增全景档案响应契约 `VoucherFullDetailResponse`、辅助核算项契约 `VoucherAuxiliaryResponse`、附件契约 `VoucherAttachmentResponse`；
      - 仓储层防 N+1 增强：`AccountingVoucherRepository` 补充通用分页 `selectPage`、多凭证批量按凭证号预取分录 `selectEntriesByVoucherNos`、按凭证号查询全部辅助核算项 `selectAuxiliaryByVoucherNo`；
      - `SubjectRepository` 扩展 `selectSubjectNameMap` 批量装配会计科目名称字典；
      - `VoucheringAssembler` 扩展分页项转换 `toPageItem`、分录行转换 `toEntryResponse`、辅助核算转换 `toAuxiliaryResponse`、全景档案组装 `toFullDetail`；
      - `VoucheringApplicationService` 扩展 `queryVoucherPage` 多维分页检索与 `getVoucherDetail` 全景档案装配；
      - `VoucheringController` 路由防冲突治理与接口暴露：`GET /accounting/voucher/page`（前置排布）与 `GET /accounting/voucher/detail/{voucherNo}`，通配符增加正则防御；
      - 自动化单测覆盖：新建 `VoucheringApplicationServiceTest`（5 个用例全部通过，覆盖空结果、多凭证批量分录预取、借贷平衡校验、全景档案、红冲可行性检查）。
    - 前端交互与页面（`accounting-ui/src/views/business/voucher/index.vue`、`src/api/voucher.ts`）：
      - 4 维核心凭证看板（凭证总数、待处理凭证、已过账凭证、红冲与异常凭证）；
      - 多维检索表单（凭证号、跟踪号、事务号、状态、凭证类型、业务线、会计日期范围）；
      - 主表格行展开（**Expand Row**）即时内嵌借贷分录明细：序号、借贷（借：蓝徽标；贷：橙徽标）、科目名称与编码、账户编号（一键复制与直达查余额）、金额（等宽千分位）、单边/缓冲标识、分录摘要；
      - 展开行底部借贷平衡合计栏：动态统计借方笔数/金额与贷方笔数/金额，借贷平衡指示灯（🟢 借贷严格平衡）；
      - 主表格列：凭证号（点击打开全景抽屉）、关联流水（跟踪号/事务号带复制）、凭证类型、交易类别、凭证金额、会计日期、借贷校验、状态 Tag、制单时间；
      - 核心操作列：统一采用 `MoreFilled` “...” 紧凑下拉菜单（列宽 70px），集成详情档案、立即过账、凭证红冲、查看原凭证、复制凭证号；
      - 凭证全景档案抽屉（`el-drawer`）：3 大 Tab（凭证分录与基本信息、辅助核算分摊项、审计链路与原始 JSON 报文）；
      - 凭证红冲弹窗（`ReversalDialog`）：红字强警示横幅（财务不可逆提醒）、原凭证回显、录入红冲记账人与红冲原因摘要，联动调用 `executeReversal`；
      - 手动执行过账确认弹窗：对未过账或失败凭证调用 `executePosting`；
      - 跨模块联动支持：监听 `route.query.voucherNo` 和 `route.query.traceNo` 自动填入并直达定位；
  → 完成内容（Step 23.4.1 手工凭证录入与审核独立模块 — `business/manual-voucher`）：
    - 架构物理隔离与核心设计：
      - 彻底解耦法定凭证表：新建独立申请表 `t_manual_voucher_apply`、申请分录表 `t_manual_voucher_apply_entry` 及流转审计日志表 `t_manual_voucher_audit_log`；
      - 杜绝脏数据污染核心凭证库：制单草稿、待初审、初审驳回、待复核、复核驳回期间全部数据沉淀在申请审批表内；
      - 严格四阶段内控生命周期：创建制单（Maker） -> 初审把关（Auditor） -> 终审复核（Checker/Reviewer） -> 确认记账（Bookkeeper）；
      - 记账正式转换原子性保证：复核通过后由记账员点击【确认记账】，在 TransactionTemplate 事务内正式生成凭证（`VOU...`）与分录（`ENT...`）落库 `t_accounting_voucher*`，回填 `reviewer_name` 与 `bookkeeper_name`，联动过账引擎实时过账并更新账户余额，最后回填申请单为已记账状态；
      - 全生命周期实名可追溯：详细记录每一次流转动作、操作人姓名、操作角色、流转前/后状态、审批意见或驳回原因、操作时间戳。
    - 后端服务与契约（`accounting-api` + `accounting-core`）：
      - 契约定义：`ManualVoucherApplyStatusEnum`、`ManualVoucherApplySaveRequest`、`ManualVoucherApplyEntryRequest`、`ManualVoucherApplyAuditRequest`、`ManualVoucherApplyPostRequest`、`ManualVoucherApplyPageRequest`、`ManualVoucherApplyPageItemResponse`、`ManualVoucherAuditLogResponse`、`ManualVoucherApplyDetailResponse`；
      - 仓储与实体：`ManualVoucherApplyPO`、`ManualVoucherApplyEntryPO`、`ManualVoucherAuditLogPO`、`ManualVoucherApplyMapper`、`ManualVoucherApplyEntryMapper`、`ManualVoucherAuditLogMapper`、`ManualVoucherApplyRepository`（内置自动建表自愈机制，防止缺少表导致的错误）；
      - 业务编排服务：`ManualVoucherApplicationService` 实现制单草稿/提审、初审通过/驳回、复核通过/驳回、确认记账、修改重提、作废、分页多维检索、全景详情与大写金额转换、看板状态统计；
      - RESTful 接口：`ManualVoucherController` 暴露完整端点群；
      - 自动化单测覆盖：新建 `ManualVoucherApplicationServiceTest`（6 个测试用例 100% 通过，覆盖草稿保存、借贷平衡、初审通过/驳回、复核、记账落库转入与凭证号回填、作废操作）。
    - 前端交互与页面（`accounting-ui/src/views/business/manual-voucher/index.vue`、`src/api/manualVoucher.ts`）：
      - 侧边栏独立挂载导航：`/business/manual-voucher`（`手工凭证录入与审核`）；
      - 6 维业务状态漏斗看板卡片：全部申请、待初审（黄）、待复核（蓝）、待记账（紫）、已记账（绿）、被驳回（红），支持点击快捷筛选；
      - 检索卡片：支持申请单号、正式凭证号、制单人、审批状态、会计日期范围等多维检索；
      - 主表格行展开（Expand Row）即时预览分录明细与底部借贷平衡看板；
      - 规范操作列（`MoreFilled` “...” 紧凑下拉菜单，列宽 70px）；
      - 制单填制弹窗（1000px）：凭证头栅格 + 动态借贷分录明细表格（增删行、借贷蓝橙徽标、末级科目搜索联动、正数金额绝对值校验、一键自动平衡差额、底部实时借贷平衡核验看板、保存草稿与提交初审双按钮）；
      - 初审把关弹窗与终审复核弹窗：录入审核人姓名与审批意见，支持审核通过与驳回退回；
      - 确认记账操作弹窗：强合规提示横幅、录入记账人姓名，确认后触发记账引擎并回显正式凭证号，支持一键穿透至凭证全景档案；
      - 经典财务凭证印签预览详情弹窗（参考原型 `凭证详情.html`）：经典纸质凭证仿真样式、“记 账 凭 证”字号、年月日、分录借贷对照表、大写及小写合计金额、四方签章栏（会计主管、审核、记账、制单）及全量流转可追溯时间轴；
      - 验证通过：TypeScript 0 错误，`vite build` 打包 100% 成功，后端单测 100% 通过。
  → 完成内容（Step 23.4.2 手工凭证全量增强 — 增量数据库脚本、辅助核算分摊与凭证附件支持）：
    - 增量数据库脚本标准化交付：
      - 增量 DDL 脚本规范落地：`docs/sql/9-manual-voucher-apply.sql` 与 `accounting-core/src/main/resources/db/migration/V9__manual_voucher_apply.sql`；
      - 包含 5 张表完整结构定义：申请单主表（`t_manual_voucher_apply`）、分录明细表（`t_manual_voucher_apply_entry`）、辅助核算分摊表（`t_manual_voucher_apply_auxiliary`）、附件明细表（`t_manual_voucher_apply_attachment`）及流转审计日志表（`t_manual_voucher_audit_log`）；
      - 仓储层自愈能力：`ManualVoucherApplyRepository.initTables()` 包含全部 5 张表的自动初始化检查。
    - 辅助核算分摊（Auxiliary Accounting）全链路支持：
      - 持久化模型与数据访问：新建 `ManualVoucherApplyAuxiliaryPO`、`ManualVoucherApplyAuxiliaryMapper`，仓储层提供批量插入、按申请单查询/删除等能力；
      - 契约层 DTO：新建 `ManualVoucherApplyAuxiliaryRequest`、`ManualVoucherApplyAuxiliaryResponse`，支持关联分录行、科目、核算类别（部门/项目/客户/供应商）、项目编码与名称、增减方向（1-增, 2-减）及核算金额；
      - 业务编排与转正落库：制单保存或重新编辑时原子落库；确认记账（`executeBookkeeping`）时将辅助核算分摊项自动转正入库至法定凭证表 `t_accounting_voucher_auxiliary`；
      - 前端交互（制单 + 原型凭证详情）：制单弹窗支持动态增删维护分录辅助核算；凭证印签详情弹窗严格参考原型《凭证详情.html》行 448-585，高保真还原“辅助核算项”独立专业表格。
    - 原始单据附件（Attachments）全链路支持：
      - 持久化模型与数据访问：新建 `ManualVoucherApplyAttachmentPO`、`ManualVoucherApplyAttachmentMapper`，支持文件名称、存储路径/URL、文件大小（Bytes）；
      - 契约层 DTO：新建 `ManualVoucherApplyAttachmentRequest`、`ManualVoucherApplyAttachmentResponse`，服务端自动解析文件类型徽标（PDF/JPG/PNG/XLSX）与可读大小格式化（如 1.00 MB）；
      - 附件张数动态联动：申请单 `attachment_count` 随附件列表自动精准统计，并在正式记账转入法定凭证表 `t_accounting_voucher` 时同步落库至 `t_accounting_voucher_attachment`；
      - 前端交互（录入 + 原型附件弹窗）：制单弹窗支持录入单据与“快速填入示范附件”；详情弹窗顶端显示“附单据 N 张”与“查看附件”按钮，严格参考原型《凭证附件.html》行 60-175 弹出附件档案管理弹窗，支持文件格式徽章展示、文件大小展示与查看/下载操作。
    - 质量与测试验收：
      - 单元测试覆盖：`ManualVoucherApplicationServiceTest` 新增 `testSaveAndBookkeepingWithAuxiliaryAndAttachment`，全套 7 个单测用例 100% 通过（0 失败 0 错误）；
      - 前端工程验证：TypeScript 0 错误，`npm run build` 打包 100% 成功。
  → 完成内容（Step 23.4.3 手工凭证体验与规范优化 — 字典驱动、制单人锁定与持久化枚举对齐）：
    - 字典动态驱动改造：
      - 凭证类型从前端写死改造为自系统字典动态拉取（`dictType = voucher_type`），附带稳健降级兜底；
      - 辅助核算分摊“核算类别”从前端写死改造为自系统字典动态拉取（`dictType = auxiliary_type`），联动回填核算项默认信息；
    - 制单人安全锁定：
      - 制单人输入框设为只读禁用，杜绝随意篡改，默认填充当前操作员；
    - 领域枚举与持久化深度解耦（根除 SQLException: Incorrect integer value: 'PENDING_AUDIT'）：
      - 根因：原 `ManualVoucherApplyStatusEnum` 错置在 `accounting-api`，受限于契约层严禁引入持久层依赖规范而无法添加 `@EnumValue`；
      - 修复：将枚举迁移归位至领域层 `com.kltb.accounting.core.domain.enums`，为 `code` 标注 `@EnumValue`；
      - `ManualVoucherApplyPO` 原生使用领域枚举，由 MyBatis-Plus 自动完成 Java 枚举与 MySQL `TINYINT` 的持久化双向映射。
    - 待办技术债与后续演进清单（TODO Backlog）：
      - [TODO-SEC-01] 制单人身份自动从登录上下文注入：当前系统尚未集成统一认证中心（SSO / SecurityContext），制单人暂时默认当前操作用户并置灰只读，后续待统一登录鉴权模块就绪后动态获取；
      - [TODO-OSS-01] 凭证原始附件对象存储直传服务对接：当前系统尚未对接对象存储服务（MinIO / Aliyun OSS / S3），待底层对象存储服务接入后将附件录入改造为文件直传与哈希防篡改校验。
  → 完成内容（Step 23.4.4 手工记账与凭证全景档案深度完善 — 魔法字符根除、字典驱动交易、科目账号智能推导、事务联动与印签打印）：
    - 消除魔法字符与领域枚举补全：
      - 新建 `ManualVoucherAuditActionEnum` 领域枚举：涵盖创建草稿、提交审核、重新提交、审核通过/驳回、复核通过/驳回、记账、作废等全生命周期动作；
      - 新建 `ManualVoucherOperatorRoleEnum` 领域枚举：涵盖制单人、审核人、复核人、记账人、系统等五大流转角色；
      - 全面替换业务服务与流转审计日志中的硬编码字符串。
    - 消除业务硬编码与字典元数据增强（Flyway V14）：
      - 新建 `V14__voucher_type_ext_and_manual_dicts.sql` 增量脚本；
      - 补充字典项：`voucher_type = ADJUST`（调账凭证）、`business_code = MANUAL`（手工记账）、`trading_code = ADJUST`（账务调整）；
      - 为 `voucher_type` 字典（收、付、转、调、冲、结等）扩充 `ext_json` 元数据：统一配置字头（prefix）、大标题（title）、默认交易类型（tradeType）、交易码（tradingCode）与资金渠道（payChannel）；
      - `ManualVoucherApplicationService.executeBookkeeping` 完全从字典动态解析交易要素，彻底消除写死代码。
    - TradeType 逻辑修正：
      - 手工凭证录入与记账时 `tradeType` 默认设为正常（1-NORMAL）；
      - 仅当凭证类型为调账（`ADJUST`）或字典配置为调账时，自动推导为调账类型（2-ADJUSTMENT）。
    - 记账与过账流程闭环明确：
      - 明确财务核心规则：手工凭证在【确认记账】时系统后台原子生成正式法定凭证、事务记录并直接触发实时过账更新分户余额，一步到位，无需也不应由用户在凭证管理页面进行二次手动过账。
    - 会计科目反查账户智能推导与多账号拦截：
      - 手工制单时分户账号允许选填；
      - 制单提交时后端根据末级科目编码自动反查 `t_account`；
      - 若该科目下唯一定位到 1 个分户账户，自动智能补全 `account_no`；
      - 若该科目下开立了多个分户账户，严正抛出业务异常（`PARAM_ERROR`），拦截并提示用户手工明确选择具体分户账号，防止窜账；
      - 若未开立分户账户，明确提示账户不存在（`ACCOUNT_NOT_FOUND`）。
    - 过账引擎失败凭证放行重试：
      - `PostingApplicationService` 调整过账前置状态校验，放行过账失败状态（`VoucherStatusEnum.FAILED`），支持在排查问题后手动重新触发过账。
    - 完整业务事务记录（t_transaction）闭环：
      - 手工凭证确认记账时，先落库 `t_transaction`（状态 `PROCESSING`，业务类型 `MANUAL`），并回填至 `t_accounting_voucher.txn_no`；
      - 过账引擎完成后将事务状态更新为 `SUCCESS`，形成“业务单据 - 事务 - 凭证 - 分录 - 分户账”的完整追溯链条。
    - 记账凭证管理（全景档案）印签详情与打印支持：
      - `views/business/voucher/index.vue` 增加“凭证印签详情”操作与仿真纸质印签弹窗，**严格隐藏审批流转时间轴**；
      - 显式区分展示【机制凭证】与【手工凭证】标签；
  → 完成内容（Step 23.4.5 系统级字典治理与硬编码全面清除 — 公共字典 ext_json 组件、系统字典类型枚举、币种枚举、审批决策枚举）：
    - 字典与 ext_json 统一公共组件落地：
      - 新建 `DictionaryComponent`（Spring Bean）与 `VoucherTypeMeta` 领域模型，提供泛型对象反序列化、安全属性提取（String/Int/BigDecimal/Bool）与按编码/名称灵活反查；
      - 封装凭证类型元数据装配与优雅降级兜底，彻底根除各业务类中重复的 JSONUtil 解析和 catch 块。
    - 动态字头与标题彻底消除硬编码：
      - `VoucheringAssembler.toFullDetail` 与 `ManualVoucherApplicationService.getDetail` 彻底删除 `if (contains("收款") || "RECEIPT".equalsIgnoreCase(...))` 庞大硬编码分支；
      - 统一接入 `DictionaryComponent.getVoucherTypeMeta`，实现凭证大标题（title）与字头（prefix）由字典及 `ext_json` 100% 动态驱动，新增凭证类型无需修改任何 Java 代码。
    - 系统级字典分类枚举（DictTypeEnum）：
      - 新建 `DictTypeEnum` 枚举（`voucher_type`, `pay_channel`, `trading_code`, `funds_type`, `auxiliary_type`, `business_code`, `account_type`, `currency`），收拢所有系统级字典类型，杜绝魔法字符串。
    - 币种枚举与硬编码 "CNY" 全面治理：
      - 新建 `CurrencyEnum`（遵循 ISO 4217 规范），定义标准币种与 `DEFAULT_CURRENCY = "CNY"`；
      - 全面重构 `ManualVoucherApplicationService`、`TemplateConverter`、`AccountOpeningDomainService`、`JournalingDomainService`、`VoucheringDomainService`、`EodDomainService` 中的 `"CNY"` 硬编码；
      - 手工记账流水与事务记录的币种由分录自动动态继承，非硬编码绑定人民币。
    - 审批与保存操作枚举化：
      - 新建 `AuditDecisionEnum`（`PASS` 通过, `REJECT` 驳回）与 `ManualVoucherSaveActionEnum`（`DRAFT` 保存草稿, `SUBMIT` 提交初审）；
      - 初审、复核与保存逻辑彻底消除 `"PASS".equalsIgnoreCase`、`"DRAFT".equalsIgnoreCase` 魔法比对，非法操作码严格校验拦截。
  → 待进行业务页面：日切与试算平衡 (`business/eod`)、缓冲记账监控 (`business/buffer-monitor`)

---

## Phase 10：联调、性能验收与上线

- [ ] **Step 24** · Integration Testing｜全链路联调
  → 详见 `docs/prompt/step-24-integration.md`

- [ ] **Step 25** · Performance & Go Live｜性能验收与上线
  → 详见 `docs/prompt/step-25-golive.md`
