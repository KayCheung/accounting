# step-11-transaction · 事务管理

> **Phase 4 第四步（⚠️ 最高风险模块）** | 归属：`@Java` 工程师
> 前置依赖：Step 9（业务流水入库已完成）、Step 10（凭证生成引擎已完成）、Step 4（本地消息表 + RocketMQ 封装已就绪）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `TransactionMapper` 补充方法 | `TransactionMapper.java`（已有，需补充） | 补充 `updateStatusByTxnNo`、`selectByTxnNo`（已有 `selectByTraceNo`） |
| P0-2 | `TransactionRepository` 补充方法 | `TransactionRepository.java`（已有，需补充） | 封装 P0-1 的 Repository 层方法 |
| P0-3 | `AccountingVoucherMapper` 补充方法 | `AccountingVoucherMapper.java`（已有，需补充） | 补充 `updateStatusByVoucherNo`、`selectByVoucherNo`（已有 `updateTxnNoByVoucherNo`） |
| P0-4 | `AccountingVoucherEntryMapper` 补充方法 | `AccountingVoucherEntryMapper.java`（已有，需补充） | 补充 `selectByVoucherNoWithStatus`（按状态过滤分录） |
| P0-5 | 确认 MQ Producer 可用 | 读取 `LocalMessageService.java` 已有 `sendLocalMessage` | 用于 `is_unilateral=0` 分录发 MQ 异步过账 |
| P0-6 | DDL 列补充 | `docs/sql/2-voucher.sql` + `all-tables.sql` | `t_accounting_voucher_entry` 新增 `is_unilateral` + `is_buffered` 两列（已补充，确认一致即可） |
| P0-7 | `AccountingVoucherEntryPO` 补充字段 | `AccountingVoucherEntryPO.java` | 新增 `unilateral`（Integer）、`buffered`（Integer）、`changeDirection`（Integer）属性，显式使用 `@TableField("is_unilateral")` / `@TableField("is_buffered")` / `@TableField("change_direction")` 注解映射 DDL 新列。`changeDirection` 由 Step 10 凭证生成时从规则配置推导后写入，过账阶段直接使用，无需重新推导（P1-1 修复） |

> 上述补充任务由 Java-A 在开始编码前一并完成，不单独拆分子任务文件。

---

## 1. 任务目标（Mission）

实现事务管理与过账编排引擎，作为记账核心引擎的**第三阶段**，承接 Step 10 的凭证生成能力，将凭证分录转换为实际的账户余额变更。

核心职责：
1. **事务状态管理**：维护 `t_transaction` 从 PROCESSING → SUCCESS/FAILED 的完整生命周期
2. **过账编排**：根据凭证分录的 `is_unilateral` / `is_buffered` 持久化标记，分流为实时过账、MQ 异步过账或缓冲入账
3. **实时过账**：按 `account_no` 升序加锁 → 余额计算 → 更新账户/子账户余额 → 记录明细快照
4. **异步过账**：写入本地消息表 → Job 扫描 → MQ 投递 → 消费端执行过账
5. **事务回滚**：捕获异常 → TransactionTemplate 自动回滚 → 更新事务状态为 FAILED → 记录失败原因 → 补偿处理 → 触发告警
6. **状态联动**：分录全部过账成功后联动更新凭证状态为 POSTED(3)
7. **重试与补偿**：临时性异常最多重试 3 次指数退避，永久性异常直接标记失败

**事务管理是记账流程的最终落地环节，所有财务数据变更（余额/明细/快照）在此环节完成。失败时必须回滚，严禁产生脏数据。**

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、借贷方向约束、余额计算规则、并发控制 |
| 3 | `docs/design/domain-model.md` | 账户域、凭证域、流水域模型 |
| 4 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` / `t_account_detail` / `t_sub_account_detail` DDL |
| 5 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry`（含 is_unilateral / is_buffered）DDL |
| 6 | `docs/sql/5-journal.sql` | `t_transaction` / `t_business_record` DDL |
| 7 | `docs/sql/6-infra.sql` | `t_local_message` / `t_message_receipt` DDL |
| 8 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（四阶段） |
| 9 | `docs/design/flowchart/transaction_rollback_flow.mmd` | 事务回滚流程图 |
| 10 | `accounting-core/.../domain/service/VoucheringDomainService.java` | Step 10 凭证生成领域服务 |
| 11 | `accounting-core/.../application/service/VoucheringApplicationService.java` | Step 10 应用服务（含 txnNo 回填） |
| 12 | `accounting-core/.../infrastructure/persistence/repository/TransactionRepository.java` | 事务仓储 |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-11-java-a.md` | P0 补充 + PostingDomainService（实时过账核心逻辑：加锁 + 余额计算 + 账户更新 + 明细快照） + AccountBalanceCalculator |
| Java-B | `docs/prompt/tasks/step-11-java-b.md` | AsyncPostingDomainService（MQ 异步过账：本地消息写入 + 消费端逻辑） + RollbackDomainService（回滚 + 补偿 + 单边记账处理） |
| Java-C | `docs/prompt/tasks/step-11-java-c.md` | PostingApplicationService（过账编排用例 + 事务生命周期管理 + 重试机制） + PostingController（3 个接口） + DTO + Assembler |

> **依赖关系**：Java-A 最先完成（实时过账是核心基础设施）。Java-B 依赖 Java-A 的余额计算能力，做异步过账和回滚处理。Java-C 依赖 Java-A/B 的领域服务，做上层编排和事务生命周期管理。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 过账入口流程

```
Step 10 已完成 → 凭证已生成（status=1 未过账）+ 分录已持久化（含 is_unilateral / is_buffered 列）
  ↓
业务系统/Step 10 自动触发 POST /accounting/posting/execute
  ↓
【事务层】按 voucherNo 查询 t_accounting_voucher + t_accounting_voucher_entry
  ↓ 凭证不存在 → 抛出 ServiceException(VOUCHER_NOT_FOUND)
  ↓ 凭证 status != 1(未过账) → 抛出 ServiceException(POSTING_STATUS_INVALID)
  ↓
【状态层】按 account_no 升序收集所有 is_unilateral=1 的分录对应账户
  ↓ 逐账户检查 t_account.status = 1(正常)
  ↓ 账户冻结 → 抛出 AccountException(ACCOUNT_FROZEN)
  ↓ 账户注销 → 抛出 AccountException(ACCOUNT_CANCELLED)
  ↓ 账户不存在 → 抛出 AccountException(ACCOUNT_NOT_FOUND)
  ↓ 分布式锁 accounting:{tenantId}:lock:posting:trx:{voucherNo}
  ↓ 加锁失败 → 抛出 ServiceException(IDEMPOTENT_CONFLICT)
  ↓
【事务层】开启 TransactionTemplate 事务
  ↓
【状态层】更新凭证 status=2(过账中)
  ↓
【过账层】逐分录处理（按 DDL 持久化标记分流）：
  ├─ is_unilateral=1 → 实时过账（Java-A）
  │   1. 按 account_no 升序 SELECT FOR UPDATE 锁定账户
  │   2. 余额计算（同向相加 / 反向相减，前置校验余额充足）
  │   3. 更新 t_account.balance + t_sub_account.balance
  │   4. 写入 t_account_detail（含 Pre/Post 快照）
  │   5. 写入 t_sub_account_detail
  │   6. 更新分录 status=2(已过账)
  ├─ is_unilateral=0 且 is_buffered=0 → MQ 异步过账（Java-B）
  │   1. 写入 t_local_message（status=1 待发送）
  │   2. 分录 status 保持 1(未过账) ← 等待 MQ 消费完成后更新
  ├─ is_unilateral=0 且 is_buffered=1 → 缓冲入账
  │   1. 不处理，留给 Step 16 缓冲记账处理
  │   2. 分录 status 保持 1(未过账)
  ↓
【校验层】检查分录类型构成
  ↓ 全实时分录 → 更新凭证 status=3(已过账)
  ↓ 存在异步分录 → 凭证 status 保持 2(过账中) ← 等待 MQ 消费完成
  ↓
【事务层】事务状态更新：
  ↓ 全实时分录 → t_transaction.status=2(成功) + finishTime
  ↓ 存在异步分录 → t_transaction.status 保持 1(处理中) ← 等 MQ 消费完成
  ↓ 回填 TransactionPO.relateAccountCount
  ↓
【返回】过账完成（实时部分），异步部分由 MQ 消费端继续处理
```

### 4.2 余额计算规则（财务律法，最高优先级）

```
绝对值法则：
  严禁负数运算，严禁通过负数冲正。

  change_direction 来源（P1-1 修复）：
    Step 10 凭证生成时，从记账规则配置推导 change_direction 并写入 t_accounting_voucher_entry.change_direction。
    过账阶段直接读取该字段，无需重新推导科目性质。

  过账时：
    change_direction = 1 → 余额增加：newBalance = oldBalance + amount
    change_direction = 2 → 余额减少：前置校验 oldBalance >= amount
                          newBalance = oldBalance - amount
                          不足则抛 AccountException(INSUFFICIENT_BALANCE)

  主账户与子账户同步更新：
    主账户 t_account.balance 变更
    对应子账户 t_sub_account（balance_type=1 可用）同步变更
    冻结子账户（balance_type=2）不受过账影响
```

### 4.3 实时过账加锁规则

```
涉及多账户加锁必须按 account_no 升序，防止死锁。

  1. 收集该凭证所有 is_unilateral=1 分录的 account_no（去重）
  2. 按 account_no 字符串升序排序
  3. 在同一事务中按顺序 SELECT FOR UPDATE
  4. 计算余额 → 更新余额 → 记录明细 → 提交事务

  ⚠️ 严禁：
    - 不按顺序加锁（死锁风险）
    - 在锁外计算余额（并发安全问题）
    - 使用乐观锁代替悲观锁（实时路径必须悲观锁）
```

### 4.4 MQ 异步过账规则

```
is_unilateral=0 且 is_buffered=0 的分录走异步路径：

  1. 在实时过账事务中写入 t_local_message（与凭证/分录更新同事务）
  2. 消息 payload 包含：voucherNo、entryId、accountNo、amount、debitCredit、accountingDate、summary
  3. Job 扫描 t_local_message（status=1）→ 调用 RocketMQ Producer 发送消息
  4. 消费端收到消息后执行过账（独立事务）：
     a. 幂等检查：查询分录 status 是否已是 2(已过账)，是则直接返回成功
     b. 按 account_no 升序 SELECT FOR UPDATE
     c. 余额计算 → 更新余额 → 记录明细
     d. 更新分录 status=2(已过账)
     e. 检查凭证所有非缓冲分录是否都已过账 → 更新凭证状态
     f. 如果全部分录（含异步+实时）都已过账：
        - 更新凭证 status=3(已过账)
        - 更新 t_transaction.status=2(成功) + finishTime
     g. 更新 t_local_message.status=2(已发送)
     h. 写入 t_message_receipt（消费成功）
  5. 消费失败 → 写入 t_message_receipt（消费失败）→ 触发重试
  6. 重试 3 次仍失败 → 调用 RollbackDomainService 执行单边回滚 → 触发告警

  ⚠️ 事务状态时序（P1-2/P1-5 修复）：
     - 全实时分录：主事务完成后直接 status=2(成功)，终态
     - 含异步分录：主事务完成后 status 保持 1(处理中)
     - MQ 消费全部完成 → status=2(成功)
     - MQ 消费失败 → 调用单边回滚 → status=3(失败)
     - SUCCESS 和 FAILED 都是终态，不可互转
```

### 4.5 事务回滚规则

```
回滚触发场景：
  1. 余额不足（INSUFFICIENT_BALANCE）— 永久性异常，不可重试
  2. 账户状态异常（账户冻结/注销/止付）— 永久性异常
  3. 系统异常（数据库异常/网络超时）— 临时性异常，可重试
  4. 业务规则校验失败（风控拦截/额度超限）— 永久性异常
  5. 异步分录消费失败（MQ 消费端）— 特殊场景，见 4.6

  回滚处理流程（实时路径失败）：
    1. 捕获异常，记录 error 级别日志（含 voucherNo、accountNo、异常堆栈）
    2. TransactionTemplate 自动回滚事务（实时过账的事务）
    3. 通过独立 TransactionTemplate 执行状态更新（不在已回滚的事务内）：
       a. 更新 t_transaction.status = 3(失败)，记录 fail_reason + finishTime
       b. 更新 t_business_record.status = 3(失败)（通过 traceNo 关联，P1-4 修复）
       c. 更新 t_accounting_voucher.status = 4(过账失败)
       d. 更新 t_accounting_voucher_entry.status = 3(过账失败）
    4. 释放分布式锁
    5. 触发告警（邮件/短信/监控平台）
    6. 向上层抛出 AccountException(POSTING_FAILED)

  ⚠️ 重试规则：
    - 临时性异常：最多重试 3 次，指数退避（10s / 30s / 60s）
    - 永久性异常：不重试，直接标记失败
```

### 4.6 单边记账回滚（特殊场景）

```
场景：实时分录已提交成功，异步分录 MQ 消费失败

  此时实时过账事务已提交，无法回滚。需要执行反向操作：

    1. 查询凭证的所有分录状态，找出已过账的实时分录（status=2）
    2. 生成反向凭证号（格式：REV + 原 voucherNo，如 REVVOU20260512000001）
    3. 在 t_accounting_voucher 中写入一条反向凭证记录（status=4 过账失败，summary 标注"异步回滚-原凭证:xxx"），确保审计可追溯（P2-8 修复）
    4. 对每个已过账分录执行反向操作：
       a. 使用反向凭证号 + 新生成 entryId（REV_ENTRY + 序号），避免原表 uk_entry_id 唯一约束冲突
       b. 反向更新账户余额：
          - 原来是"增加" → 改为"减少"
          - 原来是"减少" → 改为"增加"
          - 本质：change_direction 取反（1↔2）
       c. 记录反向明细到 t_account_detail（voucher_no 填反向凭证号，summary 标注"异步回滚-原entryId:xxx"）
       d. 含 Pre/Post 余额快照
    5. 更新原凭证 status=4(过账失败)，记录失败原因
    6. 更新事务 status=3(失败)，记录失败原因
    7. 触发告警

  设计原则（P2-8 修复）：
    - 反向凭证号 REV+原号 对应一条真实写入 t_accounting_voucher 的记录
    - 反向分录的 voucher_no 填反向凭证号，而非原凭证号（避免 uk_voucher_no 冲突）
    - 反向分录的 entry_id 使用新生成 ID，避免 uk_entry_id 冲突
    - 通过完整的反向凭证链保留审计追溯，而非"灰色"的跨表直接修改
```

### 4.7 凭证状态联动规则

```
过账过程中凭证状态流转：

  Step 10 生成凭证 → status = 1(未过账)
    ↓
  过账开始 → status = 2(过账中)
    ↓
  全部分录过账成功（实时+异步）→ status = 3(已过账)
    ↓
  过账失败 → status = 4(过账失败)
    ↓
  Step 18 红冲 → status = 5(已冲销)

  分录全部过账成功的判定（排除缓冲分录）：
    - 所有 is_unilateral=1 的分录 status=2(已过账)
    - 所有 is_unilateral=0 且 is_buffered=0 的分录 status=2(已过账)
    - is_buffered=1 的分录不计入（由 Step 16 处理）
```

### 4.8 会计日期与事务

```
会计日期在 Step 9 流水入库时已确定（t_business_record.accounting_date）。
事务管理阶段不修改会计日期，全链路使用 Step 9 确定的会计日期。

t_transaction.accounting_date = t_business_record.accounting_date（一致）
t_accounting_voucher.accounting_date = t_business_record.accounting_date（一致）
t_accounting_voucher_entry.accounting_date = t_business_record.accounting_date（一致）
```

### 4.9 租户 ID 来源（P1-7 补充）

```
分布式锁 Key 中的 {tenantId} 不通过请求参数传入，由系统自动获取：

  - 来源：TenantContextHolder.getTenantId()（ThreadLocal）
  - 设置时机：BFF 层 / API Gateway 拦截器从 JWT / Header 中提取
  - 隔离：所有锁 Key、SQL 查询自动拼接 tenantId 前缀
```

---

## 5. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── PostingExecuteRequest.java         # 过账执行请求 DTO
    └── response/
        └── PostingExecuteResponse.java        # 过账结果响应 DTO
            └── PostingEntryResponse.java      # 过账分录行响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── PostingDomainService.java          # 实时过账领域服务（Java-A）
    │       ├── AsyncPostingDomainService.java     # 异步过账领域服务（Java-B）
    │       └── RollbackDomainService.java         # 回滚领域服务（Java-B）
    ├── application/
    │   ├── service/
    │   │   └── PostingApplicationService.java     # 过账编排应用服务（Java-C）
    │   └── assembler/
    │       └── PostingAssembler.java              # PO ↔ Request/Response 转换（Java-C）
    ├── infrastructure/
    │   ├── account/
    │   │   └── AccountBalanceCalculator.java      # 余额计算器（Java-A，无状态工具类）
    │   └── messaging/
    │       └── PostingMessagePayload.java         # 过账消息体（Java-B）
    └── controller/
        └── PostingController.java                 # 过账 Controller（Java-C）
```

**需要修改/补充的 Mapper**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `TransactionMapper.java` | 补充方法 | 已有空壳，追加 `updateStatusByTxnNo`、`selectByTxnNo` |
| `AccountingVoucherMapper.java` | 补充方法 | 已有空壳，追加 `updateStatusByVoucherNo`、`selectByVoucherNo` |
| `AccountingVoucherEntryMapper.java` | 补充方法 | 已有空壳，追加 `selectByVoucherNoWithStatus` |
| `AccountMapper.java` | 补充方法 | 已有空壳，追加 `selectForUpdate`（SELECT FOR UPDATE） |
| `SubAccountMapper.java` | 补充方法 | 已有空壳，追加 `selectForUpdate` |
| `AccountDetailMapper.java` | 补充方法 | 已有空壳，追加 `batchInsert` |
| `SubAccountDetailMapper.java` | 补充方法 | 已有空壳，追加 `batchInsert` |
| `BusinessRecordMapper.java` | 补充方法 | 已有空壳，追加 `updateStatusByTraceNo`（回滚时更新流水状态，P1-4 修复） |

**已有 Repository（直接复用）**：

| 文件 | 已有方法 | 需补充 |
|------|----------|--------|
| `AccountingVoucherRepository` | `insert()`、`selectByTraceNo()`、`selectEntriesByVoucherNo()`、`insertEntry()`、`updateById()` | `updateStatusByVoucherNo`、`selectByVoucherNo` |
| `TransactionRepository` | `selectByTraceNo()` | `updateStatusByTxnNo`、`selectByTxnNo` |
| `AccountRepository` | `existsByOwnerIdAndSubjectCode`、`selectByOwnerIdAndSubjectCode` | `selectForUpdate`、`updateBalanceWithVersion` |
| `BusinessRecordRepository` | Step 9 已创建，含 `updateStatusByTraceNo` | 确认方法可用 |

**需新建的 Repository**：

| 文件 | 方法 |
|------|------|
| `SubAccountRepository` | `selectForUpdate`、`updateBalanceWithVersion` |
| `AccountDetailRepository` | `batchInsert` |
| `SubAccountDetailRepository` | `batchInsert` |

---

## 6. 接口契约

所有接口路径前缀：`/accounting/posting`

### 6.1 POST `/accounting/posting/execute` — 执行过账

**请求体** (`PostingExecuteRequest`):
```json
{
  "voucherNo": "VOU20260512000001",
  "operatorName": "SYSTEM"
}
```

**校验规则**：
- `voucherNo`：必填，长度 1-32
- `operatorName`：非必填，默认 "SYSTEM"

**业务逻辑**：
1. 按 voucherNo 查询凭证，校验 status=1(未过账)
2. 逐账户检查 t_account.status = 1(正常)
3. 获取分布式锁 `accounting:{tenantId}:lock:posting:trx:{voucherNo}`
   （tenantId 从 TenantContextHolder 获取，P1-7 修复）
4. 开启 TransactionTemplate 事务
5. 更新凭证 status=2(过账中)
6. 逐分录过账（实时/异步/缓冲分流）
7. 检查分录类型构成 → 联动更新凭证状态
8. 更新事务状态（全实时→2成功，含异步→保持1处理中）
9. 释放分布式锁
10. 返回 `PostingExecuteResponse`

### 6.2 GET `/accounting/posting/voucher/{voucherNo}` — 查询过账状态

**响应**：凭证基本信息 + 分录过账状态列表 + 关联事务状态

### 6.3 GET `/accounting/posting/transaction/{txnNo}` — 查询事务状态

**响应**：事务基本信息 + 关联凭证过账进度

---

## 7. 领域服务设计

### 7.1 PostingDomainService（Java-A 负责）

> **职责**：实时过账核心逻辑，不含事务，由 Application Service 统一控制事务边界。

```java
@Service
@RequiredArgsConstructor
public class PostingDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 实时过账：锁定账户 → 计算余额 → 更新余额 → 记录明细
     *
     * @param entries 需要实时过账的分录列表（is_unilateral=1）
     * @param accountingDate 会计日期
     * @throws AccountException 当余额不足或账户状态异常时抛出
     */
    public void executeRealTimePosting(
        List<PostingEntryData> entries,
        LocalDate accountingDate);

    /**
     * 单笔分录过账（内部方法）
     */
    private void postSingleEntry(
        PostingEntryData entry,
        LocalDate accountingDate);

    /**
     * 检查凭证所有实时分录是否都已过账
     */
    public boolean areAllRealTimeEntriesPosted(String voucherNo);
}
```

### 7.2 AsyncPostingDomainService（Java-B 负责）

```java
@Service
@RequiredArgsConstructor
public class AsyncPostingDomainService {

    private final LocalMessageService localMessageService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 写入异步过账本地消息（与实时过账同事务）
     */
    public void writeAsyncPostingMessage(
        AccountingVoucherEntryPO entry,
        AccountingVoucherPO voucher);

    /**
     * MQ 消费端：执行异步过账（独立事务）
     *
     * 幂等：先检查分录 status 是否已是 2，是则直接返回
     */
    public void consumeAsyncPostingMessage(PostingMessagePayload payload);

    /**
     * 检查凭证所有非缓冲分录（含异步+实时）是否都已过账
     */
    public boolean areAllNonBufferEntriesPosted(String voucherNo);

    /**
     * 联动更新凭证状态和事务状态（MQ 消费端调用）
     * 当所有非缓冲分录都过账成功后：
     *   - 凭证 status=3(已过账)
     *   - 事务 status=2(成功)
     */
    public void updateStatusIfAllNonBufferPosted(String voucherNo);
}
```

### 7.3 RollbackDomainService（Java-B 负责）

```java
@Service
@RequiredArgsConstructor
public class RollbackDomainService {

    private final TransactionTemplate transactionTemplate;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 实时过账失败回滚（事务外调用，使用独立事务更新状态）
     *
     * 更新事务状态 + 流水状态 + 凭证状态 + 分录状态
     */
    public void markTransactionFailed(
        String txnNo,
        String voucherNo,
        String traceNo,
        String failReason);

    /**
     * 单边记账回滚（异步分录失败，实时分录已提交）
     *
     * 生成反向凭证（REV + 原凭证号），对已过账分录执行反向操作。
     * 使用新 entryId 避免 uk_voucher_no 唯一约束冲突（P1-1 修复）。
     */
    public void executeRollbackForAsyncFailure(
        String voucherNo,
        String txnNo,
        String failReason);

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable(Throwable ex);
}
```

### 7.4 PostingApplicationService（Java-C 负责）

```java
@Service
@RequiredArgsConstructor
public class PostingApplicationService {

    private final PostingDomainService postingDomainService;
    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLockTemplate distributedLockTemplate;
    private final PostingAssembler assembler;
}
```

---

## 8. 领域数据对象

### 8.1 PostingEntryData（过账分录数据）

```java
@Data
@AllArgsConstructor
public class PostingEntryData {
    private String entryId;
    private String voucherNo;
    private String subjectCode;
    private String accountNo;
    private Integer debitCredit;      // 1=借, 2=贷
    private Integer changeDirection;  // 1=增, 2=减（P1-1 修复：与 DDL change_direction 列一致）
    private BigDecimal amount;
    private String currency;
    private String summary;
    private LocalDate accountingDate;
    private Integer unilateral;       // 0=异步/缓冲, 1=实时过账（与 DDL TINYINT 对齐，P2-1 修复）
    private Integer buffered;         // 0=非缓冲, 1=缓冲入账（与 DDL TINYINT 对齐，P2-1 修复）
    private Integer status;           // 1=未过账, 2=已过账, 3=过账失败
}
```

### 8.2 AccountBalanceChange（账户余额变更）

```java
@Data
@AllArgsConstructor
public class AccountBalanceChange {
    private String accountNo;
    private BigDecimal preBalance;
    private BigDecimal amount;
    private BigDecimal postBalance;
    private Integer debitCredit;       // 1=借, 2=贷
    private Integer changeDirection;   // 1=增, 2=减
}
```

### 8.3 PostingMessagePayload（MQ 消息体，Java-B）

```java
@Data
@AllArgsConstructor
public class PostingMessagePayload {
    private String voucherNo;
    private String entryId;
    private String accountNo;
    private String subjectCode;
    private Integer debitCredit;
    private Integer changeDirection;   // 1=增, 2=减（P2-7 补充：消费端余额计算需要）
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String currency;
    private String summary;
}
```

---

## 9. 余额计算器设计

```java
/**
 * 无状态工具类，提供余额计算的纯函数。
 * 不依赖任何 Spring Bean，不包含任何副作用。
 * 注意：此类使用基本数据类型参数（int），因其为纯计算工具类，不涉及持久化或序列化，
 * 豁免 java.md "禁止基本数据类型" 规范（P2-2 说明）。
 */
public final class AccountBalanceCalculator {

    private AccountBalanceCalculator() {}

    /**
     * 计算新余额
     *
     * @param currentBalance  当前余额（始终 >= 0）
     * @param amount          变更金额（始终 > 0）
     * @param changeDirection 增减方向（1=增, 2=减）
     * @return 新余额
     * @throws AccountException 当余额不足时抛出
     *
     * 说明：changeDirection 由 Step 10 凭证生成时推导并写入分录行 DDL 列，
     * 过账阶段直接读取使用，无需重新推导科目性质（P1-1 修复）。
     */
    public static BigDecimal calculateNewBalance(
        BigDecimal currentBalance,
        BigDecimal amount,
        int changeDirection) {

        if (changeDirection == 1) {
            // 余额增加：同向相加
            return currentBalance.add(amount);
        } else {
            // 余额减少：反向相减，前置校验余额充足
            if (currentBalance.compareTo(amount) < 0) {
                throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                    "余额不足: current=" + currentBalance + ", need=" + amount);
            }
            return currentBalance.subtract(amount);
        }
    }
}
```

---

## 10. 编码要点

### 10.1 事务边界

- **严禁使用 `@Transactional`**，必须显式使用 `TransactionTemplate`
- 实时过账事务边界：从"按 account_no 升序加锁"开始，到"更新凭证/事务状态"结束
- 异步过账消费是独立事务（消费端独立 TransactionTemplate）
- 本地消息写入与实时过账在同一事务中完成
- 回滚时的状态更新（markTransactionFailed）通过独立 TransactionTemplate 执行，不在已回滚的事务内

### 10.2 分布式锁

```
锁 Key：accounting:{tenantId}:lock:posting:trx:{voucherNo}
等待时间：0s（立即失败）
过期时间：60s（过账可能涉及多账户操作，比入口幂等锁长）

加锁失败 → 抛出 ServiceException(IDEMPOTENT_CONFLICT)

（P2-3 优化建议：可在加锁失败前增加 1-2 次 100ms 快速重试，非强制）
```

### 10.3 金额与精度

- 金额必须使用 `BigDecimal`，String 构造
- 比较使用 `compareTo()`，禁止 `equals()`
- 严禁负数运算（财务律法）
- 余额计算使用 `AccountBalanceCalculator`，禁止在业务代码中直接写 `add`/`subtract`

### 10.4 异常体系

| 场景 | 异常类型 | ResultCode | 说明 |
|------|---------|-----------|------|
| 凭证不存在 | `ServiceException` | VOUCHER_NOT_FOUND | 通用业务异常 |
| 凭证状态非法 | `ServiceException` | POSTING_STATUS_INVALID | 凭证已过账或已冲销 |
| 余额不足 | `AccountException` | INSUFFICIENT_BALANCE | 账务专项异常 |
| 账户状态异常 | `AccountException` | ACCOUNT_FROZEN / ACCOUNT_CANCELLED | 账务专项异常 |
| 账户不存在 | `AccountException` | ACCOUNT_NOT_FOUND | 账务专项异常 |
| 过账失败 | `AccountException` | POSTING_FAILED | 账务专项异常 |
| 幂等冲突 | `ServiceException` | IDEMPOTENT_CONFLICT | 通用并发异常 |

### 10.5 重试机制

```java
// 临时性异常重试策略
private static final List<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS = List.of(
    PessimisticLockingFailureException.class,  // 锁冲突
    TimeoutException.class,                     // 超时
    TransientDataAccessResourceException.class  // 临时数据库异常
);

private static final long[] RETRY_DELAYS = {10_000, 30_000, 60_000}; // 10s, 30s, 60s

public boolean isRetryable(Throwable ex) {
    return RETRYABLE_EXCEPTIONS.stream().anyMatch(c -> c.isInstance(ex));
}
```

### 10.6 与 Step 10 的衔接

```
Step 10 输出：voucherNo + 分录已持久化（is_unilateral / is_buffered 列已写入）+ txnNo
Step 11 输入：voucherNo → 查询凭证 + 分录（从 DDL 列获取分流标记）→ 按标记分流过账

Step 10 的 VoucheringApplicationService 可选择同步或异步触发过账：
  - 同步：generateVoucher() 最后调用 PostingApplicationService.executePosting()
  - 异步：生成凭证后返回，由业务系统显式调用过账接口
```

### 10.7 与 Step 12+ 的衔接

```
Step 11 输出：已过账的账户余额 + 明细快照 + 事务状态
Step 12（过账引擎）：当前 Step 11 已覆盖过账核心逻辑，Step 12 侧重
  - 过账引擎独立调度能力
  - 批量过账处理
  - 过账进度监控
Step 13（账户状态管理）：基于 Step 11 过账后的余额，执行冻结/解冻/注销
Step 16（缓冲记账）：处理 Step 11 跳过的 is_buffered=1 分录
Step 17（EOD）：基于 Step 11 产生的明细快照，执行日切试算平衡
Step 18（红冲）：基于 Step 11 已过账的凭证，执行反向过账
```

---

## 11. 完成标准（Checklist）

> **对齐日期**：2026-06-24，代码与文档逐项核对后打勾。

### Java-A
- [x] P0-1: `TransactionMapper` 补充 `updateStatusByTxnNo`、`selectByTxnNo`
- [x] P0-2: `TransactionRepository` 补充 `updateStatusByTxnNo`、`selectByTxnNo`
- [x] P0-3: `AccountingVoucherMapper` 补充 `updateStatusByVoucherNo`、`selectByVoucherNo`
- [x] P0-4: `AccountingVoucherEntryMapper` 补充 `selectByVoucherNoWithStatus`
- [x] P0-6: 确认 DDL 中 `is_unilateral` + `is_buffered` 列已补充
- [x] P0-7: `AccountingVoucherEntryPO` 补充 `unilateral` + `buffered` + `changeDirection` 属性
- [x] `AccountMapper.selectForUpdate` + XML（SELECT FOR UPDATE，按 account_no 列表）
- [x] `SubAccountMapper.selectForUpdate` + XML
- [x] `AccountDetailRepository` 新建 + `batchInsert`
- [x] `SubAccountDetailRepository` 新建 + `batchInsert`
- [x] `AccountBalanceCalculator` 无状态工具类（纯函数，无副作用，使用 int 参数豁免 POJO 规范）
- [x] `PostingDomainService` 实时过账领域服务（独立类，**不含事务**）
- [x] 按 account_no 升序 SELECT FOR UPDATE 加锁
- [x] 余额计算（change_direction 增减判断，前置校验余额充足）
- [x] 更新 t_account.balance + t_sub_account.balance
- [x] 写入 t_account_detail（含 Pre/Post 余额快照）
- [x] 写入 t_sub_account_detail
- [x] 更新分录 status=2(已过账)
- [x] `areAllRealTimeEntriesPosted` 方法
- [x] 单测：PostingDomainServiceTest（加锁/余额计算/账户状态校验/明细写入）+ AccountBalanceCalculatorTest（增减/精度/边界）

### Java-B
- [x] `AsyncPostingDomainService` 异步过账领域服务
- [x] 本地消息写入（`t_local_message`，与实时过账同事务）
- [x] MQ 消费端过账逻辑（独立事务，幂等检查：分录 status 已是 2 则直接返回）
- [x] 消费端：按 account_no 升序加锁 → 余额计算 → 更新余额 → 记录明细
- [x] 消费端：更新分录 status + 联动更新凭证状态 + 事务状态
- [x] 消费端：更新 t_local_message.status=2 + 写入 t_message_receipt
- [x] 消费失败重试（3 次指数退避）+ 标记失败 + 告警
- [x] `RollbackDomainService` 回滚领域服务
- [x] 实时回滚：markTransactionFailed（通过独立事务更新事务/流水/凭证/分录状态）
- [x] 单边回滚：executeRollbackForAsyncFailure（生成反向凭证号 REV+原号，新 entryId 避免唯一约束冲突）
- [x] `isRetryable` 方法（区分临时/永久异常）
- [x] `PostingMessagePayload` 消息体
- [x] 单测：MQ 消费幂等、消费失败重试、单边回滚（RollbackDomainService 已在 ReversalDomainServiceTest 覆盖）

### Java-C
- [x] `PostingApplicationService` 过账编排用例（**统一事务边界**）
- [x] 分布式锁控制（wait=0s，lease=60s，tenantId 从 TenantContextHolder 获取）
- [x] 凭证状态校验（status=1 未过账）
- [x] 过账前账户状态检查（NORMAL=1）
- [x] 分录分流逻辑（is_unilateral=1 实时 / is_buffered=0 异步 / is_buffered=1 缓冲）
- [x] 凭证状态联动更新（全实时 → status=3；含异步 → 保持 status=2）
- [x] 事务状态更新（全实时 → status=2成功 + finishTime；含异步 → 保持 status=1处理中）
- [x] `PostingController` 实现 3 个接口
- [x] `PostingExecuteRequest` / `PostingExecuteResponse` / `PostingEntryResponse` DTO
- [x] DTO 使用 `jakarta.validation` 包
- [x] `PostingAssembler` 完成 PO ↔ DTO 转换
- [x] 参数校验（voucherNo 必填）
- [x] 凭证不存在 / 状态非法返回明确错误
- [x] 余额不足返回明确错误（INSUFFICIENT_BALANCE）
- [x] 重试机制（临时性异常 3 次指数退避）
- [x] 单测：正常过账全流程、凭证不存在、状态非法（PostingDomainServiceTest 覆盖）

### TL Review
- [x] 实时过账按 account_no 升序 SELECT FOR UPDATE（防死锁）
- [x] 余额计算使用 AccountBalanceCalculator（纯函数，无副作用）
- [x] 严禁负数运算、严禁 SQL 计算余额（财务律法）
- [x] 事务边界统一在 Application Service（领域服务不开启事务）
- [x] 严禁 `@Transactional`，全部使用 `TransactionTemplate`
- [x] 本地消息与实时过账在同一事务中
- [x] MQ 消费端幂等（检查分录 status 防重复）
- [x] 消费失败重试策略正确（3 次指数退避）
- [x] 单边回滚使用新反向凭证号 + 新 entryId（P1-1 修复，避免唯一约束冲突）
- [x] 事务状态时序正确：含异步分录时保持 PROCESSING，等 MQ 完成后转 SUCCESS（P1-2/P1-5 修复）
- [x] 过账前有账户状态检查（P1-3 修复）
- [x] 回滚时更新 t_business_record.status（P1-4 修复）
- [x] 异常类型区分正确（AccountException vs ServiceException）
- [x] 分布式锁 Key 含 tenantId（租户隔离），从 TenantContextHolder 获取（P1-7 修复）
- [x] 凭证状态联动正确（全实时 → 3，含异步 → 保持 2，MQ 全部完成 → 3）
- [x] 事务状态更新完整（全实时 → 2+finishTime，含异步 → 保持 1）
- [x] 与 Step 10 衔接正确（voucherNo 入口，txnNo 关联，分流标记来自 DDL 列）
- [x] 与 Step 16 衔接正确（is_buffered=1 分录跳过）

---

## 12. 下一步行动

进入 **Step 12 · Posting Engine（过账引擎）**，详见 `docs/prompt/step-12-posting.md`。
