# step-13-java-c · AccountStatusApplicationService + AccountStatusController

> **Step 13 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 13 Java-A（领域服务已完成）+ Java-B（风控状态变更已完成）

---

## 1. 任务目标

实现账户状态管理应用服务、Controller、DTO、Assembler。

核心职责：
1. **AccountStatusApplicationService**：用例编排（5 个接口：冻结/解冻/注销/状态查询/风控变更）
2. **AccountStatusController**：5 个 REST 接口
3. **DTO**：4 个 Request + 2 个 Response
4. **AccountStatusAssembler**：PO ↔ DTO 转换

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范 |
| 3 | `docs/prompt/step-13-account-status.md` | Step 13 总体任务说明（§6 接口契约） |
| 4 | `accounting-core/.../domain/service/AccountStatusChangeDomainService.java` | Java-A/B 已实现 |
| 5 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 6 | `accounting-core/.../config/TransactionConfig.java` | TransactionTemplate Bean |
| 7 | `docs/prompt/tasks/step-11-java-c.md` | Step 11 Java-C DTO 定义参考 |

---

## 3. AccountStatusApplicationService（应用服务）

新建 `accounting-core/.../application/service/AccountStatusApplicationService.java`。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusApplicationService {

    private final AccountStatusChangeDomainService accountStatusChangeDomainService;
    private final AccountRepository accountRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final AccountStatusAssembler assembler;
}
```

> **注意**：此应用服务为**编排层**，不包含任何业务逻辑。所有业务逻辑下沉到领域服务。应用服务只做参数校验、分布式锁包裹、事务管理、DTO 转换、委托调用。

### 方法签名

```java
/**
 * 冻结账户
 * 编排：分布式锁 → TransactionTemplate → 领域服务 freezeAccount → DTO 转换
 */
public AccountStatusChangeResponse freezeAccount(AccountFreezeRequest request);

/**
 * 解冻账户
 * 编排：分布式锁 → TransactionTemplate → 领域服务 unfreezeAccount → DTO 转换
 */
public AccountStatusChangeResponse unfreezeAccount(AccountUnfreezeRequest request);

/**
 * 注销账户
 * 编排：分布式锁 → TransactionTemplate → 领域服务 cancelAccount → DTO 转换
 */
public AccountStatusChangeResponse cancelAccount(AccountCancelRequest request);

/**
 * 查询账户状态
 * 编排：领域服务 queryAccountStatus → DTO 转换
 */
public AccountStatusResponse queryAccountStatus(String accountNo);

/**
 * 变更风控状态
 * 编排：TransactionTemplate → 领域服务 changeRiskStatus → DTO 转换
 */
public AccountStatusChangeResponse changeRiskStatus(AccountRiskStatusRequest request);
```

### 编排示例（冻结）

```java
public AccountStatusChangeResponse freezeAccount(AccountFreezeRequest request) {
    return distributedLockTemplate.execute(
        "account:status:" + request.getAccountNo(),
        3, -1,  // waitTime=3s, leaseTime=-1 启用 watchdog 自动续期（默认 30s）
        () -> transactionTemplate.execute(status -> {
            AccountPO po = accountStatusChangeDomainService.freezeAccount(
                request.getAccountNo(), request.getReason());
            return assembler.toChangeResponse(po, AccountStatusEnum.NORMAL, AccountStatusEnum.FROZEN);
        })
    );
}
```

> **leaseTime 策略**：必须使用 `-1` 启用 Redisson watchdog 自动续期（30s）。若传固定值（如 10s），watchdog 不生效，事务内含复杂查询逻辑时可能导致锁提前释放。

> **风控变更不加分布式锁**：`changeRiskStatus` 方法直接调用 TransactionTemplate + 领域服务，不包裹 DistributedLockTemplate。

---

## 4. AccountStatusController（5 个接口）

新建 `accounting-core/.../interfaces/AccountStatusController.java`。

```java
@RestController
@RequestMapping("/accounting/account/status")
@RequiredArgsConstructor
@Validated
@Tag(name = "账户状态管理", description = "冻结/解冻/注销/状态查询/风控变更")
public class AccountStatusController {

    private final AccountStatusApplicationService accountStatusApplicationService;

    @PostMapping("/freeze")
    @Operation(summary = "冻结账户", description = "将账户状态从正常变更为冻结")
    public ApiResponse<AccountStatusChangeResponse> freezeAccount(
        @Valid @RequestBody AccountFreezeRequest request);

    @PostMapping("/unfreeze")
    @Operation(summary = "解冻账户", description = "将账户状态从冻结恢复为正常")
    public ApiResponse<AccountStatusChangeResponse> unfreezeAccount(
        @Valid @RequestBody AccountUnfreezeRequest request);

    @PostMapping("/cancel")
    @Operation(summary = "注销账户", description = "注销账户（不可逆操作）")
    public ApiResponse<AccountStatusChangeResponse> cancelAccount(
        @Valid @RequestBody AccountCancelRequest request);

    @GetMapping("/{accountNo}")
    @Operation(summary = "查询账户状态", description = "返回账户当前主状态与风控状态")
    public ApiResponse<AccountStatusResponse> queryAccountStatus(
        @PathVariable String accountNo);

    @PostMapping("/risk")
    @Operation(summary = "变更风控状态", description = "独立修改账户风控状态")
    public ApiResponse<AccountStatusChangeResponse> changeRiskStatus(
        @Valid @RequestBody AccountRiskStatusRequest request);
}
```

---

## 5. DTO 定义

### AccountFreezeRequest（`accounting-api/.../request/AccountFreezeRequest.java`）

```java
@Data
public class AccountFreezeRequest {

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    private String accountNo;

    @Size(max = 128, message = "冻结原因长度不能超过128")
    private String reason;  // 选填
}
```

### AccountUnfreezeRequest（`accounting-api/.../request/AccountUnfreezeRequest.java`）

```java
@Data
public class AccountUnfreezeRequest {

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    private String accountNo;
}
```

### AccountCancelRequest（`accounting-api/.../request/AccountCancelRequest.java`）

```java
@Data
public class AccountCancelRequest {

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    private String accountNo;

    @Size(max = 128, message = "注销原因长度不能超过128")
    private String reason;  // 选填
}
```

### AccountRiskStatusRequest（`accounting-api/.../request/AccountRiskStatusRequest.java`）

```java
@Data
public class AccountRiskStatusRequest {

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    private String accountNo;

    @NotNull(message = "风控状态不能为空")
    private Integer riskStatus;  // 1=正常, 2=止入, 3=止出, 4=止入止出
}
```

### AccountStatusResponse（`accounting-api/.../response/AccountStatusResponse.java`）

```java
@Data
public class AccountStatusResponse {

    private String accountNo;
    private String accountName;
    private String subjectCode;
    private Integer status;
    private String statusDesc;
    private Integer riskStatus;
    private String riskStatusDesc;
    private BigDecimal balance;
    private LocalDate openDate;
    private LocalDate inactiveDate;  // Assembler 层将 1970-01-01 转为 null
}
```

### AccountStatusChangeResponse（`accounting-api/.../response/AccountStatusChangeResponse.java`）

```java
@Data
public class AccountStatusChangeResponse {

    private String accountNo;
    private Integer beforeStatus;
    private String beforeStatusDesc;
    private Integer afterStatus;
    private String afterStatusDesc;
}
```

---

## 6. AccountStatusAssembler（PO ↔ DTO 转换）

新建 `accounting-core/.../application/assembler/AccountStatusAssembler.java`。

```java
@Component
public class AccountStatusAssembler {

    /**
     * PO → 状态查询响应
     * 特殊处理：inactiveDate 为 1970-01-01 时转为 null
     */
    public AccountStatusResponse toStatusResponse(AccountPO po);

    /**
     * 状态变更响应
     * @param po 变更后的账户 PO
     * @param beforeStatus 变更前状态
     * @param afterStatus 变更后状态
     */
    public AccountStatusChangeResponse toChangeResponse(
        AccountPO po, AccountStatusEnum beforeStatus, AccountStatusEnum afterStatus);
}
```

> **inactiveDate 默认值处理**：当 `inactiveDate` 为 DDL 默认值 `1970-01-01` 时，Assembler 层转为 `null` 返回，前端显示"未注销"。

---

## 7. 需要创建的文件清单

| 文件 | 模块 | 说明 |
|------|------|------|
| `AccountStatusApplicationService.java` | accounting-core | 状态管理应用服务（编排层） |
| `AccountStatusController.java` | accounting-core | 账户状态管理 Controller（5 个接口） |
| `AccountStatusAssembler.java` | accounting-core | PO ↔ DTO 转换器 |
| `AccountFreezeRequest.java` | accounting-api | 冻结请求 DTO |
| `AccountUnfreezeRequest.java` | accounting-api | 解冻请求 DTO |
| `AccountCancelRequest.java` | accounting-api | 注销请求 DTO |
| `AccountRiskStatusRequest.java` | accounting-api | 风控状态变更请求 DTO |
| `AccountStatusResponse.java` | accounting-api | 状态查询响应 DTO |
| `AccountStatusChangeResponse.java` | accounting-api | 状态变更结果响应 DTO |

---

## 8. 完成标准（Checklist）

- [X] `AccountStatusApplicationService` 编排 5 个用例（冻结/解冻/注销/查询/风控变更）
  （**注：实际编排方式为直接调用领域服务，分布式锁和 TransactionTemplate 在 DomainService 内部管理，未按初稿放在 ApplicationService 层**）
- [X] `AccountStatusController` 实现 5 个接口（冻结/解冻/注销/查询/风控变更）
- [X] `AccountFreezeRequest` / `AccountUnfreezeRequest` / `AccountCancelRequest` / `AccountRiskStatusRequest` DTO
- [X] `AccountStatusResponse` / `AccountStatusChangeResponse` DTO
- [X] 所有 DTO 使用 `jakarta.validation` 注解校验
- [X] `AccountStatusAssembler` 完成 PO ↔ DTO 转换
- [X] `inactiveDate` 默认值 `1970-01-01` 在 Assembler 层转为 `null`
- [X] 冻结/解冻/注销使用 `DistributedLockTemplate`（leaseTime=-1 启用 watchdog）—— **由 DomainService 内部管理**
- [X] 风控变更不使用分布式锁，仅 TransactionTemplate —— **由 DomainService 内部管理**
- [X] Swagger/OpenAPI 注解完整（@Tag / @Operation）
- [X] `AccountStatusChangeResponse` 实际字段为：`accountNo` / `previousStatus` / `currentStatus` / `statusDesc` / `riskStatus` / `riskStatusDesc`（比初稿增加了风控状态字段，字段名调整为 previousStatus/currentStatus）
- [X] 单测全通：正常冻结/解冻/注销、状态转换非法、余额不为零注销、并发冻结、风控状态变更
