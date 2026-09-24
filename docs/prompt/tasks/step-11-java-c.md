# step-11-java-c · PostingApplicationService + PostingController

> **Step 11 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 11 Java-A（实时过账 + P0 补充已完成） + Java-B（异步过账 + 回滚已完成）

---

## 1. 任务目标

实现过账编排应用服务、Controller、DTO、Assembler。

核心职责：
1. **PostingApplicationService**：过账编排用例（统一事务边界 + 分布式锁 + 分录分流 + 状态联动 + 重试机制）
2. **PostingController**：3 个接口（执行过账、查询过账状态、查询事务状态）
3. **DTO**：PostingExecuteRequest / PostingExecuteResponse / PostingEntryResponse
4. **PostingAssembler**：PO ↔ DTO 转换

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、幂等设计 |
| 3 | `docs/prompt/step-11-transaction.md` | Step 11 总体任务说明（§6 接口契约） |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` DDL |
| 6 | `docs/design/domain-model.md` | 凭证域、流水域模型 |
| 7 | `accounting-core/.../domain/service/PostingDomainService.java` | Java-A 已实现 |
| 8 | `accounting-core/.../domain/service/AsyncPostingDomainService.java` | Java-B 已实现 |
| 9 | `accounting-core/.../domain/service/RollbackDomainService.java` | Java-B 已实现 |
| 10 | `accounting-core/.../infrastructure/persistence/repository/AccountingVoucherRepository.java` | 凭证仓储 |
| 11 | `accounting-core/.../infrastructure/persistence/repository/TransactionRepository.java` | 事务仓储 |
| 12 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板（Step 4 已有） |
| 13 | `accounting-core/.../config/TransactionConfig.java` | TransactionTemplate Bean |
| 14 | `accounting-core/.../context/TenantContextHolder.java` | 租户上下文（P1-7 修复：tenantId 来源） |

---

## 3. PostingApplicationService（过账编排应用服务）

新建 `accounting-core/.../application/service/PostingApplicationService.java`。

```java
@Service
@RequiredArgsConstructor
public class PostingApplicationService {

    private final PostingDomainService postingDomainService;
    private final AsyncPostingDomainService asyncPostingDomainService;
    private final RollbackDomainService rollbackDomainService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final AccountMapper accountMapper;
    private final TransactionTemplate transactionTemplate;
    private final DistributedLockTemplate distributedLockTemplate;
    private final PostingAssembler assembler;
}
```

### executePosting 执行流程

```
输入：PostingExecuteRequest（voucherNo, operatorName）
  ↓
1. 【查询凭证】按 voucherNo 查询 t_accounting_voucher
   ↓ 凭证不存在 → 抛出 ServiceException(VOUCHER_NOT_FOUND)
   ↓ 凭证 status != 1(未过账) → 抛出 ServiceException(POSTING_STATUS_INVALID)
  ↓
2. 【账户状态检查】收集凭证所有非缓冲分录的 account_no（去重）
   逐账户检查 t_account.status = 1(正常)
   ↓ 账户不存在 → 抛出 AccountException(ACCOUNT_NOT_FOUND)
   ↓ 账户冻结 → 抛出 AccountException(ACCOUNT_FROZEN)
   ↓ 账户注销 → 抛出 AccountException(ACCOUNT_CANCELLED)
  ↓
3. 【分布式锁】accounting:{tenantId}:lock:posting:trx:{voucherNo}
   （tenantId 从 TenantContextHolder.getTenantId() 获取）
   等待=0s，过期=60s
   ↓ 加锁失败 → 抛出 ServiceException(IDEMPOTENT_CONFLICT)
  ↓
4. 【内部方法 doExecutePosting】TransactionTemplate.execute()
   a. 更新凭证 status=2(过账中)
   b. 分录分流：
      - is_unilateral=1 → 实时分录列表
      - is_unilateral=0 且 is_buffered=0 → 异步分录列表
      - is_unilateral=0 且 is_buffered=1 → 缓冲分录（跳过）
   c. 实时过账：postingDomainService.executeRealTimePosting(实时分录列表)
   d. 异步过账：逐分录调用 asyncPostingDomainService.writeAsyncPostingMessage()
   e. 状态联动：
      - 无异步分录 → 凭证 status=3(已过账)
      - 有异步分录 → 凭证 status 保持 2(过账中)
   f. 事务状态更新：
      - 无异步分录 → t_transaction.status=2(成功) + finishTime
      - 有异步分录 → t_transaction.status 保持 1(处理中)
      - 回填 relateAccountCount
  ↓
5. 【catch 回滚】doExecutePosting 内部捕获异常时：
   a. 调用 rollbackDomainService.markTransactionFailed()
   b. 向上抛出 AccountException(POSTING_FAILED)
  ↓
6. 【返回】PostingExecuteResponse
```

> **注意**：步骤 4 的整个过账逻辑在一个 TransactionTemplate 事务中。实时过账、本地消息写入、状态更新全部在同一事务内完成。

### 重试机制实现（P1-3 修复）

```java
private static final int MAX_RETRY = 3;
private static final long[] RETRY_DELAYS = {10_000, 30_000, 60_000}; // 10s, 30s, 60s

public PostingExecuteResponse executePosting(PostingExecuteRequest request) {
    int retryCount = 0;
    while (true) {
        try {
            return doExecutePosting(request);
        } catch (Exception e) {
            // P1-3 修复：重试期间不标记 FAILED，只在最终失败时才调用 markTransactionFailed
            if (retryCount >= MAX_RETRY || !rollbackDomainService.isRetryable(e)) {
                // 最终失败：标记 FAILED 状态
                rollbackDomainService.markTransactionFailed(
                    request.getVoucherNo(), request.getTraceNo(), e.getMessage());
                throw e;
            }
            // 可重试异常：不标记 FAILED，等待后重试
            try {
                Thread.sleep(RETRY_DELAYS[retryCount]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new AccountException(ResultCode.SYSTEM_ERROR, "重试被中断", ie);
            }
            retryCount++;
            log.warn("过账执行重试: attempt={}/{}", retryCount, MAX_RETRY);
        }
    }
}
```

> **P1-3 修复说明**：重试循环中，只有最终不可重试的失败才调用 `markTransactionFailed()` 更新状态为 FAILED。可重试异常不修改数据库状态，避免重试成功后需要补偿回退。

---

## 4. PostingController（3 个接口）

新建 `accounting-core/.../controller/PostingController.java`。

```java
@RestController
@RequestMapping("/accounting/posting")
@RequiredArgsConstructor
@Validated
public class PostingController {

    private final PostingApplicationService postingApplicationService;

    /**
     * 执行过账
     */
    @PostMapping("/execute")
    public ApiResponse<PostingExecuteResponse> executePosting(
        @Valid @RequestBody PostingExecuteRequest request) {
        return ApiResponse.success(postingApplicationService.executePosting(request));
    }

    /**
     * 查询过账状态
     */
    @GetMapping("/voucher/{voucherNo}")
    public ApiResponse<PostingExecuteResponse> getPostingStatus(
        @PathVariable String voucherNo) {
        return ApiResponse.success(postingApplicationService.getPostingStatus(voucherNo));
    }

    /**
     * 查询事务状态
     */
    @GetMapping("/transaction/{txnNo}")
    public ApiResponse<TransactionStatusResponse> getTransactionStatus(
        @PathVariable String txnNo) {
        return ApiResponse.success(postingApplicationService.getTransactionStatus(txnNo));
    }
}
```

---

## 5. DTO 定义

### PostingExecuteRequest（新建 `accounting-api/.../request/PostingExecuteRequest.java`）

```java
@Data
public class PostingExecuteRequest {

    @NotBlank(message = "凭证号不能为空")
    @Size(max = 32, message = "凭证号长度不能超过32")
    private String voucherNo;

    @Size(max = 32, message = "操作人姓名长度不能超过32")
    private String operatorName = "SYSTEM";
}
```

### PostingExecuteResponse（新建 `accounting-api/.../response/PostingExecuteResponse.java`）

```java
@Data
public class PostingExecuteResponse {

    /** 凭证号 */
    private String voucherNo;

    /** 凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败 */
    private Integer voucherStatus;

    /** 凭证状态描述 */
    private String voucherStatusDesc;

    /** 事务编号 */
    private String txnNo;

    /** 事务状态：1-处理中,2-成功,3-失败 */
    private Integer transactionStatus;

    /** 过账分录列表 */
    private List<PostingEntryResponse> entries;

    /** 是否包含异步分录 */
    private Boolean hasAsyncEntries;

    /** 是否包含缓冲分录 */
    private Boolean hasBufferEntries;
}
```

### PostingEntryResponse（新建为 PostingExecuteResponse 的内部类或独立文件）

```java
@Data
public class PostingEntryResponse {

    /** 分录流水号 */
    private String entryId;

    /** 会计科目编码 */
    private String subjectCode;

    /** 账户编号 */
    private String accountNo;

    /** 借贷方向：1-借,2-贷 */
    private Integer debitCredit;

    /** 金额 */
    private BigDecimal amount;

    /** 状态：1-未过账,2-已过账,3-过账失败 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 是否实时更新 */
    private Integer unilateral;

    /** 是否缓冲入账 */
    private Integer buffered;
}
```

---

## 6. PostingAssembler（PO ↔ DTO 转换）

新建 `accounting-core/.../application/assembler/PostingAssembler.java`。

```java
@Component
public class PostingAssembler {

    /**
     * 将凭证 PO + 分录列表转换为响应 DTO
     */
    public PostingExecuteResponse toResponse(
        AccountingVoucherPO voucher,
        List<AccountingVoucherEntryPO> entries,
        TransactionPO transaction);

    /**
     * 单个分录 PO → 分录响应 DTO
     */
    public PostingEntryResponse toEntryResponse(AccountingVoucherEntryPO entry);
}
```

---

## 7. 需要创建的文件清单

| 文件 | 说明 |
|------|------|
| `PostingApplicationService.java` | 过账编排应用服务 |
| `PostingController.java` | 过账 Controller（3 个接口） |
| `PostingExecuteRequest.java` | 过账请求 DTO（accounting-api 模块） |
| `PostingExecuteResponse.java` | 过账结果响应 DTO（accounting-api 模块） |
| `PostingEntryResponse.java` | 过账分录行响应 DTO（accounting-api 模块） |
| `PostingAssembler.java` | PO ↔ DTO 转换器 |

---

## 8. 完成标准（Checklist）

- [ ] `PostingApplicationService` 过账编排用例（**统一事务边界**）
- [ ] 分布式锁控制（wait=0s，lease=60s，tenantId 从 TenantContextHolder 获取）
- [ ] 凭证状态校验（status=1 未过账）
- [ ] 过账前账户状态检查（NORMAL=1，逐账户校验）
- [ ] 分录分流逻辑（is_unilateral=1 实时 / is_buffered=0 异步 / is_buffered=1 缓冲）
- [ ] 凭证状态联动更新（全实时 → status=3；含异步 → 保持 status=2）
- [ ] 事务状态更新（全实时 → status=2成功 + finishTime；含异步 → 保持 status=1处理中）
- [ ] 重试机制（临时性异常 3 次指数退避）
- [ ] 回滚处理（失败时调用 RollbackDomainService.markTransactionFailed）
- [ ] `PostingController` 实现 3 个接口
- [ ] `PostingExecuteRequest` / `PostingExecuteResponse` / `PostingEntryResponse` DTO
- [ ] DTO 使用 `jakarta.validation` 包
- [ ] `PostingAssembler` 完成 PO ↔ DTO 转换
- [ ] 参数校验（voucherNo 必填，长度 1-32）
- [ ] 凭证不存在 / 状态非法返回明确错误
- [ ] 余额不足返回明确错误（INSUFFICIENT_BALANCE）
- [ ] 单测：正常过账全流程、凭证不存在、凭证状态非法、含异步分录的过账
