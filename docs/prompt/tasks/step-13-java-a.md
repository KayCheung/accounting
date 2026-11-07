# step-13-java-a · P0 补充 + AccountStatusChangeDomainService（冻结/解冻/注销）

> **Step 13 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 8（账户开户）、Step 11（事务管理 + 过账状态检查）、Step 12（过账引擎）

---

## 1. 任务目标

完成 Step 13 三项前置补充任务（P0-1~P0-3），并实现账户状态变更领域服务。

核心职责：
1. 补充 Mapper/Repository 状态变更方法
2. 实现 AccountStatusChangeDomainService（冻结/解冻/注销核心业务逻辑）
3. 状态机校验（合法转换表 + 同状态幂等）
4. 并发安全：分布式锁 + 双重检查 + 乐观锁
5. 注销前置校验（主账户余额为零 + 子账户可用/冻结余额为零）

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、余额方向约束 |
| 3 | `docs/prompt/step-13-account-status.md` | Step 13 总体任务说明 |
| 4 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` DDL |
| 5 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 6 | `accounting-core/.../entity/SubAccountPO.java` | 子账户 PO |
| 7 | `accounting-core/.../enums/AccountStatusEnum.java` | 账户状态枚举（NORMAL=1/FROZEN=2/CANCELLED=3） |
| 8 | `accounting-core/.../enums/RiskStatusEnum.java` | 风控状态枚举 |
| 9 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 10 | `accounting-core/.../repository/SubAccountRepository.java` | 子账户 Repository |
| 11 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 12 | `accounting-core/.../config/TransactionConfig.java` | TransactionTemplate Bean |

---

## 3. P0 前置补充任务（开始编码前必须完成）

### P0-1: AccountMapper 补充 `updateStatusByAccountNo`

在 `AccountMapper.java` 中追加方法：

```java
/**
 * 按 accountNo 更新账户状态（乐观锁）
 */
int updateStatusByAccountNo(@Param("accountNo") String accountNo,
    @Param("status") AccountStatusEnum status, @Param("version") Long version);
```

对应 XML（在 `resources/mapper/AccountMapper.xml` 中追加）：

```xml
<update id="updateStatusByAccountNo">
    UPDATE t_account
    SET status = #{status.code}, version = version + 1
    WHERE account_no = #{accountNo} AND version = #{version} AND is_delete = 0
</update>
```

### P0-2: AccountMapper 补充 `updateRiskStatusByAccountNo`

在 `AccountMapper.java` 中追加方法：

```java
/**
 * 按 accountNo 更新账户风控状态（乐观锁）
 */
int updateRiskStatusByAccountNo(@Param("accountNo") String accountNo,
    @Param("riskStatus") RiskStatusEnum riskStatus, @Param("version") Long version);
```

对应 XML：

```xml
<update id="updateRiskStatusByAccountNo">
    UPDATE t_account
    SET risk_status = #{riskStatus.code}, version = version + 1
    WHERE account_no = #{accountNo} AND version = #{version} AND is_delete = 0
</update>
```

### P0-3: AccountRepository 补充状态变更方法

在 `AccountRepository.java` 中追加封装方法：

```java
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

## 4. AccountStatusChangeDomainService（状态变更领域服务）

新建 `accounting-core/.../domain/service/AccountStatusChangeDomainService.java`。

```java
@Service
@RequiredArgsConstructor
public class AccountStatusChangeDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final DistributedLockTemplate distributedLockTemplate;
}
```

### 4.1 状态机校验

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
            String.format("账户状态不允许转换: %s -> %s", current.getDesc(), target.getDesc()));
    }
}
```

> **注意**：`current == target` 的幂等检查必须在转换表校验之前，否则 NORMAL → NORMAL 等场景会被拦截为非法转换。

### 4.2 freezeAccount 执行流程

```
输入：accountNo + reason
  ↓
1. 查询账户 → 不存在 → AccountException(ACCOUNT_NOT_FOUND)
  ↓
2. validateTransition(current=NORMAL, target=FROZEN) → 非法 → ACCOUNT_STATUS_TRANSITION_INVALID
  ↓
3. 在 TransactionTemplate 事务中：
   a. account.status = FROZEN
   b. accountRepository.updateStatus(accountNo, FROZEN, oldVersion)
  ↓
4. 记录操作日志（SLF4J MDC）：reason = "账户冻结"
  ↓
5. 返回更新后的 AccountPO
```

### 4.3 unfreezeAccount 执行流程

```
输入：accountNo
  ↓
1. 查询账户 → 不存在 → AccountException(ACCOUNT_NOT_FOUND)
  ↓
2. validateTransition(current=FROZEN, target=NORMAL) → 非法 → ACCOUNT_STATUS_TRANSITION_INVALID
  ↓
3. 在 TransactionTemplate 事务中：
   a. account.status = NORMAL
   b. accountRepository.updateStatus(accountNo, NORMAL, oldVersion)
  ↓
4. 记录操作日志（SLF4J MDC）：reason = "账户解冻"
  ↓
5. 返回更新后的 AccountPO
```

### 4.4 cancelAccount 执行流程

```
输入：accountNo + reason
  ↓
1. 查询账户 → 不存在 → AccountException(ACCOUNT_NOT_FOUND)
  ↓
2. 账户已注销 → AccountException(ACCOUNT_STATUS_TRANSITION_INVALID)
  ↓
3. 主账户余额 balance != 0 → AccountException(ACCOUNT_BALANCE_NOT_ZERO)
  ↓
4. 子账户可用余额 != 0 → AccountException(ACCOUNT_BALANCE_NOT_ZERO)
  ↓
5. 子账户冻结余额 != 0 → AccountException(ACCOUNT_BALANCE_NOT_ZERO)
  ↓
6. 在 TransactionTemplate 事务中：
   a. account.status = CANCELLED
   b. account.inactiveDate = 当前日期（语义复用：注销场景下为"注销生效日"）
   c. accountRepository.updateStatus(accountNo, CANCELLED, oldVersion)
  ↓
7. 记录操作日志（SLF4J MDC）：reason
  ↓
8. 返回更新后的 AccountPO
```

> **注销子账户余额校验**：调用 `subAccountRepository.selectByAccountNo(accountNo)` 获取所有子账户，分别检查 `balance_type=1`（可用余额）和 `balance_type=2`（冻结余额）的 balance 是否均为零。

### 4.5 方法签名

```java
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
 *   - 子账户可用余额 + 冻结余额均为零
 *   - 账户未注销
 * 异常处理：
 *   - 账户不存在 → AccountException(ACCOUNT_NOT_FOUND)
 *   - 已注销 → AccountException(ACCOUNT_STATUS_TRANSITION_INVALID)
 *   - 余额不为零 → AccountException(ACCOUNT_BALANCE_NOT_ZERO)
 */
public AccountPO cancelAccount(String accountNo, String reason);
```

---

## 5. 需要创建的文件清单

| 文件 | 模块 | 说明 |
|------|------|------|
| `AccountStatusChangeDomainService.java` | accounting-core | 状态变更领域服务 |

---

## 6. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountMapper.java` | 追加方法 | `updateStatusByAccountNo` + `updateRiskStatusByAccountNo` |
| `AccountMapper.xml` | 追加 SQL | 对应 XML |
| `AccountRepository.java` | 追加方法 | `updateStatus` + `updateRiskStatus` |

---

## 7. 完成标准（Checklist）

- [X] P0-1: `AccountMapper.updateStatusByAccountNo` + XML
- [X] P0-2: `AccountMapper.updateRiskStatusByAccountNo` + XML
- [X] P0-3: `AccountRepository.updateStatus` / `updateRiskStatus`（含乐观锁冲突拦截）
- [X] 合法状态转换表（NORMAL↔FROZEN、NORMAL/FROZEN→CANCELLED、CANCELLED 无出边）
- [X] 同状态幂等处理（`current == target` 在转换表校验之前提前返回）
- [X] `freezeAccount` 冻结（NORMAL → FROZEN）+ MDC 操作日志
- [X] `unfreezeAccount` 解冻（FROZEN → NORMAL）+ MDC 操作日志
- [X] `cancelAccount` 注销（余额为零校验 + 子账户可用/冻结余额为零校验 + inactiveDate 设置）
- [X] 分布式锁 + 双重检查 + 乐观锁兜底（**注：锁和事务控制权在 DomainService 内部，未放在 ApplicationService**）
- [X] 依赖使用构造器注入，加 `@Service` 注解
- [X] 异常体系使用 `AccountException`，ResultCode 枚举无魔法值
- [X] 单测覆盖：18 个用例（正常冻结/解冻/注销、状态转换非法、余额不为零注销、并发冻结、子账户余额非零注销、账户不存在、查询状态）
- [X] `changeRiskStatus` 风控状态变更也在同一 DomainService 中实现（详见 java-b 任务文件）
- [X] 日志前缀实际为 `[ACCOUNT_STATUS]`（非任务文件初稿中的 `[ACCOUNT-STATUS-CHANGE]`）
