# step-08-java-c · 开户 Application Service + Controller + 事件通知

> **Step 8 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Java-A（`AccountNoGenerator` / 外部开户领域服务）、Java-B（内部开户 / 子账户创建 / `BatchOpenResult`）

---

## 1. 任务目标

实现开户功能的 Application Service 编排、Controller 接口和 DTO 层，完成自动化开户引擎的上层接口暴露，并预留开户事件 MQ 通知。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` DDL |
| 3 | `accounting-api/.../response/ApiResponse.java` | 统一响应体 |
| 4 | `accounting-api/.../response/PageResponse.java` | 分页响应体 |
| 5 | `accounting-api/.../request/PageRequest.java` | 分页请求基类 |
| 6 | `accounting-api/.../constant/ResultCode.java` | 错误码枚举 |
| 7 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 8 | Java-A 产出 | `AccountOpeningDomainService.openExternalAccount` |
| 9 | Java-B 产出 | `AccountOpeningDomainService.openInternalAccount` / `scanAndOpenInternalAccounts` / `BatchOpenResult` |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── AccountOpenRequest.java              # 外部开户请求 DTO
    │   └── InternalAccountOpenRequest.java      # 内部开户请求 DTO
    └── response/
        └── AccountOpenResponse.java             # 开户结果响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── service/
    │       └── AccountOpeningApplicationService.java  # 开户应用服务
    │   └── assembler/
    │       └── AccountOpeningAssembler.java     # PO ↔ Request/Response 转换
    └── controller/
        └── AccountOpeningController.java        # 开户管理 Controller
```

> **P2-4 修复**：包路径使用 `application/assembler/`，与项目现有转换器风格一致（如 `VoucherAssembler.java`）。

---

## 4. 接口契约

所有接口路径前缀：`/accounting/account/opening`

### 4.1 POST `/accounting/account/opening/external` — 外部客户开户

**请求体** (`AccountOpenRequest`):
```json
{
  "businessCode": "LOAN",
  "customerId": "CUST001",
  "customerType": 2,
  "subjectCode": "1001",
  "requestNo": "REQ20260301000001"
}
```

**校验规则**：
- `businessCode`：必填，长度 1-32
- `customerId`：必填，长度 1-64
- `customerType`：必填，1=个人，2=企业，99=其他
- `subjectCode`：**可选**，长度 1-32
- `requestNo`：必填，长度 1-64

**响应**：`ApiResponse<AccountOpenResponse>`

### 4.2 POST `/accounting/account/opening/internal` — 内部账户开户

**请求体** (`InternalAccountOpenRequest`):
```json
{
  "subjectCode": "1001"
}
```

**响应**：`ApiResponse<AccountOpenResponse>`

### 4.3 POST `/accounting/account/opening/internal/batch` — 批量扫描内部账户

**请求体**：无

**响应**：`ApiResponse<BatchOpenResultResponse>`

### 4.4 GET `/accounting/account/opening/{accountNo}` — 查询账户信息

**响应**：`ApiResponse<AccountOpenResponse>`

---

## 5. AccountOpeningApplicationService 实现要点

### 5.1 类结构

```java
@Service
@RequiredArgsConstructor
public class AccountOpeningApplicationService {

    private final AccountOpeningDomainService accountOpeningDomainService;
    private final AccountOpeningAssembler accountOpeningAssembler;
    private final AccountRepository accountRepository;  // 仅用于查询账户信息
}
```

> **分层决策（P1-2 修复）**：Application Service 通过注入 `AccountOpeningDomainService` 调用开户能力。领域服务内部完成持久化，Application Service 不再直接调用 Repository 做 insert/update。`AccountRepository` 仅用于 `selectByAccountNo` 查询操作（如 §4.4 查询账户信息接口）。

### 5.2 openExternalAccount 方法

```java
/**
 * 外部客户开户
 * 是否记账：否（账户准备阶段）
 * 异常处理：
 *   - 领域服务抛出的各类 AccountException → 直接向上抛出
 *   - 持久化异常 → 封装为 AccountException(ACCOUNT_CREATE_FAILED)
 */
public AccountOpenResponse openExternalAccount(AccountOpenRequest request) {
    AccountPO account = accountOpeningDomainService.openExternalAccount(
        request.getBusinessCode(),
        request.getCustomerId(),
        CustomerTypeEnum.fromValue(request.getCustomerType()),
        request.getSubjectCode(),
        request.getRequestNo()
    );

    // TODO Step 10 接入真实 MQ
    // localMessageService.send(new LocalMessage(
    //     "account.opened",
    //     account.getAccountNo(),
    //     JSON.toJSONString(buildAccountOpenedEvent(account))
    // ));
    log.info("Account opened event (reserved): accountNo={}", account.getAccountNo());

    return accountOpeningAssembler.toResponse(account);
}
```

**P1-2 修复说明**：开户持久化在 `AccountOpeningDomainService` 内部完成（通过 `TransactionTemplate` 管理事务），Application Service 只负责：
1. 将 Request DTO 转换为领域方法参数
2. 调用领域服务
3. 将返回的 PO 转换为 Response DTO
4. 预留事件通知

### 5.3 openInternalAccount 方法

```java
public AccountOpenResponse openInternalAccount(InternalAccountOpenRequest request) {
    AccountPO account = accountOpeningDomainService.openInternalAccount(
        request.getSubjectCode()
    );
    log.info("Internal account opened: accountNo={}", account.getAccountNo());
    return accountOpeningAssembler.toResponse(account);
}
```

### 5.4 batchOpenInternalAccounts 方法

```java
public BatchOpenResultResponse batchOpenInternalAccounts() {
    BatchOpenResult result = accountOpeningDomainService.scanAndOpenInternalAccounts();
    return accountOpeningAssembler.toBatchResponse(result);
}
```

### 5.5 getAccountInfo 方法

```java
public AccountOpenResponse getAccountInfo(String accountNo) {
    AccountPO account = accountRepository.selectByAccountNo(accountNo);
    if (account == null) {
        throw new AccountException(ResultCode.ACCOUNT_NOT_FOUND, "账户不存在: " + accountNo);
    }
    return accountOpeningAssembler.toResponse(account);
}
```

---

## 6. DTO 设计

### 6.1 AccountOpenRequest

```java
@Data
@Accessors(chain = true)
public class AccountOpenRequest {
    @NotBlank(message = "业务线编码不能为空")
    @Size(max = 32)
    private String businessCode;

    @NotBlank(message = "客户ID不能为空")
    @Size(max = 64)
    private String customerId;

    @NotNull(message = "客户类型不能为空")
    private Integer customerType;  // 1-个人, 2-企业, 99-其他

    @Size(max = 32)
    private String subjectCode;    // 可选，传入则精确匹配模板

    @NotBlank(message = "开户请求号不能为空")
    @Size(max = 64)
    private String requestNo;
}
```

> **P2-1 修复**：`subjectCode` 为可选字段。传入则精确匹配模板，不传则根据 businessCode + customerType 匹配首个启用模板。

### 6.2 InternalAccountOpenRequest

```java
@Data
@Accessors(chain = true)
public class InternalAccountOpenRequest {
    @NotBlank(message = "科目编码不能为空")
    @Size(max = 32)
    private String subjectCode;
}
```

### 6.3 AccountOpenResponse

```java
@Data
@Accessors(chain = true)
public class AccountOpenResponse {
    private String accountNo;       // 账户编号
    private String accountName;     // 账户名称
    private String subjectCode;     // 科目编码
    private String ownerId;         // 所有者ID
    private Integer status;         // 账户状态
    private LocalDate openDate;     // 开户日期
}
```

### 6.4 BatchOpenResultResponse

```java
@Data
@Accessors(chain = true)
public class BatchOpenResultResponse {
    private Integer totalCount;
    private Integer alreadyExists;
    private Integer newlyCreated;
    private Integer failed;
    private List<String> failedReasons;
}
```

---

## 7. AccountOpeningAssembler 实现要点

```java
@Component
public class AccountOpeningAssembler {

    public AccountOpenResponse toResponse(AccountPO po) {
        AccountOpenResponse response = new AccountOpenResponse();
        response.setAccountNo(po.getAccountNo());
        response.setAccountName(po.getAccountName());
        response.setSubjectCode(po.getSubjectCode());
        response.setOwnerId(po.getOwnerId());
        response.setStatus(po.getStatus() != null ? po.getStatus().getCode() : null);
        response.setOpenDate(po.getOpenDate());
        return response;
    }

    public BatchOpenResultResponse toBatchResponse(BatchOpenResult result) {
        BatchOpenResultResponse response = new BatchOpenResultResponse();
        response.setTotalCount(result.getTotalCount());
        response.setAlreadyExists(result.getAlreadyExists());
        response.setNewlyCreated(result.getNewlyCreated());
        response.setFailed(result.getFailed());
        response.setFailedReasons(result.getFailedReasons());
        return response;
    }
}
```

> 转换逻辑简洁，不包含业务判断。

---

## 8. 开户事件通知（预留）

在 Application Service 中预留事件发送点：

```java
// TODO Step 10 接入真实 MQ
log.info("Account opened event (reserved): accountNo={}", account.getAccountNo());
```

当前阶段只打印日志，不得引入 MQ 依赖。

---

## 9. 编码要点

- Controller 只做参数校验（`@Valid`）和调用 Application Service
- Application Service 负责用例编排和 DTO 转换，不含持久化逻辑
- 所有异常使用 `AccountException` + `ResultCode` 枚举
- 开户事件 MQ 当前只打印日志，不得发送真实消息
- 账户名称生成策略：外部账户用 customerId 截断至 32 字符，内部账户用 subjectCode

---

## 10. 完成标准

- [ ] `AccountOpeningApplicationService` 编排外部/内部开户用例
- [ ] `AccountOpeningController` 实现 4 个接口
- [ ] `AccountOpenRequest` / `InternalAccountOpenRequest` / `AccountOpenResponse` DTO 正确
- [ ] `AccountOpeningAssembler` 完成 PO ↔ DTO 转换（包路径 `application/assembler/`）
- [ ] 开户事件通知预留（当前只打印日志）
- [ ] 单测全通，含正常开户、模板缺失、模板未启用、重复开户场景

---

## 11. 下一步

Step 8 全部完成后，进入 **Step 9 · Journaling（业务流水入库）**，详见 `docs/prompt/step-09-journaling.md`。
