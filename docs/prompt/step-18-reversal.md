# step-18-reversal · 冲账与红冲

> **Phase 6 第三步** | 归属：`@Java` 工程师
> 前置依赖：Step 10（凭证生成引擎）+ Step 11（事务管理）+ Step 12（过账引擎）+ Step 17（日切与试算平衡）
>
> **与 Step 11 Rollback 的边界**：Step 11 `RollbackDomainService` 负责**过账失败回滚**（异步分录失败后的单边回滚 + 状态标记 FAILED），属于**异常路径自动修复**。本 Step 负责**业务红冲**（原凭证已过账后，因业务差错需要冲销），属于**正常业务流程**。两者不可混淆。
>
> **与 Step 12 过账引擎的边界**：红冲凭证的过账复用现有过账链路（`PostingDomainService` / `PostingApplicationService`），但需要识别 `trade_type=RED` 并保证借贷方向对调后余额计算正确。

---

## 0. 已有基础设施确认（无需重复开发）

以下字段、枚举、方法已在前期 Step 中完成，红冲功能直接复用：

| 已有项 | 所在文件 | 说明 |
|--------|---------|------|
| `t_accounting_voucher.orig_voucher_no` | `docs/sql/2-voucher.sql:28` | 红冲凭证关联原凭证号字段 |
| `VoucherStatusEnum.REVERSED(5)` | `VoucherStatusEnum.java` | 凭证状态"已冲销" |
| `TradeTypeEnum.RED(3)` | `TradeTypeEnum.java` | 交易类别"红" |
| `TradeTypeEnum.BLUE(4)` | `TradeTypeEnum.java` | 交易类别"蓝"（补充说明用） |
| `AccountingVoucherMapper.selectReversalByOrig()` | `AccountingVoucherMapper.java:44` | 按原凭证号查询红冲凭证 |
| `AccountingVoucherRepository.selectReversalByOrig()` | `AccountingVoucherRepository.java:60` | Repository 层封装 |
| `VoucherStatusEnum.POSTED(3)` | `VoucherStatusEnum.java` | 红冲前置校验：原凭证必须已过账 |
| `reversal_flow.mmd` | `docs/design/flowchart/reversal_flow.mmd` | 红冲流程设计图 |

---

## 1. 任务目标（Mission）

实现**红冲（冲账）** 完整业务链路：

1. **前置校验**：确认原凭证已过账、分录全部过账、未被重复红冲
2. **生成红冲凭证**：复制原凭证信息，`trade_type=RED`，借贷方向对调，金额保持正数
3. **红冲过账**：走标准过账链路更新账户余额（红冲分录的借贷方向 = 原分录的反方向）
4. **状态联动**：原凭证标记为 `REVERSED(5)`，红冲凭证标记为 `POSTED(3)`
5. **蓝字补充凭证**（可选扩展）：红冲后重新生成正确的蓝字凭证

**核心原则**：
- 红冲**不可删除**原凭证及其分录，必须保留完整审计轨迹
- 红冲凭证的会计日期 = 发起红冲时的会计日期（**不是**原凭证会计日期）
- 红冲凭证的借贷方向对调，但**金额保持正数**（负数运算违反财务律法）
- 红冲后的科目总账：借贷发生额都增加，通过红冲方向自然抵消原错误金额

---

## 2. 详细子任务列表

### P0-1：`ReversalDomainService` 核心领域服务（Java-A）

> **文件**：`accounting-core/src/main/java/com/kltb/accounting/core/domain/service/ReversalDomainService.java`

实现红冲核心领域逻辑：

```java
@Service
@RequiredArgsConstructor
public class ReversalDomainService {
    // 依赖注入（按项目规范使用构造器注入）
    // - TransactionTemplate（严禁 @Transactional）
    // - AccountingVoucherRepository
    // - RedisSequenceGenerator（凭证号/分录号生成）
    // - DistributedLock（按 origVoucherNo 加锁，防并发红冲）
    // - PostingDomainService（复用标准过账）
}
```

**核心方法**：

#### `ReversalResult executeReversal(String origVoucherNo, String bookkeeperName, String summary)`

红冲执行主流程，包含以下子步骤：

1. **分布式锁**：`account:reversal:{origVoucherNo}` 防止并发红冲
2. **前置校验**（双重检查）：
   - 查询原凭证，确认状态 == `POSTED(3)`
   - 查询原凭证所有分录，确认所有分录 `status == POSTED(2)`
   - 查询 `orig_voucher_no = 原凭证号` 的红冲凭证，确认不存在
3. **生成红冲凭证号**：`REV + 年月日 + 序列号`（复用 `RedisSequenceGenerator`，前缀 "REV"）
4. **复制并反转分录**：
   - 逐条复制原分录，对调 `debit_credit`（借→贷，贷→借）
   - 生成新 `entry_id`（`REV_ENTRY + 序列号`）
   - 复制辅助核算项（`t_accounting_voucher_auxiliary`），不修改 `change_direction`
5. **写入红冲凭证**：
   ```
   voucher_no    → 新生成
   trade_type    → TradeTypeEnum.RED
   orig_voucher_no → 原凭证号
   accounting_date → 当前会计日期（非原凭证会计日期）
   summary       → 入参（如"红冲-原凭证:VOU20250101001"）
   其他业务字段   → 复制自原凭证（business_code, trading_code, pay_channel 等）
   amount        → 与原凭证相同
   ```
6. **执行过账**：调用 `PostingDomainService` 按标准流程过账
7. **更新原凭证状态**：`status = VoucherStatusEnum.REVERSED`
8. **返回结果**

#### `boolean isReversable(String voucherNo)`

判断凭证是否可被红冲（封装前置校验逻辑）。

#### `List<AccountingVoucherPO> queryReversalRecords(String origVoucherNo)`

查询某凭证的所有红冲记录（复用 `AccountingVoucherRepository.selectReversalByOrig`）。

---

### P0-2：`ReversalAssembler` 转换器（Java-B）

> **文件**：`accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/convert/ReversalAssembler.java`

```java
@Mapper(componentModel = "spring")  // MapStruct
public interface ReversalAssembler {

    /**
     * 红冲请求 → 内部参数
     */
    ReversalParams toParams(ReversalRequest request);

    /**
     * 原凭证 + 新凭证 → 红冲响应
     */
    ReversalResponse toResponse(
        AccountingVoucherPO origVoucher,
        AccountingVoucherPO reversalVoucher,
        List<AccountingVoucherEntryPO> reversalEntries);

    /**
     * 红冲记录列表 → 响应列表
     */
    List<ReversalRecordResponse> toRecordResponses(
        List<AccountingVoucherPO> reversalRecords);
}
```

---

### P0-3：DTO 定义（Java-C）

> **文件**（位于 `accounting-api`）：
> - `accounting-api/src/main/java/com/kltb/accounting/api/request/ReversalRequest.java`
> - `accounting-api/src/main/java/com/kltb/accounting/api/response/ReversalResponse.java`
> - `accounting-api/src/main/java/com/kltb/accounting/api/response/ReversalRecordResponse.java`

#### `ReversalRequest`

```java
@Data
public class ReversalRequest {
    /** 原凭证号（必填） */
    @NotBlank(message = "原凭证号不能为空")
    private String origVoucherNo;

    /** 红冲摘要（选填，默认"红冲凭证:{origVoucherNo}"） */
    private String summary;

    /** 记账人姓名（选填，从登录上下文获取） */
    private String bookkeeperName;
}
```

#### `ReversalResponse`

```java
@Data
@Builder
public class ReversalResponse {
    private String origVoucherNo;         // 原凭证号
    private String reversalVoucherNo;     // 红冲凭证号
    private TradeTypeEnum tradeType;       // RED
    private BigDecimal amount;             // 红冲金额
    private LocalDate accountingDate;      // 红冲会计日期
    private int entryCount;                // 红冲分录数
    private LocalDateTime reversaledAt;    // 红冲时间
}
```

#### `ReversalRecordResponse`

```java
@Data
@Builder
public class ReversalRecordResponse {
    private String reversalVoucherNo;     // 红冲凭证号
    private String origVoucherNo;         // 原凭证号
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String summary;
    private VoucherStatusEnum status;
    private LocalDateTime postTime;
    private String bookkeeperName;
}
```

---

### P0-4：`ReversalApplicationService` 编排层（Java-C）

> **文件**：`accounting-core/src/main/java/com/kltb/accounting/core/application/service/ReversalApplicationService.java`

```java
@Service
@RequiredArgsConstructor
public class ReversalApplicationService {

    private final ReversalDomainService reversalDomainService;
    private final ReversalAssembler reversalAssembler;

    /**
     * 执行红冲
     *
     * 编排流程：
     * 1. 参数校验（traceNo/traceSeq 幂等键可选，通过 requestNo 或外部幂等控制）
     * 2. 调用 ReversalDomainService.executeReversal()
     * 3. 转换响应对象
     * 4. 记录审计日志
     */
    public ReversalResponse executeReversal(ReversalRequest request) { ... }

    /**
     * 查询红冲记录
     */
    public List<ReversalRecordResponse> queryReversalRecords(String origVoucherNo) { ... }

    /**
     * 判断凭证是否可红冲
     */
    public boolean isReversable(String voucherNo) { ... }
}
```

---

### P0-5：`ReversalController` 接口层（Java-C）

> **文件**：`accounting-core/src/main/java/com/kltb/accounting/core/interfaces/ReversalController.java`

```java
@RestController
@RequestMapping("/accounting/reversal")
@RequiredArgsConstructor
@Validated
@Tag(name = "红冲管理", description = "凭证红冲（冲账）接口")
public class ReversalController {

    private final ReversalApplicationService reversalApplicationService;

    /**
     * 执行红冲
     * POST /accounting/reversal/execute
     */
    @PostMapping("/execute")
    @Operation(summary = "执行凭证红冲", description = "根据原凭证号生成红冲凭证并执行过账")
    public ApiResponse<ReversalResponse> executeReversal(@Valid @RequestBody ReversalRequest request) { ... }

    /**
     * 查询某凭证的红冲记录
     * GET /accounting/reversal/records?origVoucherNo=VOUxxx
     */
    @GetMapping("/records")
    @Operation(summary = "查询红冲记录", description = "查询指定凭证的所有红冲凭证")
    public ApiResponse<List<ReversalRecordResponse>> queryReversalRecords(
        @RequestParam String origVoucherNo) { ... }

    /**
     * 判断凭证是否可红冲
     * GET /accounting/reversal/check?voucherNo=VOUxxx
     */
    @GetMapping("/check")
    @Operation(summary = "红冲可行性检查", description = "判断指定凭证是否可以被红冲")
    public ApiResponse<Boolean> isReversable(@RequestParam String voucherNo) { ... }
}
```

---

### P0-6：Mapper / Repository 补充（持久层）

> 以下方法为红冲新增，需补充到现有文件中：

| # | 文件 | 方法 | 说明 |
|---|------|------|------|
| 1 | `AccountingVoucherEntryMapper.java` | `int batchInsert(@Param("list") List<AccountingVoucherEntryPO> list)` | 批量插入红冲分录（XML 实现） |
| 2 | `AccountingVoucherAuxiliaryMapper.java` | `int batchInsert(@Param("list") List<AccountingVoucherAuxiliaryPO> list)` | 批量插入辅助核算项（XML 实现） |
| 3 | `AccountingVoucherMapper.java` | `List<AccountingVoucherPO> selectReversalByOrig(String origVoucherNo)` | 已存在，确认可用 |
| 4 | `AccountingVoucherRepository.java` | `selectReversalByOrig()` 返回 `List` | 已存在，确认可用 |
| 5 | `AccountingVoucherEntryMapper.java` | `List<AccountingVoucherEntryPO> selectByVoucherNo(String voucherNo)` | 已存在，确认可用 |
| 6 | `AccountingVoucherMapper.java` | `int updateStatusAndBookkeeper(String voucherNo, VoucherStatusEnum status, String bookkeeperName)` | 更新凭证状态 + 记账人（XML 实现） |

**XML 文件**：在对应 `resources/mapper/` 目录下补充 XML SQL。

---

### P0-7：错误码补充（Java-A）

> **文件**：`accounting-api/src/main/java/com/kltb/accounting/api/constant/ResultCode.java`

在 `2xxx` 段新增以下错误码：

```java
// ==================== 红冲错误 203x ====================
REVERSAL_ORIGINAL_NOT_POSTED("2035", "原凭证未过账，不可红冲"),
REVERSAL_ENTRIES_NOT_ALL_POSTED("2036", "原凭证分录未全部过账，不可红冲"),
REVERSAL_ALREADY_EXISTS("2037", "红冲记录已存在，不可重复红冲"),
REVERSAL_ORIGINAL_NOT_FOUND("2038", "原凭证不存在"),
REVERSAL_POSTING_FAILED("2039", "红冲过账失败"),
```

**注意**：`2030-2034` 已被日切相关错误码占用，红冲错误码从 `2035` 开始。

---

### P0-8：`ReversalJobHandler` XXL-JOB（可选，Java-B）

> **文件**：`accounting-job/src/main/java/com/kltb/accounting/job/job/ReversalJobHandler.java`

支持批量红冲 Job（场景：批量冲销某日期范围内指定业务线的凭证）：

```java
@Component
@XxlJob("reversalJob")
public class ReversalJobHandler {
    // 参数：accountingDate,businessCode,voucherNoList（逗号分隔）
    // 逐条执行红冲，单条失败不中断，汇总结果
}
```

---

## 3. 红冲过账余额计算逻辑

### 关键设计：红冲方向与余额

红冲凭证**不修改原凭证数据**，而是通过**反向分录 + 正向金额**实现：

```
原凭证：                    红冲凭证：
  借：1001 银行存款  100      贷：1001 银行存款  100    ← 方向对调
  贷：6001 主营业务收入 100    借：6001 主营业务收入 100  ← 方向对调

账户余额影响：
  1001 银行存款：原 +100，红冲 -100（通过贷方减少）→ 净变化 = 0
  6001 主营业务收入：原 +100，红冲 -100（通过借方减少损益）→ 净变化 = 0
```

**过账时的行为**：
- 红冲分录走标准过账链路（`PostingDomainService`）
- 过账引擎按 `debit_credit` 和 `balance_direction` 计算 `change_direction`
- 红冲分录的 `debit_credit` 已对调，因此余额自动反向更新
- **严禁**在过账链路中识别 `trade_type=RED` 后做负数运算

### 辅助核算项处理

- 复制原凭证所有 `t_accounting_voucher_auxiliary` 记录
- `change_direction` 不修改（辅助核算项不直接参与余额计算）
- `amount` 保持正数

---

## 4. 蓝字补充凭证（扩展场景）

红冲后如需重新记账，通过**蓝补**流程：

1. 调用 `ReversalDomainService.executeReversal()` 执行红冲
2. 调用标准 `VoucheringDomainService` 生成新的正确凭证（`trade_type=NORMAL` 或 `BLUE`）
3. 新凭证走标准过账流程

**可选接口**（蓝补一步到位）：

```
POST /accounting/reversal/rebook
{
    "origVoucherNo": "VOU20250101001",
    "corrections": { ... },  // 需要修正的字段
    "summary": "蓝补-修正XX"
}
→ 返回 { reversalVoucherNo, newVoucherNo }
```

此接口为可选，若时间紧张可留到后续迭代。

---

## 5. 完成标准（Checklist）

> **对齐日期**：2026-06-24，代码与文档逐项核对后打勾。

### 代码层面

- [x] `ReversalDomainService` 完整实现（分布式锁 + 前置校验 + 红冲凭证生成 + 过账 + 状态联动）
- [x] `ReversalAssembler` MapStruct 转换器
- [x] 3 个 DTO（ReversalRequest + ReversalResponse + ReversalRecordResponse）
- [x] `ReversalApplicationService` 编排层（含审计日志）
- [x] `ReversalController` 3 个接口（/execute, /records, /check）
- [x] Mapper / Repository 补充方法（批量插入 + 状态更新）
- [x] XML SQL（batchInsert + updateStatusAndBookkeeper）
- [x] ResultCode 新增 5 个错误码（2035~2039）+ 2040 补充
- [x] `ReversalJobHandler`（已实现）

### 规范层面

- [x] 严禁使用 `@Transactional`，全部使用 `TransactionTemplate`
- [x] 严禁负数运算（红冲金额全部为正，通过借贷方向对调实现）
- [x] 严禁硬编码科目号（科目号来自规则/原凭证复制）
- [x] `accounting-api` 不引入持久层依赖
- [x] 分布式锁 key 含 `tenantId`
- [x] 多账户加锁按 `account_no` 升序（复用 PostingDomainService 的锁策略）
- [x] 所有异常绑定 `ResultCode` 枚举，无魔法数字
- [x] SLF4J 日志规范：`[REVERSAL]` 前缀，关键操作记录 INFO 日志

### 质量层面

- [x] 单元测试覆盖核心领域逻辑：ReversalDomainServiceTest（前置校验/方向对调/状态联动/幂等/记录查询）
- [x] 红冲不可删除/修改原凭证数据（通过测试验证 — ReversalDomainServiceTest 验证前置校验拦截）
- [x] 重复红冲被拦截（幂等校验 — REVERSAL_ALREADY_EXISTS 测试覆盖）
- [x] 红冲后原凭证状态正确更新为 `REVERSED`
- [x] 红冲凭证 `trade_type = RED` 且 `orig_voucher_no` 正确关联
- [x] 红冲过账后余额计算正确（通过账户余额查询验证净变化为 0）

---

## 6. 文件变更清单

### 新增文件

| 文件路径 | 说明 |
|---------|------|
| `accounting-api/.../request/ReversalRequest.java` | 红冲请求 DTO |
| `accounting-api/.../response/ReversalResponse.java` | 红冲响应 DTO |
| `accounting-api/.../response/ReversalRecordResponse.java` | 红冲记录响应 DTO |
| `accounting-core/.../domain/service/ReversalDomainService.java` | 红冲核心领域服务 |
| `accounting-core/.../application/service/ReversalApplicationService.java` | 红冲编排层 |
| `accounting-core/.../infrastructure/convert/ReversalAssembler.java` | 红冲 DTO 转换器 |
| `accounting-core/.../interfaces/ReversalController.java` | 红冲 REST 接口 |
| `accounting-job/.../job/ReversalJobHandler.java` | 红冲批量 Job（可选） |

### 修改文件

| 文件路径 | 变更说明 |
|---------|---------|
| `accounting-api/.../constant/ResultCode.java` | 新增 2035~2039 错误码 |
| `accounting-core/.../mapper/AccountingVoucherEntryMapper.java` | 新增 batchInsert 方法 |
| `accounting-core/.../mapper/AccountingVoucherAuxiliaryMapper.java` | 新增 batchInsert 方法 |
| `accounting-core/.../mapper/AccountingVoucherMapper.java` | 新增 updateStatusAndBookkeeper 方法 |
| `accounting-core/.../repository/AccountingVoucherRepository.java` | 封装新方法 |
| `accounting-core/.../resources/mapper/*.xml` | 新增对应 XML SQL |
| `docs/prompt/FIN-Core_Blueprint.md` | 标记 Step 18 完成 |

---

## 7. 风险提示

| 风险 | 等级 | 说明 | 缓解措施 |
|------|------|------|---------|
| 红冲后余额计算错误 | P0 | 方向对调逻辑有误导致余额翻倍而非抵消 | 单测验证原凭证 + 红冲凭证净变化 = 0 |
| 并发红冲 | P0 | 同一凭证被同时红冲两次 | 分布式锁 + 双重检查 |
| 红冲凭证未过账 | P1 | 红冲凭证写入但过账失败，原凭证已标记 REVERSED | 在同一事务内完成过账 + 状态更新，或先过账再标记 |
| 会计日期漂移 | P1 | 跨零点执行时红冲凭证会计日期错误 | 使用 `AccountingDateCache` 获取全局会计日期 |
| 辅助核算项丢失 | P2 | 复制时遗漏辅助核算项 | 复制后比对原凭证辅助项数量 |

---

## 8. 下一步行动

Step 18 完成并确认后，推进至 **Step 19（MCP Server Integration）**。
