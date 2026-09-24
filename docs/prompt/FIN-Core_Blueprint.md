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
  → 待进行业务页面：余额与明细查询 (`business/balance`)、资金冻结与扣款 (`business/freeze`)、记账凭证管理 (`business/voucher`)、日切与试算平衡 (`business/eod`)、缓冲记账监控 (`business/buffer-monitor`)

---

## Phase 10：联调、性能验收与上线

- [ ] **Step 24** · Integration Testing｜全链路联调
  → 详见 `docs/prompt/step-24-integration.md`

- [ ] **Step 25** · Performance & Go Live｜性能验收与上线
  → 详见 `docs/prompt/step-25-golive.md`
