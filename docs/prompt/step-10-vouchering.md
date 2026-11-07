# step-10-vouchering · 凭证生成引擎

> **Phase 4 第三步（⚠️ 最高风险模块）** | 归属：`@Java` 工程师
> 前置依赖：Step 9（业务流水入库已完成）、Step 4（SpEL 表达式引擎就绪）、Step 5（领域 Repository 已就绪）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `AccountingVoucherMapper` 补充方法 | `AccountingVoucherMapper.java`（已有，需补充） | 补充 `updateTxnNoByVoucherNo`（已有 `selectByTraceNo`，无需追加） |
| P0-2 | `AccountingVoucherEntryMapper` 补充方法 | `AccountingVoucherEntryMapper.java`（已有，需补充） | 补充 `updateStatusByVoucherNo`（已有 `selectByVoucherNo`，无需追加） |
| P0-3 | `VoucherNoGenerator` 凭证号生成器 | 新建 `accounting-core/.../account/VoucherNoGenerator.java` | 格式：`VOU + yyyyMMdd + seq6`，Redis 每日重置，**Lua 脚本原子化**（复用 Step 9 P0-1 修复模式） |
| P0-4 | `EntryIdGenerator` 分录流水号生成器 | 新建 `accounting-core/.../account/EntryIdGenerator.java` | 格式：`ENT + yyyyMMddHHmmssSSS + seq4`，Redis 序号，**Lua 脚本原子化**（同 P0-3） |
| P0-5 | 确认 SpEL 表达式引擎可用 | 读取 `RuleApplicationService.java` 已有 `SPEL_PARSER` | 用于 `extend_script` 计算分录金额，抽取为 `RuleScriptExecutor` 组件 |

> **注意**：`AccountingVoucherRepository`（凭证仓储）已在 Step 6 创建，内含 `insert()`、`selectByTraceNo()`、`selectEntriesByVoucherNo()` 等方法，无需新建。`AccountingVoucherEntryRepository` 无需新建，直接使用 `AccountingVoucherRepository.insertEntry()` 即可。

---

## 1. 任务目标（Mission）

实现凭证生成引擎，作为记账核心引擎的**第二阶段**，承接 Step 9 的流水入库能力，将业务流水转换为标准会计凭证。

核心职责：
1. **规则匹配**：根据流水的业务参数（businessCode + tradingCode + payChannel）匹配记账规则
2. **SpEL 脚本计算**：执行规则明细中的 `extend_script`，计算每条分录的实际金额
3. **辅助核算分摊**：根据 `t_accounting_rule_auxiliary` 生成分摊后的辅助核算分录
4. **借贷平衡校验**：ΣDebit == ΣCredit 才允许生成凭证
5. **凭证持久化**：写入 `t_accounting_voucher` + `t_accounting_voucher_entry` + `t_accounting_voucher_auxiliary`
6. **缓冲规则匹配**：逐分录匹配缓冲规则，标记需异步入账的分录
7. **回填事务编号**：将 txnNo 回填到凭证记录

**凭证生成是记账流程的核心转换环节，将业务语言翻译为会计语言。借贷平衡校验在此环节完成，不通过则整个流程回滚。**

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、借贷方向约束、余额计算规则 |
| 3 | `docs/design/domain-model.md` | 凭证域、规则域、流水域模型 |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` / `t_accounting_voucher_auxiliary` DDL |
| 5 | `docs/sql/3-rule.sql` | `t_accounting_rule` / `t_accounting_rule_detail` / `t_accounting_rule_auxiliary` / `t_buffer_posting_rule` DDL |
| 6 | `docs/sql/5-journal.sql` | `t_business_record` / `t_business_detail` / `t_transaction` DDL |
| 7 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（四阶段） |
| 8 | `docs/design/flowchart/transaction_rollback_flow.mmd` | 事务回滚流程图 |
| 9 | `accounting-core/.../entity/AccountingVoucherPO.java` | 凭证 PO |
| 10 | `accounting-core/.../entity/AccountingVoucherEntryPO.java` | 分录 PO |
| 11 | `accounting-core/.../entity/AccountingVoucherAuxiliaryPO.java` | 辅助核算 PO |
| 12 | `accounting-core/.../domain/service/JournalingDomainService.java` | Step 9 流水入库领域服务 |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-10-java-a.md` | P0 补充 + VoucherNoGenerator/EntryIdGenerator + VoucheringDomainService（规则匹配 + SpEL计算 + 分录生成 + 借贷平衡校验） + 凭证持久化 |
| Java-B | `docs/prompt/tasks/step-10-java-b.md` | 辅助核算分摊引擎（按比例/固定金额/不分摊） + 缓冲规则匹配 + BufferPostingDomainService + 辅助核算项持久化 |
| Java-C | `docs/prompt/tasks/step-10-java-c.md` | VoucheringApplicationService（用例编排 + txnNo回填） + VoucheringController（3 个接口） + DTO + Assembler |

> **依赖关系**：Java-A 最先完成（凭证号生成器、分录生成、借贷平衡校验是基础设施）。Java-B 依赖 Java-A 生成的分录列表，做辅助核算分摊和缓冲匹配。Java-C 依赖 Java-A/B 的领域服务，做上层编排和 txnNo 回填。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 凭证生成入口流程

```
Step 9 已完成 → 流水入库 + 事务创建 + 账户就绪
  ↓
业务系统调用 POST /accounting/voucher/generate（或由 Step 9 自动触发）
  ↓
【凭证层】按 traceNo 查询 t_business_record + t_business_detail
  ↓ 流水不存在 → 抛出 ServiceException(JOURNAL_NOT_FOUND)
  ↓ 流水 status != PROCESSING → 抛出 ServiceException(JOURNAL_STATUS_INVALID)
  ↓
【规则层】按 businessCode + tradingCode + payChannel 查询记账规则（status=2）
  ↓ 规则不存在 → 抛出 AccountException(RULE_NOT_FOUND)
  ↓ 规则已停用 → 抛出 AccountException(RULE_DISABLED)
  ↓
【计算层】加载规则明细 + 辅助核算配置
  ↓ 逐规则明细行执行 SpEL 脚本，计算分录金额
  ↓ SpEL 执行异常 → 抛出 AccountException(SPEL_CALC_ERROR)
  ↓
【分摊层】逐分录匹配辅助核算项配置
  ↓ 按比例分摊：前 N-1 按比例计算，最后一条补差（防精度损失）
  ↓ 按固定金额分摊：直接取 allocation_value
  ↓ 不分摊：不生成辅助核算分录
  ↓
【校验层】借贷平衡校验：ΣDebit == ΣCredit ?
  ↓ 不平衡 → 抛出 AccountException(VOUCHER_NOT_BALANCED)
  ↓
【持久层】生成凭证号 → 写入 t_accounting_voucher（txn_no 为空）
  ↓ 生成分录流水号 → 写入 t_accounting_voucher_entry（含 is_unilateral + is_buffered 列）
  ↓ 写入 t_accounting_voucher_auxiliary（辅助核算项）
  ↓
【缓冲层】逐分录匹配缓冲规则
  ↓ 匹配到缓冲规则 → 写入 t_buffer_posting_detail（status=1 待入账）
  ↓ 未匹配 → 标记为正常记账流程
  ↓
【回填层】将 txnNo 回填到 t_accounting_voucher
  ↓
【返回】凭证已生成，voucherNo + 分录列表 + 缓冲标记
  ↓
流转至 Step 11（事务管理） → Step 12（过账引擎）
```

### 4.2 SpEL 脚本计算规则

```
t_accounting_rule_detail.extend_script 为 SpEL 表达式，用于计算分录金额。

示例场景：贷款发放
  业务明细：{ fundsType: "LOAN_PRINCIPAL", amount: 10000 }
  规则明细：
    row 1: subjectCode="1301", debitCredit=1(借), extendScript="#root.amount"  → 10000
    row 2: subjectCode="1001", debitCredit=2(贷), extendScript="#root.amount"  → 10000

SpEL 上下文变量（#root）：
  - amount: 业务明细金额（BigDecimal）
  - fundsType: 款项类型（String）
  - businessDetail: 完整业务明细对象（BusinessDetailPO）
  - accountingDate: 会计日期（LocalDate）

SpEL 脚本要求：
  - 返回值必须为 BigDecimal 类型（或可隐式转换为 BigDecimal 的数值）
  - 脚本在 Step 7 时已通过预加载校验，此处为运行时执行
  - 执行失败视为系统错误，阻断整个凭证生成流程
```

### 4.3 辅助核算分摊规则

```
t_accounting_rule_auxiliary 定义分录行的辅助核算分摊方式。

allocation_method = 1（不分摊）：
  不生成 t_accounting_voucher_auxiliary 记录

allocation_method = 2（固定金额）：
  auxAmount = allocation_value（直接取配置值）
  需要校验：auxAmount ≤ 分录总金额

allocation_method = 3（按比例）：
  auxAmount = 分录总金额 × (allocation_value / 100)
  前 N-1 条按比例计算（四舍五入到 6 位小数）
  最后一条补差：auxAmount_N = 分录总金额 - Σ(auxAmount_1..N-1)
  目的：防止浮点精度累积导致分摊金额不等于分录金额

change_direction 确定增减方向：
  与分录 debit_credit 一致：借方增加 / 贷方减少（视科目性质而定）
```

### 4.4 借贷平衡校验

```
Σ(所有分录行 where debitCredit=1 的 amount) == Σ(所有分录行 where debitCredit=2 的 amount)

校验失败时：
  1. 抛出 AccountException(VOUCHER_NOT_BALANCED)，携带 ΣDebit 和 ΣCredit 差值
  2. 不写入任何凭证数据（无脏数据）
  3. 上层 catch 后更新 t_transaction.status = FAILED

⚠️ 使用 BigDecimal.compareTo() 判断，禁止 equals()（精度不同会导致误判）
```

### 4.5 缓冲规则匹配

```
逐分录（t_accounting_voucher_entry）匹配 t_buffer_posting_rule：

匹配条件（AND）：
  1. businessCode + tradingCode + payChannel 与流水一致
  2. subject_code = 规则 subject_code（或规则 subject_code IS NULL / 空串时匹配任意）
  3. account_no = 规则 account_no（或规则 account_no IS NULL / 空串时匹配任意）
  4. debit_credit 一致
  5. accounting_date 在 [effective_time, expiration_time] 区间内

匹配结果：
  - 匹配到 → 写入 t_buffer_posting_detail（status=1 待入账，buffer_mode 取自规则）
  - 未匹配 → 该分录走正常过账流程（Step 12）

⚠️ 同一分录最多匹配一条缓冲规则（按 id 取第一条）
```

### 4.6 凭证号生成规则

```
格式：VOU + yyyyMMdd + seq6，如 VOU20260512000001

Redis key：vou:seq:{yyyyMMdd}
TTL：25 小时（每日自动重置）
序号从 1 开始，格式化为 6 位数字（%06d）

并发安全（P0-1 修复）：使用 Lua 脚本原子化初始化，消除 isNotExists→set→increment 竞态。
复用 Step 9 TransactionNoGenerator 的 Lua 脚本模式，详见子任务 java-a.md。
```

### 4.7 分录流水号生成规则

```
格式：ENT + yyyyMMddHHmmssSSS + seq4，如 ENT20260512103000001001

Redis key：ent:seq:{yyyyMMddHHmmssSSS}
TTL：2 小时（按秒级时间窗口重置）
序号从 1 开始，格式化为 4 位数字（%04d）

并发安全（P0-2 修复）：同 P0-1，使用 Lua 脚本原子化初始化。
同一凭证内多条分录依次生成不同 entryId，seq4 保证同一毫秒内不重复。
```

---

## 5. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── VoucherGenerateRequest.java        # 凭证生成请求 DTO
    └── response/
        └── VoucherGenerateResponse.java       # 凭证生成结果响应 DTO
            └── VoucherEntryResponse.java      # 分录行响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── VoucheringDomainService.java     # 凭证生成领域服务（Java-A）
    │       └── BufferPostingDomainService.java  # 缓冲规则匹配领域服务（Java-B）
    ├── application/
    │   ├── service/
    │   │   └── VoucheringApplicationService.java  # 凭证生成应用服务
    │   └── assembler/
    │       └── VoucheringAssembler.java         # PO ↔ Request/Response 转换
    ├── infrastructure/
    │   ├── account/
    │   │   ├── VoucherNoGenerator.java          # 凭证号生成器
    │   │   └── EntryIdGenerator.java            # 分录流水号生成器
    │   └── spel/
    │       └── RuleScriptExecutor.java          # SpEL 脚本执行器（如已有则复用）
    ├── infrastructure/persistence/
    │   └── mapper/
    │       ├── AccountingVoucherMapper.java         # 凭证 Mapper（已有，补充 updateTxnNoByVoucherNo）
    │       └── AccountingVoucherEntryMapper.java    # 分录 Mapper（已有，补充 updateStatusByVoucherNo）
    └── controller/
        └── VoucheringController.java            # 凭证生成 Controller
```

**需要修改/补充的 Mapper**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountingVoucherMapper.java` | 补充方法 | 已有空壳，追加 `updateTxnNoByVoucherNo` |
| `AccountingVoucherEntryMapper.java` | 补充方法 | 已有空壳，追加 `updateStatusByVoucherNo` |
| `AccountingVoucherAuxiliaryMapper.java` | 补充方法 | 已有空壳，追加 `selectByEntryId`（已有）+ 确认 insert 可用（BaseMapper） |
| `BufferPostingRuleMapper.java` | 补充方法 | 已有空壳，追加 `selectMatchingRules` |
| `BufferPostingDetailMapper.java` | 补充方法 | 已有空壳，追加 `selectBySharding`（已有）+ 确认 insert 可用（BaseMapper） |

**已有 Repository（无需新建，直接复用）**：

| 文件 | 可用方法 |
|------|----------|
| `AccountingVoucherRepository` | `insert()`、`selectByTraceNo()`、`selectEntriesByVoucherNo()`、`insertEntry()`、`insertAuxiliary()`、`updateById()` |
| `AccountingRuleRepository` | `selectByBusinessKey()`、`selectDetailsWithAuxiliary()`、`selectAuxiliariesByRuleId()` |
| `BusinessRecordRepository` | `selectByTraceNo()` |
| `BusinessDetailRepository` | `selectByTraceNo()` |
| `TransactionRepository` | `selectByTraceNo()` |

---

## 6. 接口契约

所有接口路径前缀：`/accounting/voucher`

### 6.1 POST `/accounting/voucher/generate` — 生成凭证

**请求体** (`VoucherGenerateRequest`):
```json
{
  "traceNo": "TRC20260512000001",
  "bookkeeperName": "SYSTEM"
}
```

**校验规则**：
- `traceNo`：必填，长度 1-64
- `bookkeeperName`：非必填，默认 "SYSTEM"

**业务逻辑**：
1. 按 traceNo 查询流水，校验 status=PROCESSING
2. 匹配记账规则（businessCode + tradingCode + payChannel）
3. 执行 SpEL 脚本计算分录金额
4. 生成辅助核算分摊分录
5. 借贷平衡校验
6. 生成凭证号 + 分录流水号
7. 写入凭证 + 分录 + 辅助核算项
8. 匹配缓冲规则，写入缓冲记录
9. 回填 txnNo 到凭证
10. 返回 `VoucherGenerateResponse`

### 6.2 GET `/accounting/voucher/{voucherNo}` — 查询凭证详情

**响应**：凭证基本信息 + 分录列表 + 辅助核算项列表 + 缓冲标记

### 6.3 GET `/accounting/voucher/trace/{traceNo}` — 按流水号查询凭证

**响应**：关联凭证列表（一个流水可能对应多个凭证，当前阶段仅一个）

---

## 7. 领域服务设计（拆分为两个独立类）

### 7.1 VoucheringDomainService（Java-A 负责）

> **P1-1 修复**：`persistVoucher` 不开启独立事务，只做 save 操作，由 Application Service 统一控制事务边界。

```java
@Service
@RequiredArgsConstructor
public class VoucheringDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final VoucherNoGenerator voucherNoGenerator;
    private final EntryIdGenerator entryIdGenerator;
    private final RuleScriptExecutor ruleScriptExecutor;

    /**
     * 按 traceNo 查询流水及其明细
     */
    public JournalWithDetails loadJournal(String traceNo);

    /**
     * 匹配记账规则（含 status=2 校验）
     */
    public AccountingRuleWithDetails matchRule(
        String businessCode, String tradingCode, String payChannel);

    /**
     * 执行 SpEL 脚本计算分录金额
     *
     * @param ruleDetail 规则明细行
     * @param businessDetail 对应的业务明细行
     * @param accountingDate 会计日期
     * @return 计算后的分录金额
     */
    public BigDecimal calculateEntryAmount(
        AccountingRuleDetailPO ruleDetail,
        BusinessDetailPO businessDetail,
        LocalDate accountingDate);

    /**
     * 借贷平衡校验
     *
     * @param entries 待校验的分录列表
     * @throws AccountException 当 ΣDebit != ΣCredit 时抛出
     */
    public void validateDebitCreditBalance(List<VoucherEntryData> entries);

    /**
     * 写入凭证 + 分录（不开启事务，由 Application Service 控制事务边界）
     *
     * @return 生成的凭证号
     */
    public String persistVoucher(
        BusinessRecordPO journal,
        AccountingRulePO rule,
        List<VoucherEntryData> entries,
        String bookkeeperName);
}
```

### 7.2 BufferPostingDomainService（Java-B 负责）

```java
@Service
@RequiredArgsConstructor
public class BufferPostingDomainService {

    private final BufferPostingRuleMapper bufferPostingRuleMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;
    private final AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;

    /**
     * 辅助核算分摊引擎
     *
     * @param entryData 分录数据
     * @param auxiliaryConfigs 该分录行对应的辅助核算配置列表
     * @return 分摊后的辅助核算项列表
     */
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
        VoucherEntryData entryData,
        List<AccountingRuleAuxiliaryPO> auxiliaryConfigs);

    /**
     * 缓冲规则匹配
     *
     * @param entryData 分录数据
     * @param businessCode 业务线编码
     * @param tradingCode 交易编码
     * @param payChannel 支付渠道
     * @return 匹配到的缓冲规则（无则 null）
     */
    public BufferPostingRulePO matchBufferRule(
        VoucherEntryData entryData,
        String businessCode, String tradingCode, String payChannel);

    /**
     * 批量写入缓冲记账明细
     */
    public void persistBufferPostingDetails(
        List<BufferPostingDetailData> details);

    /**
     * 批量写入辅助核算项
     */
    public void persistAuxiliaryItems(List<AuxiliaryItemData> items);

    /**
     * 计算分片值（同一账户必须在同一分片）
     */
    public Long calculateSharding(String accountNo);
}
```

---

## 8. 领域数据对象

### 8.1 VoucherEntryData（分录数据，Java-A 生成）

```java
@Data
@AllArgsConstructor
public class VoucherEntryData {
    private String entryId;
    private String voucherNo;
    private Integer rowNum;
    private String subjectCode;
    private String accountNo;
    private Integer debitCredit;   // 1=借, 2=贷
    private BigDecimal amount;
    private String currency;
    private String summary;
    private LocalDate accountingDate;
    private Integer isUnilateral;  // 来自规则明细 is_unilateral（0/1，与 DDL TINYINT 对齐）
    private Integer isBuffered;    // 缓冲匹配后标记（0/1，与 DDL TINYINT 对齐）
}
```

### 8.2 AuxiliaryItemData（辅助核算项数据，Java-B 生成）

```java
@Data
@AllArgsConstructor
public class AuxiliaryItemData {
    private String entryId;
    private String voucherNo;
    private String subjectCode;
    private String auxType;
    private String auxCode;
    private String auxName;
    private Integer changeDirection;  // 1=增, 2=减
    private BigDecimal amount;
    private LocalDate accountingDate;
}
```

### 8.3 BufferPostingDetailData（缓冲记账明细数据，Java-B 生成）

```java
@Data
@AllArgsConstructor
public class BufferPostingDetailData {
    private Long ruleId;
    private Integer bufferMode;
    private String voucherNo;
    private String entryId;
    private String txnNo;
    private String traceNo;
    private Integer traceSeq;
    private String businessCode;
    private String tradingCode;
    private String payChannel;
    private Integer tradeType;
    private LocalDateTime tradeTime;
    private String accountNo;
    private Integer debitCredit;
    private String currency;
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String summary;
    private Long sharding;  // account_no 哈希取模
}
```

---

## 9. 编号生成器设计

> **P0-1/P0-2 修复**：使用 Lua 脚本原子化初始化，消除 `isNotExists() → set() → expire()` 的 TOCTOU 竞态窗口。
> 与 Step 9 修复后的 `TransactionNoGenerator` 保持一致（详见该文件源码）。

### 9.1 VoucherNoGenerator

```java
@Component
@RequiredArgsConstructor
public class VoucherNoGenerator {

    private final RedissonClient redissonClient;

    /**
     * 凭证号生成（Lua 脚本原子化初始化）
     * 格式：VOU + yyyyMMdd + seq6，如 VOU20260512000001
     */
    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "vou:seq:" + date;
        String luaScript =
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "  redis.call('set', KEYS[1], '0') " +
            "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
            "end " +
            "return redis.call('incr', KEYS[1])";

        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
            .evalAsync(RScript.Mode.READ_WRITE, luaScript, RScript.ReturnType.INTEGER,
                Collections.singletonList(key), String.valueOf(TimeUnit.HOURS.toSeconds(25)));

        long seq;
        try {
            seq = (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成凭证编号失败: " + key, e);
        }
        return "VOU" + date + String.format("%06d", seq);
    }
}
```

### 9.2 EntryIdGenerator

```java
@Component
@RequiredArgsConstructor
public class EntryIdGenerator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RedissonClient redissonClient;

    /**
     * 分录流水号生成（Lua 脚本原子化初始化）
     * 格式：ENT + yyyyMMddHHmmssSSS + seq4，如 ENT20260512103000001001
     */
    public String generate() {
        String timeSuffix = LocalDateTime.now().format(TIME_FMT);
        String key = "ent:seq:" + timeSuffix;
        String luaScript =
            "if redis.call('exists', KEYS[1]) == 0 then " +
            "  redis.call('set', KEYS[1], '0') " +
            "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
            "end " +
            "return redis.call('incr', KEYS[1])";

        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
            .evalAsync(RScript.Mode.READ_WRITE, luaScript, RScript.ReturnType.INTEGER,
                Collections.singletonList(key), String.valueOf(TimeUnit.HOURS.toSeconds(2)));

        long seq;
        try {
            seq = (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成分录流水号失败: " + key, e);
        }
        return "ENT" + timeSuffix + String.format("%04d", seq);
    }
}
```

---

## 10. 编码要点

### 10.1 事务边界

- 凭证生成（`t_accounting_voucher` + `t_accounting_voucher_entry` + `t_accounting_voucher_auxiliary`）必须在同一事务中完成
- 使用 `TransactionTemplate` 管理事务，严禁 `@Transactional`
- **P1-1 修复**：`VoucheringDomainService.persistVoucher()` 不开启独立事务，只做 save 操作。事务边界由 `VoucheringApplicationService.generateVoucher()` 统一控制
- 缓冲明细写入（`t_buffer_posting_detail`）在同一事务内
- txnNo 回填在同一事务内完成

### 10.2 SpEL 脚本执行

- SpEL 脚本在 Step 7 时已通过预加载校验（合法性检查）
- 运行时执行需捕获 `EvaluationException`，转换为 `AccountException(SPEL_CALC_ERROR)`
- 上下文使用 `StandardEvaluationContext`，`#root` 为业务明细对象
- 返回值通过 `BigDecimal.valueOf()` 转换，防止浮点精度丢失

### 10.3 辅助核算分摊精度

```java
// 按比例分摊示例（3 条，比例分别为 30%、30%、40%）
BigDecimal total = entryAmount;  // 如 100.000000
BigDecimal[] ratios = { new BigDecimal("0.30"), new BigDecimal("0.30"), new BigDecimal("0.40") };

BigDecimal[] amounts = new BigDecimal[ratios.length];
BigDecimal sum = BigDecimal.ZERO;
for (int i = 0; i < ratios.length - 1; i++) {
    amounts[i] = total.multiply(ratios[i]).setScale(6, RoundingMode.HALF_UP);
    sum = sum.add(amounts[i]);
}
// 最后一条补差
amounts[ratios.length - 1] = total.subtract(sum);
// 确保 amounts 总和 == total
```

### 10.4 金额校验

- `amount > 0`，严禁负数（财务律法）
- 借贷平衡：`totalDebit.compareTo(totalCredit) == 0`
- 使用 `BigDecimal`，禁止 `new BigDecimal(double)`，禁止 `equals()` 比较金额
- 辅助核算分摊金额合计必须等于分录金额

### 10.5 异常体系

| 场景 | 异常类型 | ResultCode | 说明 |
|------|---------|-----------|------|
| 流水不存在 | `ServiceException` | JOURNAL_NOT_FOUND | 流水状态异常 |
| 流水状态非法 | `ServiceException` | JOURNAL_STATUS_INVALID | 流水非 PROCESSING |
| 记账规则不存在 | `AccountException` | RULE_NOT_FOUND | 账务领域异常 |
| 记账规则已停用 | `AccountException` | RULE_DISABLED | 账务领域异常 |
| SpEL 执行错误 | `AccountException` | SPEL_CALC_ERROR | 脚本运行时错误 |
| 借贷不平衡 | `AccountException` | VOUCHER_NOT_BALANCED | 凭证生成核心校验 |
| 辅助核算配置缺失 | `AccountException` | AUXILIARY_CONFIG_MISSING | 辅助核算异常 |
| 分摊金额校验失败 | `AccountException` | AUXILIARY_AMOUNT_MISMATCH | 分摊金额不匹配 |
| 缓冲规则匹配异常 | `AccountException` | BUFFER_RULE_ERROR | 缓冲入账异常 |

### 10.6 凭证状态流转

```
Step 10 生成凭证 → status = 1（未过账）
  ↓
Step 12 过账开始 → status = 2（过账中）
  ↓
Step 12 过账完成 → status = 3（已过账）
  ↓
Step 12 过账失败 → status = 4（过账失败）
  ↓
Step 18 红冲 → status = 5（已冲销）
```

### 10.7 与 Step 9 的衔接

```
Step 9 输出：traceNo + accountingDate + txnNo
Step 10 输入：traceNo → 查询流水 → 匹配规则 → 生成凭证 → 回填 txnNo

凭证生成时 txn_no 初始为空字符串（DDL 默认值），在 persistVoucher 方法中
通过 transactionTemplate 更新为 Step 9 生成的 txnNo。
```

### 10.8 与 Step 11/12 的衔接

```
Step 10 输出：voucherNo + 分录已持久化（is_unilateral / is_buffered 列已写入）+ txnNo
Step 11 输入：voucherNo → 查询凭证 + 分录（从 DDL 列获取分流标记）→ 按标记分流过账：
  - is_unilateral=1 → 实时过账（Step 11 同步处理）
  - is_unilateral=0 且 is_buffered=0 → MQ 异步过账
  - is_unilateral=0 且 is_buffered=1 → 缓冲入账（Step 16 处理）
```

---

## 11. 完成标准（Checklist）

> **对齐日期**：2026-06-24，代码与文档逐项核对后打勾。
> **P0-3/P0-4 重构说明**：`VoucherNoGenerator` 和 `EntryIdGenerator` 已统一重构为 `RedisSequenceGenerator`，通过参数化前缀/时间格式/TTL 实现所有编号生成，Lua 脚本原子化逻辑复用，消除了两份重复代码。

### Java-A
- [x] P0-1: `AccountingVoucherMapper` 补充 `updateTxnNoByVoucherNo`
- [x] P0-2: `AccountingVoucherEntryMapper` 补充 `updateStatusByVoucherNo`
- [x] P0-3/P0-4: `RedisSequenceGenerator` 统一凭证号/分录号生成（Lua 脚本原子化，替代 VoucherNoGenerator + EntryIdGenerator）
- [x] P0-5: `RuleScriptExecutor` SpEL 执行器（含异常处理）
- [x] `VoucheringDomainService` 凭证生成全流程（独立类，**不含事务**，P1-1 修复）
- [x] 流水查询 + 状态校验（status=PROCESSING）
- [x] 记账规则匹配（businessCode + tradingCode + payChannel，含辅助核算映射，P1-3 修复）
- [x] SpEL 脚本计算分录金额（#root 为 BusinessDetailPO，P1-4 修复）
- [x] 分录列表生成（含 isUnilateral + isBuffered 标记，持久化时写入 DDL 列）
- [x] 借贷平衡校验（ΣDebit.compareTo(ΣCredit) == 0）
- [x] 凭证持久化（voucher + entries，复用已有 AccountingVoucherRepository.insert/insertEntry）
- [x] voucherType 从规则获取（P1-5 修复）

### Java-B
- [x] 辅助核算分摊引擎（按比例/固定金额/不分摊）
- [x] 按比例分摊最后一条补差（防精度损失）
- [x] 辅助核算项金额校验（合计 = 分录金额）
- [x] 辅助核算项持久化（`t_accounting_voucher_auxiliary`，逐条 insert）
- [x] 缓冲规则匹配（subject_code/account_no 含 NULL/空串处理，P1-6 修复）
- [x] `BufferPostingDomainService` 独立领域服务
- [x] 缓冲记账明细写入（`t_buffer_posting_detail`，逐条 insert）
- [x] sharding 分片值计算（account_no 哈希取模）
- [x] `BufferPostingRuleMapper.selectMatchingRules` + XML（含 NULL 判断）

### Java-C
- [x] `VoucheringApplicationService` 编排凭证生成用例（**统一事务边界**，P1-1 修复）
- [x] `VoucheringController` 实现 3 个接口
- [x] `VoucherGenerateRequest` / `VoucherGenerateResponse` / `VoucherEntryResponse` DTO
- [x] DTO 使用 `jakarta.validation` 包
- [x] `VoucheringAssembler` 完成 PO/Result ↔ DTO 转换
- [x] 参数校验（traceNo 必填）
- [x] 流水不存在 / 状态非法返回明确错误
- [x] SpEL 计算错误返回明确错误（SPEL_CALC_ERROR）
- [x] 借贷不平衡返回明确错误（VOUCHER_NOT_BALANCED）
- [x] `resolveAccountNo` 有合理 fallback（P1-2 修复）
- [x] `getAuxiliaryConfigs` 从 `selectAuxiliariesByRuleId` 获取（P1-3 修复）
- [x] txnNo 从 TransactionRepository 获取（P1-2 修复）
- [x] 单测覆盖：VoucheringDomainServiceTest（借贷平衡/SpEL计算/规则匹配/流水加载/凭证持久化）+ BufferPostingDomainServiceTest（辅助核算分摊/缓冲匹配）

### TL Review
- [x] 凭证号/分录号生成器使用 Lua 脚本原子化（无 TOCTOU 竞态，统一为 RedisSequenceGenerator）
- [x] 事务边界统一在 Application Service（persistVoucher 不开启独立事务，P1-1）
- [x] SpEL 脚本执行异常处理正确（#root 为 BusinessDetailPO，P1-4）
- [x] 借贷平衡校验使用 BigDecimal.compareTo()
- [x] 辅助核算分摊精度正确（最后一条补差）
- [x] voucherType 从 rule.getVoucherType() 获取（P1-5）
- [x] 辅助核算配置通过 selectAuxiliariesByRuleId 获取映射（P1-3）
- [x] resolveAccountNo 有合理 fallback（P1-2）
- [x] 缓冲规则匹配含 NULL/空串处理（P1-6）
- [x] 凭证持久化事务完整性（同一事务内完成）
- [x] txnNo 从 TransactionRepository 正确获取
- [x] 异常类型区分正确
- [x] 与 Step 9 衔接正确（traceNo 入口，txnNo 回填）
- [x] 与 Step 11/12 衔接正确（is_unilateral + is_buffered 列已持久化，Step 11 按列值分流）
- [x] 严禁 `@Transactional`，全部使用 `TransactionTemplate`

---

## 12. 下一步行动

进入 **Step 11 · Transaction Management（事务管理）**，详见 `docs/prompt/step-11-transaction.md`。
