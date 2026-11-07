# step-13-account-status · 账户状态管理

> **Phase 5 第一步** | 归属：`@Java` 工程师
> 前置依赖：Step 8（账户开户）、Step 11（事务管理 + 过账状态检查）、Step 12（过账引擎）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `AccountMapper` 补充 `updateStatusByAccountNo` | `AccountMapper.java` + `AccountMapper.xml` | 按 accountNo 更新 status（乐观锁） |
| P0-2 | `AccountMapper` 补充 `updateRiskStatusByAccountNo` | `AccountMapper.java` + `AccountMapper.xml` | 按 accountNo 更新 risk_status（乐观锁） |
| P0-3 | `AccountRepository` 补充状态变更方法 | `AccountRepository.java` | 封装 Mapper 调用，含乐观锁版本校验 |

### P0-1 & P0-2 Mapper 方法定义

```java
// AccountMapper.java
int updateStatusByAccountNo(@Param("accountNo") String accountNo,
    @Param("status") AccountStatusEnum status, @Param("version") Long version);
int updateRiskStatusByAccountNo(@Param("accountNo") String accountNo,
    @Param("riskStatus") RiskStatusEnum riskStatus, @Param("version") Long version);
```

**XML 片段**（status 变更，risk_status 同理）：

```xml
<update id="updateStatusByAccountNo">
    UPDATE t_account
    SET status = #{status.code}, version = version + 1
    WHERE account_no = #{accountNo} AND version = #{version} AND is_delete = 0
</update>
```

### P0-3 Repository 方法

```java
// AccountRepository.java
public void updateStatus(String accountNo, AccountStatusEnum status, Long version) {
    int affected = accountMapper.updateStatusByAccountNo(accountNo, status, version);
    if (affected == 0) {
        throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "账户状态更新冲突: " + accountNo);
    }
}

public void updateRiskStatus(String accountNo, RiskStatusEnum riskStatus, Long version) {
    int affected = accountMapper.updateRiskStatusByAccountNo(accountNo, riskStatus, version);
    if (affected == 0) {
        throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "账户风控状态更新冲突: " + accountNo);
    }
}
```

> 上述三个补充任务由 Java-A 工程师在开始编码前一并完成，不单独拆分子任务文件。

---

## 1. 任务目标（Mission）

实现账户状态管理能力，支持以下操作：

1. **账户冻结/解冻**：设置或恢复 `t_account.status` 主状态
2. **账户注销**：将账户标记为注销（不可逆操作）
3. **状态查询**：返回账户当前主状态与风控状态
4. **风控状态变更**：独立于主状态，修改 `t_account.risk_status`

> 注意：本 Step 仅做**账户级状态管控**，不涉及资金冻结/解冻操作。资金冻结（子账户余额转移 + 冻结明细记录）在 Step 14 实现。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、余额方向约束 |
| 3 | `docs/design/domain-model.md` | 账户域模型 |
| 4 | `docs/sql/1-account.sql` | `t_account` DDL |
| 5 | `docs/design/flowchart/freeze_unfreeze_flow.mmd` | 冻结/解冻流程图（Step 14 会用到，此处参考账户级冻结逻辑） |
| 6 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 7 | `accounting-core/.../enums/AccountStatusEnum.java` | 账户状态枚举 |
| 8 | `accounting-core/.../enums/RiskStatusEnum.java` | 风控状态枚举 |
| 9 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 10 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 11 | `accounting-core/.../config/TransactionConfig.java` | TransactionTemplate Bean |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-13-java-a.md` | P0-1/P0-2/P0-3 补充 + 状态变更领域服务 + 冻结/解冻/注销 + 状态机校验 + 子账户余额校验 |
| Java-B | `docs/prompt/tasks/step-13-java-b.md` | 风控状态变更 + 操作日志记录 |
| Java-C | `docs/prompt/tasks/step-13-java-c.md` | Application Service + Controller（5 个接口）+ DTO + Assembler |

> **依赖关系**：Java-A 最先完成（Mapper/Repository 补充 + 领域服务是基础设施）。Java-B 依赖 Java-A 的 Repository 方法。Java-C 依赖 Java-A/B 的领域服务。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 账户主状态状态机

```
NORMAL(1 正常) ──(冻结)──→ FROZEN(2 冻结)
NORMAL(1 正常) ──(注销)──→ CANCELLED(3 注销)
FROZEN(2 冻结) ──(解冻)──→ NORMAL(1 正常)
FROZEN(2 冻结) ──(注销)──→ CANCELLED(3 注销)
CANCELLED(3 注销) ──(不可逆转)──→ X
```

**非法转换拦截**：
- CANCELLED → 任何状态：抛出 `ACCOUNT_STATUS_TRANSITION_INVALID`
- 同状态变更（如 NORMAL → NORMAL）：直接返回成功（幂等）

### 4.2 冻结操作

```
前置校验：
  - 账户必须存在
  - 当前状态必须为 NORMAL（仅正常账户可冻结）
执行动作：
  - t_account.status = FROZEN
  - 操作日志记录 reason（通过 SLF4J MDC 记录，当前阶段 t_account 表无 remark 字段，reason 仅留存日志；后续可考虑追加 remark 字段）
后置影响：
  - 过账引擎（PostingDomainService）已拦截非 NORMAL 账户，冻结后自动无法过账
```

### 4.3 解冻操作

```
前置校验：
  - 账户必须存在
  - 当前状态必须为 FROZEN（仅冻结账户可解冻）
执行动作：
  - t_account.status = NORMAL
  - 操作日志记录解冻操作
```

### 4.4 注销操作

```
前置校验：
  - 账户必须存在
  - 当前状态不能为 CANCELLED（不可重复注销）
  - 当前状态必须为 NORMAL 或 FROZEN
  - 主账户余额 balance 必须为 0（余额不为零禁止注销）
  - ⚠️ 子账户余额校验：可用子账户余额 + 冻结子账户余额必须均为零（资金未清零禁止注销）
执行动作：
  - t_account.status = CANCELLED
  - t_account.inactiveDate = 当前日期（语义复用：DDL 原注释为"动支日期"，注销场景下复用为"注销生效日"）
  - 操作日志记录 reason（通过 SLF4J MDC 记录，当前阶段 t_account 表无 remark 字段，reason 仅留存日志）
后置影响：
  - 注销后不可恢复（不可逆操作）
  - 过账引擎已拦截 CANCELLED 账户
```

### 4.5 风控状态变更

```
前置校验：
  - 账户必须存在（调用 accountRepository.selectByAccountNo）
执行动作：
  - t_account.risk_status = 新值
  - 独立于主状态，可与任何主状态共存
  - 在 TransactionTemplate 事务中完成，依赖乐观锁 version 兜底

风控状态联动规则：
  - NO_IN(止入)：拒绝所有入账请求
  - NO_OUT(止出)：拒绝所有出账请求
  - NO_IN_OUT(止入止出)：同时拒绝入出账
  - NORMAL(正常)：恢复所有入账出账能力
```

### 4.6 并发安全

所有状态变更操作必须：
1. 加分布式锁：业务 lockKey 为 `account:status:{accountNo}`（不含租户前缀）
2. 双重检查：查询当前状态 → 加锁 → 再次查询当前状态 → 执行变更
3. 使用乐观锁版本校验兜底（`version` 字段）

> **锁 Key 租户隔离说明**：`DistributedLockTemplate.execute()` 内部通过 `LOCK_KEY_FORMAT = "accounting:%s:lock:%s"` 自动包装，
> tenantId 从 `TenantContext` 自动注入。调用方只需传入业务 lockKey（如 `account:status:{accountNo}`），
> 最终 Redis 中的完整 Key 为 `accounting:{tenantId}:lock:account:status:{accountNo}`。

---

## 5. 需要创建/修改的文件

### 新建

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── AccountFreezeRequest.java              # 冻结请求 DTO
    │   ├── AccountUnfreezeRequest.java            # 解冻请求 DTO
    │   ├── AccountCancelRequest.java              # 注销请求 DTO
    │   └── AccountRiskStatusRequest.java          # 风控状态变更请求 DTO
    └── response/
        ├── AccountStatusResponse.java             # 账户状态查询响应 DTO
        └── AccountStatusChangeResponse.java       # 状态变更结果响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       └── AccountStatusChangeDomainService.java  # 状态变更领域服务（核心业务逻辑）
    ├── application/
    │   ├── service/
    │   │   └── AccountStatusApplicationService.java   # 状态管理应用服务（用例编排）
    │   └── assembler/
    │       └── AccountStatusAssembler.java        # PO ↔ Request/Response 转换
    └── interfaces/
        └── AccountStatusController.java           # 账户状态管理 Controller
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `AccountMapper.java` | 新增 `updateStatusByAccountNo`、`updateRiskStatusByAccountNo` |
| `AccountMapper.xml` | 新增上述两个方法的 SQL |
| `AccountRepository.java` | 新增 `updateStatus`、`updateRiskStatus` 方法 |
| `ResultCode.java` | 新增错误码（见下方） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"3014"` | `ACCOUNT_STATUS_TRANSITION_INVALID` | 账户状态转换非法（**与 3005 的区别**：3005 表示"状态本身非法，无法操作"，3014 表示"状态转换路径不合法"） |
| `"3015"` | `ACCOUNT_BALANCE_NOT_ZERO` | 账户余额不为零，无法注销（含主账户 + 子账户可用/冻结余额） |

---

## 6. 接口契约

所有接口路径前缀：`/accounting/account/status`

### 6.1 POST `/accounting/account/status/freeze` — 冻结账户

**请求体** (`AccountFreezeRequest`):
```json
{
  "accountNo": "00120260301000001",
  "reason": "客户异常交易风控"
}
```

**校验规则**：
- `accountNo`：必填，长度 1-32
- `reason`：选填，长度 1-128（记录冻结原因）

**业务逻辑**：
1. 检查账户是否存在
2. 检查当前状态是否为 NORMAL
3. 加分布式锁 `account:status:{accountNo}`
4. 双重检查当前状态
5. 更新 `t_account.status = FROZEN`
6. 返回变更前/后状态

**错误码**：
- `ACCOUNT_NOT_FOUND`：账户不存在
- `ACCOUNT_STATUS_TRANSITION_INVALID`：当前状态不允许冻结（非 NORMAL）
- `OPTIMISTIC_LOCK_FAILED`：并发冲突

### 6.2 POST `/accounting/account/status/unfreeze` — 解冻账户

**请求体** (`AccountUnfreezeRequest`):
```json
{
  "accountNo": "00120260301000001"
}
```

**业务逻辑**：
1. 检查账户是否存在
2. 检查当前状态是否为 FROZEN
3. 加分布式锁
4. 双重检查
5. 更新 `t_account.status = NORMAL`
6. 返回变更前/后状态

**错误码**：
- `ACCOUNT_NOT_FOUND`：账户不存在
- `ACCOUNT_STATUS_TRANSITION_INVALID`：当前状态非 FROZEN，无法解冻

### 6.3 POST `/accounting/account/status/cancel` — 注销账户

**请求体** (`AccountCancelRequest`):
```json
{
  "accountNo": "00120260301000001",
  "reason": "客户申请销户"
}
```

**业务逻辑**：
1. 检查账户是否存在
2. 检查当前状态不能为 CANCELLED
3. 检查 `balance == 0`（主账户余额）
4. 检查子账户可用余额 + 冻结余额均为零（调用 `SubAccountRepository.selectByAccountNo`）
5. 加分布式锁
6. 双重检查（步骤 3-4 的预检查在锁外执行，由本步双重检查兜底，保证加锁瞬间余额仍为零）
7. 更新 `t_account.status = CANCELLED`
8. 设置 `t_account.inactiveDate = 当前日期`
9. 返回变更前/后状态

**错误码**：
- `ACCOUNT_NOT_FOUND`：账户不存在
- `ACCOUNT_STATUS_TRANSITION_INVALID`：账户已注销
- `ACCOUNT_BALANCE_NOT_ZERO`：账户余额不为零，无法注销

### 6.4 GET `/accounting/account/status/{accountNo}` — 查询账户状态

**响应** (`AccountStatusResponse`):
```json
{
  "accountNo": "00120260301000001",
  "accountName": "CUST001",
  "subjectCode": "1001",
  "status": 1,
  "statusDesc": "正常",
  "riskStatus": 1,
  "riskStatusDesc": "正常",
  "balance": 1000.000000,
  "openDate": "2026-03-01",
  "inactiveDate": null
}
```

> **Assembler 处理**：当 `inactiveDate` 为 DDL 默认值 `1970-01-01` 时，
> Assembler 层转为 `null` 返回，前端显示"未注销"。

### 6.5 POST `/accounting/account/status/risk` — 变更风控状态

**请求体** (`AccountRiskStatusRequest`):
```json
{
  "accountNo": "00120260301000001",
  "riskStatus": 2
}
```

**校验规则**：
- `accountNo`：必填，长度 1-32
- `riskStatus`：必填，1=正常，2=止入，3=止出，4=止入止出

**业务逻辑**：
1. 检查账户是否存在
2. 更新 `t_account.risk_status`
3. 风控状态变更不需要分布式锁（独立字段，与主状态互不干扰）
4. 返回变更前/后状态

---

## 7. AccountStatusChangeDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class AccountStatusChangeDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final DistributedLockTemplate distributedLockTemplate;

    /**
     * 冻结账户（status: NORMAL → FROZEN）
     * 异常处理：
     *   - 账户不存在 → AccountException(ACCOUNT_NOT_FOUND)
     *   - 当前状态非 NORMAL → AccountException(ACCOUNT_STATUS_TRANSITION_INVALID)
     */
    public AccountPO freezeAccount(String accountNo, String reason);

    /**
     * 解冻账户（status: FROZEN → NORMAL）
     * 异常处理：
     *   - 账户不存在 → AccountException(ACCOUNT_NOT_FOUND)
     *   - 当前状态非 FROZEN → AccountException(ACCOUNT_STATUS_TRANSITION_INVALID)
     */
    public AccountPO unfreezeAccount(String accountNo);

    /**
     * 注销账户（status: NORMAL/FROZEN → CANCELLED）
     * 前置校验：
     *   - 账户余额 balance == 0
     *   - 子账户可用余额 + 冻结余额均为零（调用 SubAccountRepository.selectByAccountNo 校验）
     *   - 账户未注销
     * 异常处理：
     *   - 账户不存在 → AccountException(ACCOUNT_NOT_FOUND)
     *   - 已注销 → AccountException(ACCOUNT_STATUS_TRANSITION_INVALID)
     *   - 余额不为零 → AccountException(ACCOUNT_BALANCE_NOT_ZERO)
     */
    public AccountPO cancelAccount(String accountNo, String reason);

    /**
     * 变更风控状态（独立操作，不依赖主状态）
     */
    public AccountPO changeRiskStatus(String accountNo, RiskStatusEnum riskStatus);

    /**
     * 查询账户状态（返回完整 PO）
     */
    public AccountPO queryAccountStatus(String accountNo);
}
```

---

## 8. 编码要点

### 8.1 事务边界

- 冻结/解冻/注销操作在 `TransactionTemplate` 事务中完成
- 风控状态变更也在 `TransactionTemplate` 事务中
- 分布式锁包裹整个事务执行

```java
// ApplicationService 编排示例
public AccountStatusChangeResponse freeze(AccountFreezeRequest request) {
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

> **leaseTime 策略**：必须使用 `-1` 启用 Redisson watchdog 自动续期（30s）。
> 若传固定值（如 10s），watchdog 不生效，事务内含复杂查询逻辑时可能导致锁提前释放。

### 8.2 并发安全

- 锁 Key 格式：业务 lockKey = `account:status:{accountNo}`（由 DistributedLockTemplate 自动包装为 `accounting:{tenantId}:lock:{lockKey}`）
- 冻结/解冻/注销：使用 `DistributedLockTemplate` + 双重检查 + 乐观锁
- 风控状态变更：使用乐观锁兜底，不加分布式锁。
  > 说明：过账引擎在执行 `PostingDomainService` 时会持有账户级分布式锁，
  > 读取 risk_status 与状态变更操作天然通过乐观锁互斥。
  > 若后续发现过账路径存在不持锁读 risk_status 的场景，则风控变更也需加分布式锁。

### 8.3 状态机校验

```java
// 合法状态转换表
private static final Map<AccountStatusEnum, List<AccountStatusEnum>> VALID_TRANSITIONS = Map.of(
    AccountStatusEnum.NORMAL, List.of(AccountStatusEnum.FROZEN, AccountStatusEnum.CANCELLED),
    AccountStatusEnum.FROZEN, List.of(AccountStatusEnum.NORMAL, AccountStatusEnum.CANCELLED)
    // CANCELLED 无任何出边
);

// 校验方法（含幂等处理）
private void validateTransition(AccountStatusEnum current, AccountStatusEnum target) {
    if (current == target) return; // 同状态变更，幂等放行
    List<AccountStatusEnum> allowed = VALID_TRANSITIONS.getOrDefault(current, Collections.emptyList());
    if (!allowed.contains(target)) {
        throw new AccountException(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID,
            String.format("账户状态不允许转换: %s → %s", current.getDesc(), target.getDesc()));
    }
}
```

> **注意**：`current == target` 的幂等检查必须在转换表校验之前，
> 否则 NORMAL → NORMAL 等场景会被 `VALID_TRANSITIONS` 拦截为非法转换。

### 8.4 异常体系

| 场景 | 异常类型 | ResultCode |
|------|---------|-----------|
| 账户不存在 | AccountException | ACCOUNT_NOT_FOUND |
| 状态转换非法 | AccountException | ACCOUNT_STATUS_TRANSITION_INVALID |
| 余额不为零无法注销 | AccountException | ACCOUNT_BALANCE_NOT_ZERO |
| 乐观锁冲突 | AccountException | OPTIMISTIC_LOCK_FAILED |
| 获取分布式锁失败 | ServiceException | LOCK_ACQUIRE_FAILED |

---

## 9. 完成标准（Checklist）

### Java-A
- [ ] P0-1: `AccountMapper.updateStatusByAccountNo` + XML
- [ ] P0-2: `AccountMapper.updateRiskStatusByAccountNo` + XML
- [ ] P0-3: `AccountRepository.updateStatus` / `updateRiskStatus`
- [ ] `AccountStatusChangeDomainService.freezeAccount` 冻结（NORMAL → FROZEN）
- [ ] `AccountStatusChangeDomainService.unfreezeAccount` 解冻（FROZEN → NORMAL）
- [ ] `AccountStatusChangeDomainService.cancelAccount` 注销（余额为零校验 + 子账户余额校验 + 不可逆 + inactiveDate 设置）
- [ ] 状态机校验（合法转换表 + 同状态幂等）
- [ ] 并发安全：`DistributedLockTemplate` + 双重检查 + 乐观锁

### Java-B
- [ ] `AccountStatusChangeDomainService.changeRiskStatus` 风控状态变更（含账户存在性校验 + 乐观锁版本更新）
- [ ] 冻结/解冻操作 reason 日志记录（SLF4J MDC）

### Java-C
- [ ] `AccountStatusApplicationService` 编排 5 个用例
- [ ] `AccountStatusController` 实现 5 个接口
- [ ] `AccountFreezeRequest` / `AccountUnfreezeRequest` / `AccountCancelRequest` / `AccountRiskStatusRequest` DTO
- [ ] `AccountStatusResponse` / `AccountStatusChangeResponse` DTO
- [ ] `AccountStatusAssembler` 完成 PO ↔ DTO 转换
- [ ] 单测全通，含：正常冻结/解冻/注销、状态转换非法、余额不为零注销、并发冻结、风控状态变更

### TL Review
- [ ] 状态机转换表完整性（覆盖所有合法/非法转换）
- [ ] 同状态幂等处理正确（validateTransition 中 current == target 提前返回）
- [ ] 注销前置校验正确性（主账户余额为零 + 子账户余额为零 + 未注销状态）
- [ ] 事务边界正确（TransactionTemplate，非 @Transactional）
- [ ] 并发安全三层防护（分布式锁 + 双重检查 + 乐观锁），leaseTime = -1 启用 watchdog
- [ ] 锁 Key 租户隔离正确（DistributedLockTemplate 自动注入 tenantId）
- [ ] 风控状态变更独立于主状态（乐观锁兜底 + TransactionTemplate 事务控制）
- [ ] inactiveDate 默认值处理（Assembler 层将 1970-01-01 转为 null）
- [ ] 异常体系使用 AccountException，ResultCode 枚举无魔法值
- [ ] 3014（转换非法）与 3005（状态非法）语义区分清晰
- [ ] 领域服务加 @Service 注解，构造器注入依赖

---

## 10. 与后续 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 14（资金冻结解冻） | 复用本 Step 的分布式锁模板 + 事务模式；Step 14 操作子账户余额，本 Step 操作主账户状态 |
| Step 15（余额查询 API） | 可复用本 Step 的 `queryAccountStatus` 能力 |
| Step 11（过账引擎增强） | 后续可在过账流程中接入风控状态拦截（NO_IN / NO_OUT） |

---

## 11. 下一步行动

进入 **Step 14 · Freeze & Unfreeze（资金冻结与解冻）**，详见 `docs/prompt/step-14-freeze.md`。
