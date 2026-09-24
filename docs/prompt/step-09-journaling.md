# step-09-journaling · 业务流水入库

> **Phase 4 第二步（⚠️ 最高风险模块）** | 归属：`@Java` 工程师
> 前置依赖：Step 8（自动化开户引擎已完成）、Step 4（RocketMQ 封装 + 分布式锁模板 + 字典缓存）、Step 5（领域 Repository 已就绪）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `BusinessRecordMapper` 按 traceNo 查重方法 | `BusinessRecordMapper.java`（已有，需补充方法）+ `BusinessRecordRepository` 新建 | 幂等校验：`selectByTraceNo(traceNo, traceSeq)` |
| P0-2 | `TransactionMapper` 按 traceNo 查询方法 | `TransactionMapper.java`（已有，需补充方法）+ `TransactionRepository` 新建 | 事务关联：`selectByTraceNo(traceNo)` |
| P0-3 | 确认 `TransactionConfig` 已存在 | `TransactionConfig.java`（Step 8 P0-4 已创建） | 复用 TransactionTemplate Bean |
| P0-4 | 修复 `AccountingRuleMapper.xml` selectByBusinessKey | `AccountingRuleMapper.xml` | 补充 `AND status = 2` 过滤，仅匹配已启用规则（S4 修复） |

> 上述补充任务由 Java-A 在开始编码前一并完成，不单独拆分子任务文件。

---

## 1. 任务目标（Mission）

实现业务流水入库引擎，作为记账核心引擎的**入口环节**，承接 Step 8 的开户能力，为 Step 10 凭证生成引擎提供数据基础。

核心职责：
1. **幂等校验**：基于 `trace_no + trace_seq` 唯一键，防止重复请求
2. **流水持久化**：将业务记账请求写入 `t_business_record` + `t_business_detail`，并确定会计日期
3. **事务创建**：在流水持久化后创建 `t_transaction` 记录，为后续凭证生成和过账提供事务追踪
4. **预开户检查**：在流水入库阶段检查所需账户是否存在，不存在则触发 Step 8 的自动开户能力

**流水入库是记账流程的入口，幂等和会计日期确定在此环节完成，后续全链路使用此会计日期，不可变更。**

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、幂等设计、余额方向约束 |
| 3 | `docs/design/domain-model.md` | 流水域、凭证域、规则域模型 |
| 4 | `docs/sql/5-journal.sql` | `t_business_record` / `t_business_detail` / `t_transaction` DDL |
| 5 | `docs/sql/1-account.sql` | `t_account` / `t_account_template` DDL |
| 6 | `docs/sql/3-rule.sql` | `t_accounting_rule` / `t_accounting_rule_detail` DDL |
| 7 | `docs/design/flowchart/accounting_flow.mmd` | 入账流程总览 |
| 8 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（四阶段） |
| 9 | `docs/design/flowchart/auto_account_opening_flow.mmd` | 记账自动开户流程图 |
| 10 | `docs/design/flowchart/transaction_rollback_flow.mmd` | 事务回滚流程图 |
| 11 | `accounting-core/.../entity/BusinessRecordPO.java` | 业务流水 PO |
| 12 | `accounting-core/.../entity/BusinessDetailPO.java` | 流水明细 PO |
| 13 | `accounting-core/.../entity/TransactionPO.java` | 事务 PO |
| 14 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 15 | `accounting-core/.../entity/AccountTemplatePO.java` | 开户模板 PO |
| 16 | `accounting-core/.../domain/service/AccountOpeningDomainService.java` | Step 8 开户领域服务 |
| 17 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 18 | `accounting-core/.../repository/AccountRepository.java` | 账户仓储 |
| 19 | `accounting-core/.../repository/AccountingRuleRepository.java` | 记账规则仓储（已有 `selectByBusinessKey` + `selectDetailsWithAuxiliary`） |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-09-java-a.md` | P0 补充 + 幂等校验 + 流水持久化 + 会计日期确定 + 事务编号生成 |
| Java-B | `docs/prompt/tasks/step-09-java-b.md` | 预开户检查领域服务（独立类）+ 账户列表解析 + 集成 Step 8 |
| Java-C | `docs/prompt/tasks/step-09-java-c.md` | Journaling Application Service + Controller + 入口幂等锁 |

> **依赖关系**：Java-A 最先完成（Repository 补充和流水持久化是基础设施）。Java-B 依赖 Java-A 的流水持久化结果和 Step 8 的开户能力。Java-C 依赖 Java-A/B 的领域服务，做上层编排和幂等锁控制。建议串行执行：A → B → C。
>
> **职责隔离（S1 修复）**：`JournalingDomainService`（Java-A）和 `AccountPreCheckDomainService`（Java-B）是**两个独立的领域服务类**，避免多人修改同一文件产生合并冲突。

---

## 4. 核心业务规则

### 4.1 记账请求入口流程

```
业务系统调用 POST /accounting/journal/submit
  ↓
【幂等层】获取分布式锁 accounting:{tenantId}:lock:idempotent:trace:{traceNo}-{traceSeq}
  ↓ 加锁失败 → 返回 IDEMPOTENT_CONFLICT（请求已处理）
  ↓ 加锁成功
【幂等层】查询 t_business_record WHERE trace_no=? AND trace_seq=?
  ↓ 已存在 → 幂等返回已有结果
  ↓ 不存在
【流水层】校验请求参数合法性
  ↓
【流水层】确定会计日期 accounting_date（当前阶段：trade_time 所在日期）
  ↓
【流水层】写入 t_business_record（status=PROCESSING） + t_business_detail（逐条）
  ↓
【开户层】调用 AccountPreCheckDomainService 解析规则 + 预开户检查
  ↓ 账户不存在 且模板 auto_open=1
     → 调用 AccountOpeningDomainService 自动开户
  ↓ 账户不存在 且无模板/模板不自动 → 更新流水 status=FAILED，阻断
  ↓
【事务层】创建 t_transaction（status=PROCESSING，txn_no 已生成）
  ↓
【返回】记账流水已入库，trace_no + accounting_date + txn_no，等待 Step 10 凭证生成
```

### 4.2 会计日期确定规则

```
默认规则：accounting_date = trade_time 的日期部分
日切后规则（Step 17 实现）：
  - 若当前时间 > 日切时间点（由配置决定），会计日期 = 当前日期 + 1（T+1）
  - 否则 accounting_date = 当前日期

会计日期一旦写入 t_business_record.accounting_date，全链路不可变更。
Step 17（EOD）会按 accounting_date 分区处理缓冲记录。
```

### 4.3 幂等设计

| 层次 | 实现方式 | 防护目标 |
|------|----------|----------|
| 入口幂等 | 分布式锁 + `trace_no + trace_seq` 唯一约束 | 防重复请求 |

```
锁 Key：accounting:{tenantId}:lock:idempotent:trace:{traceNo}-{traceSeq}
等待时间：0s（立即失败，不做等待）
过期时间：30s（防止死锁）
```

### 4.4 预开户检查

```
在流水入库阶段，通过 AccountPreCheckDomainService（独立领域服务）执行：
  1. 根据 business_code + trading_code + pay_channel 匹配记账规则（status=2 已启用）
  2. 解析规则明细，获取所有需要使用的 subject_code
  3. 结合 customer_id + customer_type 构建待检查账户列表（customerId + customerType 在构建时绑定）
  4. 逐账户检查是否存在
     → 不存在则触发 Step 8 的 AccountOpeningDomainService 自动开户
  5. 返回账户编号列表（按 account_no 升序排列）

⚠️ 开户失败必须更新流水 status=FAILED 并阻断后续流程
```

---

## 5. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── JournalSubmitRequest.java            # 记账请求入参 DTO
    │   └── JournalDetailRequest.java            # 流水明细入参 DTO
    └── response/
        └── JournalSubmitResponse.java           # 记账结果响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── JournalingDomainService.java     # 流水入库领域服务（Java-A）
    │       └── AccountPreCheckDomainService.java # 预开户检查领域服务（Java-B，独立类）
    ├── application/
    │   ├── service/
    │   │   └── JournalingApplicationService.java  # 流水入库应用服务
    │   └── assembler/
    │       └── JournalingAssembler.java         # PO ↔ Request/Response 转换
    ├── infrastructure/
    │   └── account/
    │       └── TransactionNoGenerator.java      # 事务编号生成器
    └── controller/
        └── JournalingController.java            # 流水入库 Controller
```

**需要修改/补充的 Repository**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `BusinessRecordMapper.java` | 补充方法 | 已有空壳，追加 `selectByTraceNo` |
| `TransactionMapper.java` | 补充方法 | 已有空壳，追加 `selectByTraceNo` |
| `BusinessRecordRepository.java` | **新建** | 封装流水持久化操作 |
| `BusinessDetailRepository.java` | **新建** | 封装流水明细持久化操作（M1/S3 修复） |
| `TransactionRepository.java` | **新建** | 封装事务持久化操作 |
| `AccountingRuleMapper.xml` | 修改 | 补充 `AND status = 2` 过滤（S4 修复） |

---

## 6. 接口契约

所有接口路径前缀：`/accounting/journal`

### 6.1 POST `/accounting/journal/submit` — 提交记账流水

**请求体** (`JournalSubmitRequest`):
```json
{
  "traceNo": "TRC20260512000001",
  "traceSeq": 0,
  "businessCode": "LOAN",
  "tradingCode": "DISBURSE",
  "payChannel": "BANK_TRANSFER",
  "tradeType": 1,
  "amount": 10000.00,
  "tradeTime": "2026-05-12T10:30:00",
  "summary": "贷款发放",
  "details": [
    {
      "customerId": "CUST001",
      "customerType": 2,
      "fundsType": "LOAN_PRINCIPAL",
      "itemCode": "PRINCIPAL",
      "amount": 10000.00
    }
  ]
}
```

**校验规则**：
- `traceNo`：必填，长度 1-64，系统跟踪号
- `traceSeq`：非必填，默认 0，预留幂等序列号
- `businessCode`：必填，长度 1-32
- `tradingCode`：必填，长度 1-32
- `payChannel`：必填，长度 1-32
- `tradeType`：必填，1=正常，2=调账，3=红，4=蓝
- `amount`：必填，> 0（严禁负数）
- `tradeTime`：必填
- `summary`：可选，长度 0-64
- `details`：非空数组，每个元素包含客户维度的款项明细（N2 修复：含 `itemCode`）

**业务逻辑**：
1. 获取幂等锁 `accounting:{tenantId}:lock:idempotent:trace:{traceNo}-{traceSeq}`
2. 幂等检查：查询 `t_business_record` 是否已存在，存在则返回已有结果
3. 确定会计日期（`accounting_date`）
4. 在事务中写入 `t_business_record`（status=PROCESSING） + `t_business_detail`（逐条，含 itemCode）
5. 预开户检查：通过 `AccountPreCheckDomainService` 解析规则 → 获取所需账户列表 → 逐账户检查 → 不存在则自动开户
6. 生成事务编号并通过 `TransactionNoGenerator` 创建 `t_transaction`（status=PROCESSING）
7. 释放幂等锁
8. 返回 `JournalSubmitResponse`（含 traceNo、accountingDate、txnNo、是否需要凭证生成）

### 6.2 GET `/accounting/journal/{traceNo}` — 查询流水状态

**响应**：流水基本信息 + 明细列表 + 关联事务状态 + 关联凭证状态

### 6.3 GET `/accounting/journal/trace/{traceNo}/transaction` — 查询关联事务

**响应**：事务基本信息（txnNo、status、accountingDate 等）

---

## 7. 领域服务设计（S1 修复：拆分为两个独立类）

### 7.1 JournalingDomainService（Java-A 负责）

```java
@Service
@RequiredArgsConstructor
public class JournalingDomainService {

    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessDetailRepository businessDetailRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionNoGenerator transactionNoGenerator;
    private final TransactionTemplate transactionTemplate;

    /**
     * 幂等检查：按 traceNo + traceSeq 查询已存在的流水
     */
    public BusinessRecordPO checkIdempotent(String traceNo, Integer traceSeq);

    /**
     * 确定会计日期
     * 当前阶段：直接使用 tradeTime.toLocalDate()
     */
    public LocalDate determineAccountingDate(LocalDateTime tradeTime);

    /**
     * 在事务中写入流水 + 明细 + 创建事务记录
     * 返回 JournalSubmitResult（领域层结果对象）
     */
    public JournalSubmitResult persistJournal(
        String traceNo, Integer traceSeq, String businessCode,
        String tradingCode, String payChannel, Integer tradeType,
        BigDecimal amount, LocalDateTime tradeTime, String summary,
        List<JournalDetailRequest> details, LocalDate accountingDate);
}
```

**persistJournal 执行流程**：
1. 生成 txnNo（通过 `TransactionNoGenerator`）
2. 在 `TransactionTemplate` 事务中：
   a. 构建 `BusinessRecordPO`（status=PROCESSING，accountingDate）
   b. `businessRecordRepository.save(record)`
   c. 构建 `BusinessDetailPO` 列表，逐条 `businessDetailRepository.save(detail)`
   d. 构建 `TransactionPO`（txnNo、traceNo、accountingDate、amount、status=PROCESSING）
   e. `transactionRepository.save(transaction)`
3. 返回 `JournalSubmitResult`

### 7.2 AccountPreCheckDomainService（Java-B 负责，独立类）

```java
@Service
@RequiredArgsConstructor
public class AccountPreCheckDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountRepository accountRepository;
    private final AccountOpeningDomainService accountOpeningDomainService;
}
```

```java
/**
 * 预开户检查：根据业务参数解析所需账户，不存在则自动开户
 *
 * 执行流程：
 *   1. 根据 businessCode + tradingCode + payChannel 查询记账规则（已有方法 selectByBusinessKey）
 *      → 规则不存在 → 抛出 AccountException(RULE_NOT_FOUND)
 *      → 规则 status != ENABLED → 抛出 AccountException(RULE_DISABLED)
 *   2. 解析规则明细（已有方法 selectDetailsWithAuxiliary），提取所有 subject_code
 *   3. 构建待检查账户列表（customerId + customerType 在构建时绑定，M3 修复）
 *   4. 逐账户检查是否存在
 *      → 不存在 → 调用 AccountOpeningDomainService.openExternalAccount
 *      → 开户失败 → 抛出 AccountException(SUB_ACCOUNT_CREATE_FAILED)
 *   5. 对已有账户查询 account_no
 *   6. 所有 account_no 升序排列后返回（M4 修复：排的是真实 account_no）
 *
 * @param businessCode 业务线编码
 * @param tradingCode  交易编码
 * @param payChannel   支付渠道
 * @param customerMap  客户ID → (customerType, customerId) 映射
 * @return 账户编号列表（已按 account_no 升序排列）
 */
public List<String> checkAndOpenAccounts(
    String businessCode, String tradingCode, String payChannel,
    Map<String, CustomerTypeEnum> customerMap);
```

---

## 8. JournalSubmitResult 领域结果对象（S2 修复）

```java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

/**
 * 流水入库领域层结果对象
 * Application Service 通过 Assembler 将此转换为 JournalSubmitResponse DTO
 */
@Data
@AllArgsConstructor
public class JournalSubmitResult {

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 事务编号 */
    private String txnNo;
}
```

---

## 9. TransactionNoGenerator 事务编号生成器（M2 修复）

```java
@Component
@RequiredArgsConstructor
public class TransactionNoGenerator {

    private final RedissonClient redissonClient;

    /**
     * 事务编号生成
     * 格式：TXN + yyyyMMdd + seq6，如 TXN20260512000001
     * Redis key：txn:seq:{yyyyMMdd}
     * TTL：25 小时（每日自动重置）
     * 序号从 1 开始，格式化为 6 位数字
     */
    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "txn:seq:" + date;
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        if (atomicLong.isNotExists()) {
            atomicLong.set(0);
            atomicLong.expire(25, TimeUnit.HOURS);
        }
        long seq = atomicLong.incrementAndGet();
        return "TXN" + date + String.format("%06d", seq);
    }
}
```

> 与 Step 8 的 `AccountNoGenerator` 保持一致的设计风格。

---

## 10. 编码要点

### 10.1 事务边界

- 流水入库（`t_business_record` + `t_business_detail` + `t_transaction`）必须在同一事务中完成
- 使用 `TransactionTemplate` 管理事务，严禁 `@Transactional`
- 预开户检查中的每个账户开户使用独立事务（通过 `DistributedLockTemplate.execute()` 内部事务）
- 幂等锁的获取和释放在事务之外

### 10.2 幂等锁时序

```
获取幂等锁 → 查询幂等（锁内） → 不存在 → 执行业务 → 释放锁
                                         ↓ 已存在 → 直接返回（锁内）
```

- 锁等待时间：0s（立即失败）
- 锁过期时间：30s
- 加锁失败抛 `ServiceException(ResultCode.IDEMPOTENT_CONFLICT)`

### 10.3 会计日期确定

当前阶段会计日期直接使用 `tradeTime.toLocalDate()`。日切逻辑在 Step 17 实现，当前阶段预留接口方法 `determineAccountingDate()`，内部直接返回 `tradeTime.toLocalDate()` 即可。

### 10.4 金额校验

- `amount > 0`，严禁负数（财务律法）
- 流水明细金额合计必须等于流水总金额
- 使用 `BigDecimal`，禁止 `new BigDecimal(double)`，禁止 `equals()` 比较

### 10.5 异常体系（N3 修复：明确边界）

| 场景 | 异常类型 | ResultCode | 说明 |
|------|---------|-----------|------|
| 幂等冲突 | `ServiceException` | IDEMPOTENT_CONFLICT | 通用并发/基础设施异常 |
| 参数校验失败 | `ParamException` | PARAM_ERROR | 通用参数校验异常 |
| 流水明细金额合计不等 | `ServiceException` | PARAM_ERROR | 参数校验下沉到 Application Service |
| 记账规则不存在 | `AccountException` | RULE_NOT_FOUND | 账务领域异常 |
| 记账规则已停用 | `AccountException` | RULE_DISABLED | 账务领域异常 |
| 开户模板不存在 | `AccountException` | ACCOUNT_TEMPLATE_NOT_FOUND | 账务领域异常 |
| 模板不支持自动开户 | `AccountException` | ACCOUNT_TEMPLATE_NOT_AUTO_OPEN | 账务领域异常 |
| 子账户创建失败 | `AccountException` | SUB_ACCOUNT_CREATE_FAILED | 账务领域异常 |

**区分原则**：`AccountException` 用于账务领域规则违反（科目/规则/账户/开户等）；`ServiceException` 用于通用业务异常（金额合计不等）；`ParamException` 用于入口参数格式校验。

### 10.6 流水 FAILED 状态更新（N4 修复）

预开户检查失败（开户失败）时，`AccountPreCheckDomainService` 抛出 `AccountException`。`JournalingApplicationService.submitJournal()` 在 `catch (AccountException e)` 块中：

```java
catch (AccountException e) {
    // 更新流水状态为 FAILED
    businessRecordRepository.updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
    throw e; // 继续向上抛出，由 GlobalExceptionHandler 处理
}
```

对应 `BusinessRecordRepository` 需补充 `updateStatusByTraceNo` 方法。

### 10.7 与 Step 8 的集成

Step 9 是 Step 8 的**第一个调用方**。集成点：

```
JournalingApplicationService.submitJournal()
  → JournalingDomainService.persistJournal()       // 流水入库
  → AccountPreCheckDomainService.checkAndOpenAccounts()  // 预开户检查
    → AccountingRuleRepository.selectByBusinessKey()     // 复用已有方法
    → AccountingRuleRepository.selectDetailsWithAuxiliary() // 复用已有方法
    → AccountOpeningDomainService.openExternalAccount()  // Step 8 开户能力
```

其中开户 `requestNo` 使用 `traceNo` 作为开户请求号。

### 10.8 与 Step 10 的衔接

Step 9 完成后，流水已入库、事务已创建、账户已就绪。Step 10 凭证生成引擎将以 `traceNo` 为入口，读取流水数据并生成凭证。

```
Step 9 输出：traceNo + accountingDate + txnNo + accountNoList
Step 10 输入：traceNo → 查询流水 → 匹配规则 → 生成凭证
```

---

## 11. 完成标准（Checklist）

### Java-A
- [ ] P0-1: `BusinessRecordMapper` 补充 `selectByTraceNo` + `BusinessRecordRepository` 新建
- [ ] P0-2: `TransactionMapper` 补充 `selectByTraceNo` + `TransactionRepository` 新建
- [ ] P0-3: 确认 `TransactionConfig` 存在
- [ ] P0-4: `AccountingRuleMapper.xml` selectByBusinessKey 补充 `AND status = 2`
- [ ] `BusinessDetailRepository` 新建 + `save` 方法（S3/M1 修复）
- [ ] `JournalingDomainService` 流水入库全流程（独立类，无 checkAndOpenAccounts）
- [ ] 幂等检查（`trace_no + trace_seq` 唯一键查询）
- [ ] 会计日期确定（`determineAccountingDate` 方法）
- [ ] `t_business_record` + `t_business_detail` 事务写入
- [ ] `TransactionNoGenerator` 事务编号生成（Redis 序号，每日重置，M2 修复）
- [ ] 流水 FAILED 状态更新方法 `updateStatusByTraceNo`

### Java-B
- [ ] `AccountPreCheckDomainService` 独立领域服务（S1 修复）
- [ ] 记账规则解析（复用已有 `selectByBusinessKey`，含 status=2 校验，N1/S4 修复）
- [ ] 规则明细解析（复用已有 `selectDetailsWithAuxiliary`，N1 修复）
- [ ] 账户列表构建时绑定 customerId + customerType（M3 修复）
- [ ] 逐账户检查 + 不存在时自动开户（集成 Step 8）
- [ ] 返回真实 account_no 升序列表（M4 修复）
- [ ] 开户失败抛出 AccountException 阻断流程

### Java-C
- [ ] `JournalingApplicationService` 编排流水提交用例
- [ ] `JournalingController` 实现 3 个接口
- [ ] `JournalSubmitRequest` / `JournalDetailRequest` / `JournalSubmitResponse` DTO
- [ ] `JournalDetailRequest` 含 `itemCode` 字段（N2 修复）
- [ ] DTO 使用 `jakarta.validation` 包（M6 修复）
- [ ] `JournalingAssembler` 完成 PO/Result ↔ DTO 转换（包路径 `application/assembler/`）
- [ ] 入口幂等锁（`DistributedLockTemplate`，wait=0s，lease=30s）
- [ ] 参数校验（金额 > 0、必填字段、明细金额合计 = 总金额）
- [ ] 幂等重复请求时查询已有事务并返回 txnNo（M5 修复）
- [ ] catch AccountException 时更新流水 status=FAILED（N4 修复）
- [ ] 单测全通，含正常流水、幂等重复、规则缺失、开户失败、金额不匹配场景

### TL Review
- [ ] 幂等锁正确（lockKey 含 tenantId + traceNo + traceSeq，wait=0，lease=30s）
- [ ] 流水入库事务完整性（record + detail + transaction 在同一事务）
- [ ] 会计日期正确确定且不可变更
- [ ] 预开户检查使用独立 AccountPreCheckDomainService（S1）
- [ ] AccountingRuleMapper.xml selectByBusinessKey 含 status=2 过滤（S4）
- [ ] 预开户检查返回真实 account_no 升序列表（M4）
- [ ] 金额计算无负数、使用 BigDecimal
- [ ] 异常类型区分正确（AccountException vs ServiceException vs ParamException，N3）
- [ ] 开户失败时更新流水 status=FAILED（N4）
- [ ] DTO 使用 jakarta.validation（M6）
- [ ] 领域服务加 @Service 注解，构造器注入，内部完成持久化
- [ ] 与 Step 8 集成点正确（AccountOpeningDomainService 调用方式）

---

## 12. 下一步行动

进入 **Step 10 · Vouchering（凭证生成引擎）**，详见 `docs/prompt/step-10-vouchering.md`。
