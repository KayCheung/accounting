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
    - 币种单一事实来源架构演进（废弃 CurrencyEnum，全量由 t_dictionary 驱动）：
      - 架构决议：遵循单一事实来源（SSOT）原则，彻底删除静态硬编码的 `CurrencyEnum`，消除与字典表双头维护与数据脱节的系统隐患；
      - 业务币种全面由系统字典表 `t_dictionary(dict_type='currency')` 动态驱动；
      - 建立全局聚合常量类 `Constants.java`（`DEFAULT_CURRENCY = "CNY"`、`SYSTEM_OPERATOR = "system"`），避免微小常量类碎片化离散，后续系统通用常量统一定义在此；
      - `DictionaryComponent` 提供 `isDictValid`（支持状态与有效性检查）与 `resolveCurrencyName`（字典名称反查与优雅降级兜底）；
      - 全面重构 `ManualVoucherApplicationService`、`TemplateConverter`、`AccountOpeningDomainService`、`JournalingDomainService`、`VoucheringDomainService`、`EodDomainService`，将币种默认值统一指向 `Constants.DEFAULT_CURRENCY`，币种解析动态依赖字典服务；
      - 手工记账流水与事务记录的币种由分录自动动态继承，非硬编码绑定人民币。
    - 审批与保存操作枚举化：
      - 新建 `AuditDecisionEnum`（`PASS` 通过, `REJECT` 驳回）与 `ManualVoucherSaveActionEnum`（`DRAFT` 保存草稿, `SUBMIT` 提交初审）；
      - 初审、复核与保存逻辑彻底消除 `"PASS".equalsIgnoreCase`、`"DRAFT".equalsIgnoreCase` 魔法比对，非法操作码严格校验拦截。
  → 完成内容（Step 23.4.6 过账引擎加固、SQL 语法空集合防御、事务失败原因截断保护与印签详情标签优化）：
    - 根治 SQLSyntaxErrorException near 'ORDER BY account_no ASC'：
      - 根因：`AccountMapper.xml` 的 `selectForUpdateBatch` 中 `<foreach>` 在 `accountNos` 为空集合或 null 时不渲染内容，导致拼接出 `WHERE account_no IN ORDER BY account_no ASC FOR UPDATE` 语法错误；
      - 修复：XML 改用 `<choose><when test="accountNos != null and accountNos.size() > 0">` 包装，空集合时回退至 `AND 1 = 0`；同时在 `AccountRepository.selectForUpdateBatch` 增加非空校验与有效账号过滤，入参为空时直接返回空列表，免查数据库。
    - 根治 Data truncation: Data too long for column 'fail_reason'：
      - 根因：底层 SQL 或业务异常堆栈过长，直接写入 `t_transaction.fail_reason`（VARCHAR(255)）触发 MySQL 截断异常，导致事务回滚自身崩溃；
      - 修复：在 `RollbackDomainService` 和 `TransactionRepository` 增加安全截断机制（超过 250 字符自动保留前 247 字符加省略号），保障失败状态与原因 100% 安全落库。
    - 过账前置分录账号合法性拦截：
      - 在 `PostingApplicationService` 过账前置检查中增加对分录 `accountNo` 的非空校验，阻断未关联分户账号的脏数据分录流入过账引擎。
    - 前端凭证全景档案与印签打印标签体验优化：
      - 移除 `manual-voucher/index.vue` 与 `voucher/index.vue` 凭证字号旁冗余重复的“收款凭证/记账凭证”标签；
      - 仅保留【手工凭证】/【机制凭证】来源标签，并配置舒展间距（`ml-2`），与顶层大标题各司其职，消除标签挤塞。
  → 完成内容（Step 23.4.7 1970 默认时间全链路脱敏过滤与凭证编号基于凭证类型动态前缀驱动）：
    - 数据库 1970-01-01 默认时间全链路脱敏过滤（后端 DTO 转 null + 前端格式化双重防御）：
      - 根因：MySQL DDL 默认值设置 `post_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00'`，凭证未过账时数据库填充默认时间并序列化至前端；
      - 后端治理：全局常量聚合类 `Constants.java` 新增 `EPOCH_DATE` (1970-01-01) 与 `EPOCH_DATE_TIME` (1970-01-01 00:00:00)；`AccountStatusAssembler`、`VoucheringAssembler` 与 `ReversalAssembler` 全面移除本地分散定义的重复常量，统一引用 `Constants` 并安全转为 `null`；
      - 前端双重防御：`voucher/index.vue` 与 `manual-voucher/index.vue` 的 `formatDateTime` 拦截 `!val || val.startsWith('1970-01-01')` 统一返回 `'-'`。
    - 凭证编号基于凭证类型（voucher_type）动态前缀驱动架构升级：
      - 彻底改变以往凭证号一律固定以 `VOU` 开头的生硬逻辑，依据财务会计标准凭证类型实现字母前缀动态推导；
      - `VoucherTypeMeta` 领域模型扩展 `voucherNoPrefix`（如 `REC`, `PAY`, `TRF`, `ADJ`, `REV`, `PET`, `VOU`）；
      - `DictionaryComponent` 统一解析 `ext_json.voucherNoPrefix`，未配置时通过 `deriveVoucherNoPrefix` 智能推导：
        - 收款凭证 (`RECEIPT`) → `REC`（如 `REC20260930000001`）
        - 付款凭证 (`PAYMENT`) → `PAY`（如 `PAY20260930000001`）
        - 转账凭证 (`TRANSFER`) → `TRF`（如 `TRF20260930000001`）
        - 调账凭证 (`ADJUST`) → `ADJ`（如 `ADJ20260930000001`）
        - 冲账凭证 (`REVERSAL`) → `REV`（如 `REV20260930000001`）
        - 期末结转凭证 (`PERIOD_END`) → `PET`（如 `PET20260930000001`）
        - 记账凭证/默认 (`GENERAL`) → `VOU`（如 `VOU20260930000001`）
      - 重构 `ManualVoucherApplicationService.executeBookkeeping`、`VoucheringDomainService.persistVoucher` 与 `PeriodEndTransferDomainService.generateTransferVoucher`，凭证编号生成全面切换至基于凭证类型的动态前缀。
      - 数据库沉淀：创建 Flyway 增量脚本 `V15__voucher_no_prefix.sql` 完善 `t_dictionary` 凭证类型扩展属性。
  → 完成内容（Step 23.4.8 核心业务单号生成器统一抽象、看板统计DTO强类型化、科目余额方向规范化与领域枚举深度治理）：
    - 统一业务单号与流水号生成器抽象 (`BusinessNoGenerator`)：
      - 新增通用业务单号生成组件 `BusinessNoGenerator`，统一收拢系统内凭证号 (`voucherNo`)、事务号 (`txnNo`)、分录流水号 (`entryId`)、手工记账申请号 (`applyNo`) 及期末结转号 (`transferNo`)；
      - 彻底根除历史私有方法中硬编码假日期（如 `"20260929"`、`"20260930"`）的严重缺陷，动态基于入参会计日期/系统时间生成；
      - 内置高并发 Redis 原子自增并支持动态格式化时间戳降级；
      - 全面重构 `ManualVoucherApplicationService`、`VoucheringDomainService`、`PeriodEndTransferDomainService` 与 `ReversalDomainService`，彻底消除各领域类各自为政的私有单号生成逻辑。
    - 看板状态统计强类型 DTO 治理 (`ManualVoucherStatisticsResponse`)：
      - 严禁在接口契约中使用 `Map<String, Object>`，定义专用响应结构体 `ManualVoucherStatisticsResponse`（`total`, `pendingAudit`, `pendingReview`, `pendingBookkeeping`, `booked`, `rejected`）；
      - 同步重构 `ManualVoucherController.getStatistics` 与 `ManualVoucherApplicationService.getStatistics`，确立严格强类型数据契约。
    - 财务金额中文大写工具抽象 (`FinancialAmountUtil`)：
      - 落地 `FinancialAmountUtil.toChineseWords(BigDecimal)` 公共工具类（位于 `shared/util`），提供财务金额中文大写标准转换与防御；
      - 统一接入 `ManualVoucherApplicationService` 与 `VoucheringAssembler`，消除各处零散重复私有转换实现。
    - 会计科目借贷余额方向严格权威判定（彻底根绝首字符猜测）：
      - 彻底废除 `firstChar == '2' || ...` 猜测科目借贷方向的反模式，确立科目表 `t_account_subject.debit_credit` 为唯一权威依据；
      - 记账推导分录 `changeDirection` 时严格读取 `AccountSubjectPO.getDebitCredit()`，科目不存在或未配置借贷方向时抛出明确 `AccountException` 拦截；
      - 分录借贷与科目余额方向一致记为增加(1)，相反记为减少(2)。
    - 实体层枚举化与展示描述治理：
      - `ManualVoucherApplyAuxiliaryPO.changeDirection` 字段类型由 `Integer` 升级为 `ChangeDirectionEnum`；
      - `ManualVoucherApplyPO.tradeType` 字段类型由 `Integer` 升级为 `TradeTypeEnum`；
      - 前端响应装配与展示一律调用枚举原生 `getDesc()`，消除所有 `po.getTradeType() == 1 ? "正常" : "调账"`、`? "减少" : "增加"` 胶水代码；
      - 审计日志记录消除 `"ACTION"`、`"SYSTEM"`、`"OPERATOR"` 魔法字符串，统一收拢至 `ManualVoucherAuditActionEnum`、`Constants.SYSTEM_OPERATOR` 与 `ManualVoucherOperatorRoleEnum`。
  → 完成内容（Step 23.4.9 实时与异步过账架构纯净化治理与链路元数据完整性保障 BUG261008-002）：
    - 根因分析与架构反思：
      - 早期实现中为避免 `t_account_detail` 与 `t_sub_account_detail` 数据库 NOT NULL 约束报错，在过账层硬编码填充 `"INTERNAL"`、`"ACCOUNTING"`、`"SYSTEM"`、`TradeTypeEnum.NORMAL`、`TXN...000000` 等伪默认值，甚至在过账阶段临时生成事务号与事务主记录；
      - 此举严重违背“Trace -> Txn -> Voucher -> Detail”全链路权威一致性原则，破坏了审计追溯链条与按渠道/业务维度的统计准确性，属于严重职责越界；
      - 历史方法 `executeRealTimePosting(entries, accountingDate)` 脱离了聚合根凭证上下文，迫使内部引入 `voucherCache` 跨库查询，产生接口歧义与多余开销。
    - 纯净化重构落地：
      - 废除伪造默认值与职责倒置：过账引擎严禁兜底捏造事务或填充假默认值。明细的 `txnNo`、`traceNo`、`traceSeq`、`businessCode`、`tradingCode`、`payChannel`、`tradeType`、`tradeTime`、`accountingDate`、`tenantId` 必须 100% 忠实继承自所属凭证聚合根；
      - 确立前置强契约与快速失败（Fast-Fail）：过账前严格校验 `voucher != null && StrUtil.isNotBlank(voucher.getTxnNo())`，凡上游凭证核心元数据不完备者一律立即拦截并抛出明确异常，坚决杜绝脏数据入库；
      - 彻底清除 `voucherCache`：过账最小操作单元即为单张凭证，分录均隶属于当前凭证，无需且不应存在方法内凭证缓存；
      - 废除脱离凭证的旧重载与冗余参数：删除 `executeRealTimePosting(entries, accountingDate)`，会计日期直接取自 `voucher.getAccountingDate()`，统一收敛为单一强类型入口：`executeRealTimePosting(AccountingVoucherPO voucher, List<AccountingVoucherEntryPO> entries)`；
      - 瘦身依赖注入：`PostingDomainService` 移除对 `TransactionRepository`、`BusinessNoGenerator` 与 `AccountingVoucherRepository` 的无用注入，恢复过账纯粹领域职责；
      - `PostingApplicationService`、`ReversalDomainService`、`AsyncPostingDomainService` 同步完成契约对齐，全面通过单元测试验证。
  → 完成内容（Step 23.4.10 账户状态统一校验器抽象与风控状态一体化治理）：
    - 统一校验器落地 (`AccountValidator`)：
      - 新增基础组件 `AccountValidator`（位于 `infrastructure/account`，与 `AccountBalanceCalculator` 同级）；
      - 集中统一封装：存在性校验（`validateExists`）、主状态校验（`validateNormal`）、过账主状态+风控状态联合校验（`validatePostable`）、资金冻结操作校验（`validateFreezable`）、状态机转换校验（`validateTransition`）；
      - 全面消除散落在 `PostingDomainService`、`PostingApplicationService`、`AsyncPostingDomainService`、`FreezeDomainService`、`AccountStatusChangeDomainService` 各处的重复冗余 if-else 状态判定；
      - 统一全链路异常状态码映射（`ACCOUNT_NOT_FOUND` 3001, `ACCOUNT_FROZEN` 3002, `ACCOUNT_CANCELLED` 3003, `ACCOUNT_RISK_BLOCKED` 3004, `ACCOUNT_STATUS_ILLEGAL` 3005），确保异常提示与前置拦截口径严格一致；
  → 完成内容（Step 23.4.11 过账链路数据读取与校验前置治理——“读验先于写”原则落地）：
    - 根因分析：
      - `AsyncPostingDomainService.consumeAsyncPostingMessage` 历史逻辑中存在严重的反模式：在更新主账户与子账户余额（`accountRepository.updateById`）之后，才在第 9 步查询关联凭证 `accountingVoucherRepository.selectByVoucherNoSimple` 并校验 `voucher` 是否存在以及 `txnNo` 是否非空；
      - 若凭证缺失或核心字段非法，或在更新主账户后子账户余额不足，数据库已被脏写或部分更新，破坏了领域模型的干净性与 Fast-Fail 前置失败原则。
    - 纯净化与前置校验落地：
      - **第一阶段：只读预检与快速失败（Fast-Fail）**：进入消费方法后，按严格顺序先检验 `payload` 核心参数，读取并校验分录（分录已过账则直接幂等返回），读取并校验凭证及其 `txnNo` 完整性，通过 `AccountValidator` 预检目标账户的存在性与可入账状态。在任何加锁与写操作前完成所有只读前置校验；
      - **第二阶段：悲观行锁**：按账号加行级排他锁，获取最新持久态；
      - **第三阶段：纯内存余额试算与绝对值法则校验**：基于行锁最新余额试算主账户与子账户的新余额，若余额不足在进入写库前立即拦截抛出 `INSUFFICIENT_BALANCE`；
      - **第四阶段：原子落库与明细持久化**：统一执行账户更新、分录状态更新、明细落库、状态联动、消息确认与回执入库；
      - **实时过账同步对齐**：`PostingDomainService.executeRealTimePosting` 在执行批量账户更新前，同样引入前置循环预校验，确保批次内所有分录的目标账户均有效且状态正常，彻底根绝“部分账户更新后遇到异常回滚”的脏操作隐患；
      - **测试保障**：新增单元测试 `AsyncPostingDomainServiceTest`（10 个测试用例，覆盖参数缺失、分录不存在、幂等跳过、凭证缺失、事务号空白、账户不存在、账户冻结、余额不足先验拦截、正常主子账户过账等），并与 `PostingDomainServiceTest`、`AccountValidatorTest` 全部通过。
  → 完成内容（Step 23.4.12 科目余额方向更新缺陷修复 BUG261008-001）：
    - 根因分析：前端科目编辑弹窗虽然可操作单选框变更余额方向，但 `updatePayload` 漏传了 `debitCredit`；后端 `SubjectUpdateRequest` DTO 缺失 `debitCredit` 字段，且 `SubjectConverter.updatePO` 亦未映射该字段；另外编辑非顶级科目时单选框会被 `:disabled="Boolean(currentParent)"` 误锁。
    - 修复内容：
      - 后端 DTO：`SubjectUpdateRequest` 增加 `debitCredit` 字段（1=借方，2=贷方）；
      - 后端转换器：`SubjectConverter.updatePO` 补充 `debitCredit` 的枚举映射与更新；
      - 前端接口与页面：`accounting-ui/src/api/subject.ts` 的 `SubjectUpdateRequest` 接口补充 `debitCredit?: number`；`subject/index.vue` 的 `updatePayload` 补全 `debitCredit` 传参，将禁用条件放宽为 `:disabled="!isEditMode && Boolean(currentParent)"`；
      - 质量验证：新增 `SubjectApplicationServiceTest` 单元测试验证余额方向更新逻辑，Maven 全模块编译与单测 100% 通过，前端 `vue-tsc && vite build` 生产构建 0 错误通过。
  → 完成内容（Step 23.4.13 转换器层枚举映射坏味道治理与 SSOT 对齐）：
    - 根因与坏味道清理：
      - 彻底消除 `SubjectConverter`、`TemplateConverter`、`BufferRuleConverter`、`RuleConverter` 中重复硬编码定义的私有静态枚举 Map（`CATEGORY_MAP`、`NATURE_MAP`、`DEBIT_CREDIT_MAP`、`CUSTOMER_TYPE_MAP`、`BALANCE_DIR_MAP`、`BUFFER_MODE_MAP`、`STATUS_MAP`、`ACCOUNT_SCOPE_MAP`、`ALLOCATION_METHOD_MAP`）；
      - 消除 `DictConverter` 等处的 `status == 1 ? ENABLED : DISABLED` 魔法数字判断；
    - 统一重构落地：
      - 统一收敛至各枚举类内聚的 `fromCode(Integer code)` 方法，实现单一事实来源（SSOT）；
      - 补全 `AccountScopeEnum.fromCode` 方法；
      - 修复 `AccountStatusChangeDomainServiceTest` 与 `LocalMessageRetryJobTest` 中 mock 缺失与分片上下文传递缺陷；
      - 全模块 Maven Reactor 构建、219 个后端单元测试与前端 `npm run build` 100% 通过。
  → 完成内容（Step 23.4.14 乐观锁版本号自动托管与仓储层受影响行数强校验治理 BUG261008-004）：
    - 根因定位与分析：
      - 现象：过账成功，`t_account_detail` 与 `t_sub_account_detail` 正常生成记录，但 `t_account` 与 `t_sub_account` 的余额未发生任何改变；
      - 根因 1（业务层手动修改 version）：实体 `AccountPO` 与 `SubAccountPO` 的 `version` 字段标注了 `@Version`，且全局配置了 MyBatis-Plus 乐观锁拦截器 `OptimisticLockerInnerInterceptor`。插件会自动提取实体当前版本号作为 CAS 条件（`WHERE version = ?`）并在更新时自增（`SET version = version + 1`）。业务代码（`PostingDomainService`、`AsyncPostingDomainService`、`RollbackDomainService`）手动执行了 `version = version + 1`，传给插件后 CAS 条件变为 `WHERE version = (原version + 1)`，永远无法匹配数据库记录，导致更新行数恒为 0；
      - 根因 2（仓储层静默吞错）：`AccountRepository.updateById` 与 `SubAccountRepository.updateById` 仅返回布尔值，调用方未检查返回值且无任何异常抛出，导致更新 0 行被直接放行，事务正常提交，引发严重的账实不符漏洞。
    - 修复与治理落地：
      - **彻底剥离业务层 version 干扰**：从 `PostingDomainService`、`AsyncPostingDomainService`、`RollbackDomainService` 中彻底删除手动 `setVersion(...)` 代码，保留实体查出的原生版本号，全权交由 MyBatis-Plus 乐观锁拦截器安全处理 CAS 匹配与版本号自增；
      - **仓储层“真正的成功”强校验**：严格贯彻金融核心铁律，在 `AccountRepository`、`SubAccountRepository`、`AccountDetailRepository`、`SubAccountDetailRepository`、`AccountingVoucherRepository` 的 `insert`、`updateById`、`updateEntryById` 等写操作中实施受影响行数强校验（`affected > 0`）。更新 0 行显式抛出 `OPTIMISTIC_LOCK_FAILED`，插入 0 行显式抛出 `SYSTEM_ERROR`，严禁任何形式的静默放行；
      - **自动化测试保障**：新增仓储受影响行数专项单元测试 `PersistenceAffectedRowsEnforcementTest`（11 个用例），并在 `PostingDomainServiceTest` 与 `AsyncPostingDomainServiceTest` 中增加实体版本号未被业务层篡改的断言，全模块 230 个单元测试 100% 通过。
  → 完成内容（Step 23.4.15 实时过账分录状态持久化闭环修复 BUG261008-005）：
    - 根因定位与分析：
      - 现象：凭证实时过账成功后，主表变为已过账（POSTED），但分录表状态仍为未过账；发起红冲时触发 `validateReversable` 强校验拦截并报错 `code=2036, message=原凭证分录未全部过账`；
      - 根因：早期瘦身重构中将 `AccountingVoucherRepository` 移出了 `PostingDomainService`，导致实时过账仅在内存中执行了 `entry.setStatus(POSTED)` 和 `balanceUpdateTime`，漏掉了持久化更新分录到数据库；
    - 修复与闭环：
      - `PostingDomainService` 重新注入 `AccountingVoucherRepository`，在实时入账循环中显式调用 `accountingVoucherRepository.updateEntryById(entry)` 将分录更新落库；
      - 增强 `AccountingVoucherRepository.updateEntryById` 支持实体无主键 ID 时降级通过唯一业务单号 `entryId` 更新；
      - `PostingDomainServiceTest` 单元测试增加对分录持久化的 `verify` 校验，全模块全量 230 个单测全部通过。
  → 完成内容（Step 23.4.16 凭证分录增减方向类型枚举化重构与强类型收敛）：
    - **领域对象强类型化**：将 `AccountingVoucherEntryPO.changeDirection` 字段由原生 `Integer` 重构为 `ChangeDirectionEnum` 枚举类型，统一与 `AccountDetailPO`、`AccountingVoucherAuxiliaryPO` 等明细实体的类型定义；
    - **余额与风控计算器支持**：`AccountBalanceCalculator` 与 `AccountValidator` 补充对 `ChangeDirectionEnum` 的重载支持，消除过账与回滚过程中的三元表达式魔法数字转换（`1->INCREASE, 2->DECREASE`）；
    - **红冲与回滚方向收敛**：规范 `ReversalDomainService` 与 `RollbackDomainService` 中的分录方向翻转逻辑，红冲分录明确将 `changeDirection` 对调为相反方向，实现余额精准冲销；
    - **测试健全与回归**：更新 `AccountBalanceCalculatorTest`、`PostingDomainServiceTest`、`PostingMonitorDomainServiceTest` 及 `ManualVoucherApplicationServiceTest`，全工程 233 个单元测试全部通过。
  → 完成内容（Step 23.4.17 过账服务子账户盲取治理与精准可用余额收敛 BUG261008-006）：
    - **根因与风险消除**：排查并彻底治理 `PostingDomainService`、`AsyncPostingDomainService`、`RollbackDomainService`、`BufferPostingEngineDomainService` 中使用 `subs.get(0)` 盲取子账户的重大隐患，防止在主账户拥有可用（AVAILABLE）和冻结（FROZEN）多子账户时，因数据库检索记录排位变化误触冻结余额；
    - **精准过滤与明细对齐**：
      - 统一收敛为按 `BalanceTypeEnum.AVAILABLE` 精准流过滤匹配（`subs.stream().filter(s -> BalanceTypeEnum.AVAILABLE.equals(s.getBalanceType())).findFirst().orElse(null)`）；
      - 同步将 `SubAccountDetailPO` 的 `balanceType` 对齐子账户实体的原生余额类型，杜绝类型不一致风险；
    - **自动化测试保障与回归**：
      - 修复 `ReversalDomainServiceTest` 中 `TransactionCallback` 导包路径并补全上下文 Mock；
      - 在 `PostingDomainServiceTest` 中新增多子账户倒序排列（FROZEN 在前、AVAILABLE 在后）防护单测，严密验证冻结子账户零篡改、可用子账户精准扣增；
      - 更新 `AsyncPostingDomainServiceTest` 等测试用例，全工程全量 236 个单元测试 100% 成功通过。
  → 完成内容（Step 23.4.18 红冲凭证独立 trace_no 与唯一索引防冲治理 BUG261008-007）：
    - **根因分析**：
      - 现象：执行凭证红冲时抛出 `DuplicateKeyException: Duplicate entry 'MVA20261008000002-1' for key 'uk_trace_no'`；
      - 根因：原 `ReversalDomainService.buildReversalVoucher` 直接复用了原凭证的 `traceNo` 和 `traceSeq`。由于表 `t_accounting_voucher` 包含唯一键 `uk_trace_no (trace_no, trace_seq)`，红冲凭证插入时与原凭证冲突；
    - **修复与落地**：
      - 在核心编号生成器 `BusinessNoGenerator` 中补齐统一跟踪号生成能力：`generateTraceNo(LocalDate date)` 与 `generateTraceNo(String prefix, LocalDate date)`，基于 Redis 原子递增生成标准格式跟踪号；
      - 重构 `ReversalDomainService.buildReversalVoucher`，红冲凭证生成全新的跟踪号 `businessNoGenerator.generateTraceNo(accountingDate)`，`traceSeq` 设为 1，消除唯一索引冲突；
      - 过账引擎 `PostingDomainService` 将该全新跟踪号无缝透传至 `t_account_detail` 与 `t_sub_account_detail`，符合审计与追溯要求；
      - 更新 `ReversalDomainServiceTest`，补充对红冲凭证独立 `traceNo` 与 `traceSeq` 的单测断言，全工程 236 个单元测试 100% 通过。
  → 完成内容（Step 23.4.19 子账户明细与账户明细必填字段防御性补全与防 null 治理 BUG261008-008）：
    - **根因分析**：
      - 现象：凭证红冲过账时报 `DataIntegrityViolationException: Field 'txn_no' doesn't have a default value`；
      - 根因：底层 MySQL 表 `t_sub_account_detail` 与 `t_account_detail` 的 `txn_no`、`trace_no`、`trace_seq`、`trading_code` 为 `NOT NULL` 且无默认值。在手工凭证或红冲等场景中，若上游未传业务订单号 `txn_no`（为 null），MyBatis-Plus 动态 SQL 生成策略会跳过 null 字段，导致 `INSERT INTO` 语句中完全不包含 `txn_no` 列，触发 MySQL 严格模式完整性约束异常；
    - **修复与闭环**：
      - **仓储守门防线（Repository Defensive Guard）**：在 `SubAccountDetailRepository.insert` 和 `AccountDetailRepository.insert` 中对数据库 `NOT NULL` 且无默认值的列实施绝对防 null 兜底（`txnNo` 兜底 `""`、`traceNo` 兜底凭证号/`""`、`traceSeq` 兜底 1、`tradingCode` 兜底 `""`、`businessCode` 兜底 `""`、`payChannel` 兜底 `""`）；
      - **过账与冻结引擎赋值健壮性强化**：
        - `PostingDomainService` 在构造 `AccountDetailPO` 与 `SubAccountDetailPO` 时对凭证属性进行非空安全提取（`StrUtil.isNotBlank(...)`）；
        - `ReversalDomainService` 红冲凭证构建赋予合理业务默认码（`businessCode="MANUAL"`、`tradingCode="REVERSAL"`、`payChannel="INTERNAL"`）；
        - `FreezeDomainService` 补齐子账户明细插入时缺失的 `txnNo`、`traceNo`、`traceSeq`、`tradingCode` 赋值；
  → 完成内容（Step 23.4.20 资金冻结业务线与交易编码常量收敛及全量必填字段治理）：
    - **问题定位与业务语义**：
      - 表 `t_account_freeze_detail` 中 `business_code` 代表业务线编码（字典 CODE）。早期接口未开放业务线参数，代码中直接硬编码了魔法字符串 `"GENERAL"`，且缺失 `txn_no`、`trading_code`、`trace_no`、`trace_seq` 等非空必填字段的系统设置；
    - **重构与治理落地**：
      - **系统通用聚合常量收敛**：在契约层 `Constants.java` 中新增 `DEFAULT_BUSINESS_CODE = "GENERAL"`（默认通用业务线）、`TRADING_CODE_FREEZE = "FREEZE"`、`TRADING_CODE_UNFREEZE = "UNFREEZE"`、`TRADING_CODE_DEDUCT = "DEDUCT"`，彻底消除魔法字符串；
      - **上游透传与默认兜底兼顾**：请求 DTO `FundFreezeRequest` 扩展可选 `businessCode` 字段，应用层与领域服务 `FreezeDomainService.freezeFund` 支持按业务线冻结并使用 `Constants.DEFAULT_BUSINESS_CODE` 安全兜底；
      - **全量必填字段补全与仓储守门**：`FreezeDomainService` 补齐 `AccountFreezeDetailPO` 插入时的 `txnNo`、`tradingCode`、`traceNo`、`traceSeq` 等必填字段；在 `FreezeDetailRepository.insert` 中加入受影响行数强校验与防 null 兜底；
      - **自动化测试保障**：在 `FreezeDomainServiceTest`、`FreezeApplicationServiceTest`、`PersistenceAffectedRowsEnforcementTest` 中补齐业务线断言与防御性单测，全工程 238 个测试用例 100% 通过。
  → 完成内容（Step 23.4.21 资金部分解冻与剩余全解分录流水号冲突修复及剩余金额流转治理 BUG261009-001）：
    - **根因定位与分析**：
      - 现象：资金冻结支持部分解冻，首次部分解冻成功，但第二次发起解冻（解冻剩余全部资金）时报错：`SQLIntegrityConstraintViolationException: Duplicate entry 'FRZ20261009000001-FRZ20261009000001-2-UFZ' for key 'uk_voucher_no'`；
      - 根因 1（分录流水号静态拼接引发唯一键冲突）：MySQL 表 `t_sub_account_detail` 存在唯一约束 `UNIQUE KEY uk_voucher_no (voucher_no, entry_id)`（其中 `entry_id VARCHAR(32)`）。原 `FreezeDomainService.insertSubAccountDetail` 生成 `entry_id` 时采用静态字符串拼接：`voucherNo + "-" + balanceType.getCode() + "-" + operation`（如 `FRZ...-2-UFZ`）。同一冻结凭证号（`voucher_no`）在多次解冻时，`voucherNo`、`balanceType`、`operation` 均完全相同，导致第 2 次解冻生成的 `entry_id` 必然与第 1 次重复，触发数据库唯一键冲突；同理多次扣款时主账户明细 `t_account_detail` 的 `(voucher_no, entry_id)` 也存在相同隐患；
      - 根因 2（单据剩余金额未扣减与终态判定失准）：原逻辑在部分解冻时未更新 `t_account_freeze_detail` 单据本身的剩余金额（`freeze_amount`），且解冻与扣款后单据状态判断使用了 `newFrozen.compareTo(BigDecimal.ZERO) == 0`（即整个账户冻结子账户余额是否为 0）。若账户存在多笔冻结，该判断将严重失真，且单据剩余额度无法正确追踪。
    - **修复与治理落地**：
      - **分录流水号动态唯一化重构**：
        - `FreezeDomainService` 引入 `BusinessNoGenerator` 统一流水号生成器；
        - 子账户明细 `insertSubAccountDetail` 统一采用 `businessNoGenerator.generateEntryId(operation)`（格式如 `UFZ20261009000001`，长度 24 位 <= 32 位），扣款主账户明细 `insertAccountDetail` 统一采用 `businessNoGenerator.generateEntryId("DED")`，彻底杜绝多次解冻与多次扣款场景下的主/子明细流水号冲突；
        - 交易编码与动作规范对齐，解冻使用 `Constants.TRADING_CODE_UNFREEZE`，扣款使用 `Constants.TRADING_CODE_DEDUCT`，冻结使用 `Constants.TRADING_CODE_FREEZE`；
      - **单据剩余金额扣减与状态流转闭环**：
        - 在 `AccountFreezeDetailMapper` 与 `FreezeDetailRepository` 中新增带乐观锁校验的 `updateAmountAndStatus(voucherNo, newFreezeAmount, status, version)`；
        - `FreezeDomainService` 严密校验解冻金额不超过单据当前剩余金额（`unfreezeAmount <= currentRecord.getFreezeAmount()`），计算单据剩余金额 `remaining = currentRecord.getFreezeAmount().subtract(unfreezeAmount)`；
        - 若 `remaining == 0`，单据状态更新为 `UNFROZEN(2)`，金额更新为 0；若 `remaining > 0`，单据状态保持 `FROZEN(1)`，金额更新为 `remaining`；扣款逻辑同样精确维护单据剩余金额与状态流转；
      - **仓储层防御与自动化测试保障**：
        - `SubAccountDetailRepository.insert` 与 `AccountDetailRepository.insert` 增加 `entryId` 防 null/空字符串兜底；
        - `FreezeDomainServiceTest` 新增 `unfreezeFund_partialAndFullSuccess_noDuplicateEntry` 单测，覆盖“冻结 100 → 部分解冻 40 → 全部解冻剩余 60”完整链路，断言状态由 FROZEN 最终流转为 UNFROZEN，且生成 4 条子账户明细流水号各不相同；
  → 完成内容（Step 23.4.22 资金冻结明细初始金额与累计已解冻/已扣款扩展字段补齐治理）：
    - **背景与守恒模型**：
      - 为支持对资金冻结单据全生命周期的精确审计追溯，对冻结明细表补齐“初始冻结金额（`orig_freeze_amount`）”、“累计已解冻金额（`unfrozen_amount`）”、“累计已扣款金额（`deducted_amount`）”三项扩展字段；
      - 严格满足金融核心守恒定律：`orig_freeze_amount == freeze_amount + unfrozen_amount + deducted_amount`；
    - **DDL 脚本与持久化层落地**：
      - 新增 Flyway 增量迁移脚本 `V16__add_freeze_detail_ext_amounts.sql`，支持幂等增列与历史存量数据平滑回填；
      - `AccountFreezeDetailPO` 增加对应字段，`FreezeDetailRepository.insert` 加入防御性非空兜底；
      - `AccountFreezeDetailMapper` 与 `FreezeDetailRepository` 新增 `updateAmountsAndStatus` 乐观锁更新方法；
    - **领域服务流转与 API/UI 展现闭环**：
      - `FreezeDomainService.freezeFund` 冻结时初始化初始金额为 `freezeAmount`，已解冻与已扣款置为 0；
      - `unfreezeFundInternal` 与 `deductFromFreeze` 分别原子累加已解冻金额与已扣款金额，并同步递减剩余有效冻结金额；
      - API 契约层 `FreezeDetailResponse` 与 `FreezeListResponse` 增加扩展金额字段；
      - 前端 `accounting-ui`（`freeze/index.vue`）表格增加“初始冻结金额”、“已解冻金额”、“已扣款金额”列，详情抽屉与解冻/扣款弹窗同步透出初始金额与剩余可用额度；
      - 全模块 Maven 单元测试 249 个用例 100% 通过，前端 `npm run build` 100% 成功。
  → 完成内容（Step 23.4.23 日切与试算平衡业务页面开发与全链路接口打通）：
    - **API 契约对接封装**：
      - 新增 `accounting-ui/src/api/eod.ts`，严格对接后端 `EodController` 的全部 5 大接口（状态查询 `getEodStatus`、手动切日 `switchDate`、前置诊断 `getPreCheckResult`、试算平衡 `getTrialBalance`、手动日切全流程调度 `executeEod`）；
    - **业务视图与交互看板落地**：
      - 新增 `accounting-ui/src/views/business/eod/index.vue`，实现工作台与控制台聚合设计：
        1. 会计日期状态看板：展示全局 T 日会计日期、状态标签机（1~9 映射）、耗时统计与动态生命周期流程步骤条（`el-steps`：瞬间切日→存量清理→余额快照→试算平衡→期末结转→账务归档→完成），失败阶段（`failedStage`）自适应高亮与错误警报提示；
        2. 前置诊断卡片：展示缓冲明细、在途事务、未过账凭证、在途流水等关键指标项积压计数与通行状态判定；
        3. 试算平衡借贷看板：借方发生额合计、贷方发生额合计、借贷差额 KPI 对比，不平衡科目告警提示；
        4. 科目发生额明细表格：支持科目代码复制、借贷发生额与净差额千分位右对齐（`AmountDisplay`）、仅看异常/不平衡科目过滤；
        5. 对话框与报告抽屉：手动瞬间切日弹窗、手动日切确认弹窗、日切执行结果结构化报告抽屉（含阶段状态、余额生成笔数、快照笔数、期末结转凭证清单）；
    - **路由与打包闭环**：
      - 更新 `router/index.ts`，将 `business/eod` 占位路由正式接入 `views/business/eod/index.vue`；
      - 前端 `npm run build`（`vue-tsc && vite build`）100% 成功通过，零编译警告与类型错误。
  → 完成内容（Step 23.4.24 缓冲记账监控业务页面开发与全链路接口打通）：
    - **API 契约对接封装**：
      - 新增 `accounting-ui/src/api/buffer-monitor.ts`，严格对接后端 `BufferPostingController`（待入账统计 `getBufferPendingStats`、大盘监控 `getBufferMonitor`、手动触发入账 `executeBufferPosting`）；
    - **业务视图与交互看板落地**：
      - 新增 `accounting-ui/src/views/business/buffer-monitor/index.vue`，实现大盘监控与风控预警聚合设计：
        1. 待入账模式存量看板：按 3 种缓冲模式（模式 1 异步逐条、模式 2 日间批量、模式 3 日终批量）展示待入账笔数与发生金额（千分位右对齐），透出最早待入账时间与涉及账户总数；
        2. 状态分布与入账流转大盘：展示待入账、处理中、成功、失败 4 类状态的笔数、金额及占比分布；
        3. Running Balance 动账余额校验告警（核心风控）：校验缓冲入账后账户动账余额与实际余额一致性，正常态显示绿色安全通过，异常态红色高亮警示账户、实际余额、推导余额与差额；
        4. 入账失败 Top 账户排行：展示失败笔数最高的账户清单，支持一键发起该账户的针对性重试入账；
        5. 对话框与报告抽屉：手动触发缓冲记账弹窗（支持指定模式、指定单账户、批次大小配置）、执行结果报告抽屉（含总数、成功数、失败数、耗时及失败明细清单）；
    - **路由与打包闭环**：
      - 更新 `router/index.ts`，将最后一个占位路由 `business/buffer-monitor` 正式接入 `views/business/buffer-monitor/index.vue`；
      - 前端 `npm run build`（`vue-tsc && vite build`）100% 成功通过，至此前端所有业务页面已全部开发完毕！
  → 完成内容（Step 23.4.25 日余额与快照批量入库 tenant_id 缺失与拦截器改写旁路缺陷修复 BUG261009-002）：
    - **根因分析**：
      - 现象：日切时计算并持久化日余额报错 `java.sql.SQLIntegrityConstraintViolationException: Column 'tenant_id' cannot be null`；
      - 根因 1（MyBatis-Plus 拦截器 SQL 改写旁路机制）：`TenantLineInnerInterceptor` 基于 JSqlParser 改写 INSERT 语句时，仅在 INSERT 列名列表中**不含** `tenant_id` 时才会自动追加该列和默认值。若 XML 中**已显式声明** `tenant_id` 列（如 `AccountBalanceMapper.xml` 中的 `batchUpsertBalance`），拦截器判定开发者已手动指定，从而**完全跳过该列和值的改写**，放行原始占位符 `#{item.tenantId}`；
      - 根因 2（业务层 PO 实体构造漏赋租户）：`EodDomainService.calculateDailyBalances` 与 `generateDailySnapshot` 在 `new AccountBalancePO()` 和 `new AccountBalanceSnapshotPO()` 时，仅填充业务金额与科目字段，未调用 `setTenantId(TenantContext.get())`；
      - 根因 3（MyBatis-Plus MetaObjectHandler 无法自动填充 XML 批量 SQL）：框架公共字段自动填充仅对 BaseMapper 原生方法有效，对自定义 XML `<foreach>` 动态批量语句不触发，导致 JDBC 绑定 null 传入 MySQL 严格约束列报错；
    - **三层纵深防御修复落地**：
      - **第一道防线（仓储层守门员）**：在 `AccountBalanceRepository.batchUpsert` 和 `AccountBalanceSnapshotRepository.batchInsert` 中增加防御性遍历，对 `tenantId == null` 的实体自动填入 `TenantContext.get()`（未设置时兜底 `SYSTEM_TENANT = -1`）；
      - **第二道防线（领域服务显式赋值）**：在 `EodDomainService.calculateDailyBalances` 和 `generateDailySnapshot` 中显式设置 `balance.setTenantId(TenantContext.get())` 与 `snapshot.setTenantId(...)`；
      - **第三道防线（XML 动态 SQL 兜底）**：在 `AccountBalanceMapper.xml` 与 `AccountBalanceSnapshotMapper.xml` 中将参数绑定升级为 `COALESCE(#{item.tenantId}, -1)`；
  → 完成内容（Step 23.4.26 日切失败重试状态幂等重置与唯一键 uk_eod_date 冲突修复 BUG261009-003）：
    - **根因分析**：
      - 现象：日切因异常失败后，运维或管理人员在前端重新触发日切进行重试时，系统抛出 `java.sql.SQLIntegrityConstraintViolationException: Duplicate entry '2026-10-08-0' for key 'uk_eod_date'`；
      - 根因 1（数据库唯一性审计约束）：`t_eod_status` 表定义了唯一索引 `uk_eod_date (accounting_date, is_delete)`，保障每一会计日全局仅有一条有效生命周期记录；
      - 根因 2（失败状态持久化保留）：首次触发日切时创建了初始状态记录，随后因业务/数据/SQL 异常中断后，catch 块将记录状态置为 `9(失败)` 并记录了失败阶段与原因，该记录依然保留在表中；
      - 根因 3（仓储层缺乏幂等与状态重置能力）：重试时再次调用 `EodStatusRepository.createStatus`，原代码盲目执行 `insert`，未对已有记录进行判断和状态重置，直接撞上 `uk_eod_date` 唯一键冲突；
    - **修复方案与财务状态机治理落地**：
      - **仓储层幂等重置改造**：`EodStatusRepository` 新增 `resetStatus(LocalDate accountingDate)`，在重试时原子重置 `eod_status = 1`，并清空 `failed_stage`、`fail_reason`、`archive_date_time` 与执行耗时；
      - **生命周期安全防重判定**：
        1. 场景 A（未存在记录）：首次发起日切，正常执行 `insert`；
        2. 场景 B（已存在记录且 `eod_status == 8` 完成）：严格遵守金融关账规范，抛出 `AccountException(ResultCode.EOD_ALREADY_EXECUTED)`，阻断已完成会计日的重复执行；
        3. 场景 C（已存在记录且处于非完成/失败状态）：判定为日切重试，调用 `resetStatus` 幂等重置状态，平滑支持重跑；
        4. 场景 D（高并发插入兜底）：捕获 `DuplicateKeyException`，再次查询并重置，防止并发击穿；
    - **领域服务与测试健全**：
      - `EodStatusDomainService` 记录明确的重试审计日志；
      - 新增 `EodStatusRepositoryTest`（4 个用例）与 `EodStatusDomainServiceTest`（7 个用例），覆盖首次创建、失败重试重置、已完成防重拦截、并发兜底以及失败超长原因截断等全场景；
  → 完成内容（Step 23.4.27 期末结转规则配置与执行管理全功能闭环交付）：
    - **业务定位与会计律法核心规范**：
      - 明确期末结转在金融账务核心生命周期中的定位：损益类科目（收入/费用）期末余额轧差清零并归集至所有者权益科目（本年利润），是日终核算与月度结账的关键节点；
      - 修复纠正借贷结转方向：对于借方余额（费用类），贷记源科目、借记目标科目；对于贷方余额（收入类），借记源科目、贷记目标科目；确保科目余额严格归零且借贷发生额绝对平衡；
    - **API 契约层与 DTO 扩充（accounting-api）**：
      - 请求 DTO：`TransferRuleQueryRequest`, `TransferRuleCreateRequest`, `TransferRuleUpdateRequest`, `TransferRuleStatusRequest`, `TransferExecuteRequest`, `TransferRecordQueryRequest`；
      - 响应 DTO：`TransferRuleResponse`, `TransferPreviewResponse`, `TransferPreviewRuleItemResponse`, `TransferPreviewEntryItemResponse`, `TransferExecuteBatchResponse`, `TransferExecuteItemResponse`, `TransferRecordResponse`；
      - 错误码扩展：`ResultCode` 扩充 2041~2046 结转专项错误码（规则不存在、编码已存在、目标科目非末级、借贷不平衡、生成凭证失败、已结转且未开启重试）；
    - **后端核心层治理与增强（accounting-core）**：
      - 领域服务 `PeriodEndTransferDomainService`：新增只读试算预览 `previewTransfer`（Dry Run 模式实时扫描科目余额表，借贷轧差计算，不落库、不修改余额）；重构 `executeTransfer` 支持按单规则/多规则执行、`forceRetry` 重试幂等清理与正式凭证出具；
      - 应用服务 `PeriodEndTransferApplicationService`：落地规则编码唯一性校验、目标科目末级有效性校验（`leaf == true`）、规则 CRUD、批量预览及执行组装；
      - 仓储层 `PeriodEndTransferRuleRepository` & `PeriodEndTransferRecordRepository`：补充 MyBatis-Plus 分页、状态切换、物理/逻辑控制与历史记录条件查询；
      - RESTful 控制器：新增 `PeriodEndTransferRuleController` (`/accounting/transfer/rule`) 与 `PeriodEndTransferController` (`/accounting/transfer`)；
      - 专属单元测试：新增 `PeriodEndTransferDomainServiceTest` (3 个用例) 与 `PeriodEndTransferApplicationServiceTest` (4 个用例)，后端全工程 269 个测试 100% 通过；
    - **前端视图与向导式工作台交付（accounting-ui）**：
      - 封装 API 客户端 `accounting-ui/src/api/transfer.ts`，涵盖规则 CRUD、试算预览、执行与审计查询；
      - 规则配置视图 `accounting-ui/src/views/config/transfer-rule/index.vue`：支持规则编码、类型、执行顺序、通配符/末级科目选择、状态启停、摘要模板变量与快速删除；
      - 结转工作台视图 `accounting-ui/src/views/business/transfer/index.vue`：
        1. 顶部全局系统会计日与联机状态监控；
        2. 向导三部曲：Step 1 参数与规则勾选 -> Step 2 在线试算预览（Dry Run 宏观看板、借贷平衡指示灯、分规则分录展开明细、借贷发生额合计对比）-> Step 3 执行结果卡片与凭证号归档；
        3. 结转历史审计台账：支持流水号、会计日、凭证号多维检索，支持点击凭证号直接弹窗全景查看凭证档案；
      - 路由与菜单接入：更新 `router/index.ts` 与 `Sidebar.vue`；
      - 前端打包验证：`npm run build`（`vue-tsc && vite build`）100% 成功，零编译警告与类型错误。
  → 完成内容（Step 23.4.28 期末结转规则自动结转配置与周期化执行支持）：
    - **领域周期模型与触发算法（accounting-core）**：
      - 新增 `PeriodCycleEnum`（1-每日 DAILY, 2-月末 MONTHLY, 3-季末 QUARTERLY, 4-年末 YEARLY, 5-仅手动 MANUAL）；
      - 内置日历触发器算法 `isTriggerable(LocalDate accountingDate)`：严格校验每日、月末（`TemporalAdjusters.lastDayOfMonth`）、季末（3/6/9/12 月末）、年末（12 月 31 日）与纯手工规则；
      - `PeriodEndTransferRulePO` 扩展 `autoTransfer`（是否支持自动结转）与 `periodCycle` 字段；
    - **规则查询与自动结转过滤**：
      - `PeriodEndTransferRuleRepository` 扩展 `selectAutoTriggerableRules`，仅筛选出启用、`autoTransfer=true` 且符合当前会计日期周期条件的规则；
      - `PeriodEndTransferDomainService` 落地 `executeAutoTransfer`（自动结转模式）与 `executeTransfer`（手动特批模式）双轨执行机制，日切 EOD 阶段 6 仅触发自动规则，彻底防止非周期规则误跑；
    - **独立自动化定时任务（accounting-job）**：
      - 新增 `PeriodEndTransferJobHandler`（`@XxlJob("periodEndTransferJob")`），支持解耦于日切的独立定时调度（如每月末 23:30 自动跑），具备失败报警与上下文审计能力；
    - **API 契约与 DTO 增强（accounting-api）**：
      - `TransferRuleCreateRequest`, `TransferRuleUpdateRequest`, `TransferRuleQueryRequest`, `TransferRuleResponse`, `TransferPreviewRuleItemResponse` 全量对齐 `autoTransfer` 与 `periodCycle`；
    - **前端配置与向导视图联动（accounting-ui）**：
      - `src/views/config/transfer-rule/index.vue`：支持自动结转（开关/筛选/列）与结转周期（单选组/筛选/彩色标签）的完整配置与快速启停；
      - `src/views/business/transfer/index.vue`：步骤 1 增加结转周期多维过滤，表格与预览卡片清晰透出周期与自动结转标识；
      - 前端 `npm run build` 与后端全模块 275 个单元测试 100% 成功通过。
- [x] **Step 23** · 业务功能页面开发全量交付完毕（100% 完成）


---

## Phase 10：联调、性能验收与上线

- [ ] **Step 24** · Integration Testing｜全链路联调
  → 详见 `docs/prompt/step-24-integration.md`

- [ ] **Step 25** · Performance & Go Live｜性能验收与上线
  → 详见 `docs/prompt/step-25-golive.md`
