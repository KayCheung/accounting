# step-13-java-b · 风控状态变更 + 操作日志记录

> **Step 13 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 13 Java-A（Mapper/Repository 补充 + AccountStatusChangeDomainService 已就绪）

---

## 1. 任务目标

在 Java-A 的基础上，补充风控状态变更能力和操作日志记录。

核心职责：
1. `changeRiskStatus` 风控状态变更（独立于主状态）
2. 冻结/解冻操作 reason 日志记录（SLF4J MDC）

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范 |
| 3 | `docs/prompt/step-13-account-status.md` | Step 13 总体任务说明（§4.5 风控状态变更） |
| 4 | `docs/sql/1-account.sql` | `t_account` DDL |
| 5 | `accounting-core/.../enums/RiskStatusEnum.java` | 风控状态枚举（NORMAL=1/NO_IN=2/NO_OUT=3/NO_IN_OUT=4） |
| 6 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository（含 Java-A 补充的方法） |
| 7 | `accounting-core/.../domain/service/AccountStatusChangeDomainService.java` | Java-A 已实现 |

---

## 3. changeRiskStatus 执行流程

```
输入：accountNo + riskStatus
  ↓
1. 查询账户 → 不存在 → AccountException(ACCOUNT_NOT_FOUND)
  ↓
2. 同状态幂等：currentRiskStatus == newRiskStatus → 直接返回
  ↓
3. 在 TransactionTemplate 事务中：
   a. account.riskStatus = newRiskStatus
   b. accountRepository.updateRiskStatus(accountNo, newRiskStatus, oldVersion)
  ↓
4. 记录操作日志（SLF4J MDC）：风控状态变更 + 新值描述
  ↓
5. 返回更新后的 AccountPO
```

> **不需要分布式锁**：风控状态变更操作的是独立字段 `risk_status`，与主状态 `status` 互不干扰。过账引擎在执行 `PostingDomainService` 时会持有账户级分布式锁，读取 risk_status 与状态变更操作天然通过乐观锁互斥。

### 风控状态联动规则（日志记录用）

| riskStatus | 联动效果 |
|------------|---------|
| NO_IN(2) | 拒绝所有入账请求 |
| NO_OUT(3) | 拒绝所有出账请求 |
| NO_IN_OUT(4) | 同时拒绝入出账 |
| NORMAL(1) | 恢复所有入账出账能力 |

---

## 4. 方法签名（追加到 AccountStatusChangeDomainService）

```java
/**
 * 变更风控状态（独立操作，不依赖主状态）
 * 异常处理：
 *   - 账户不存在 → AccountException(ACCOUNT_NOT_FOUND)
 */
public AccountPO changeRiskStatus(String accountNo, RiskStatusEnum riskStatus);
```

---

## 5. 操作日志记录规范

冻结/解冻/注销/风控变更操作均须通过 SLF4J MDC 记录操作日志：

```java
// 示例：冻结操作日志
MDC.put("accountNo", accountNo);
MDC.put("operation", "freeze");
MDC.put("reason", reason);
MDC.put("fromStatus", oldStatus.getCode().toString());
MDC.put("toStatus", AccountStatusEnum.FROZEN.getCode().toString());
log.info("[ACCOUNT-STATUS-CHANGE] 账户状态变更: accountNo={}, operation={}, reason={}, from={}, to={}",
    accountNo, "freeze", reason, oldStatus.getDesc(), AccountStatusEnum.FROZEN.getDesc());
MDC.clear();
```

> **注意**：当前阶段 `t_account` 表无 remark 字段，reason 仅留存日志；后续可考虑追加 remark 字段。

---

## 6. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountStatusChangeDomainService.java` | 追加方法 | `changeRiskStatus` + MDC 日志记录 |

---

## 7. 完成标准（Checklist）

- [X] `changeRiskStatus` 风控状态变更（含账户存在性校验 + 同状态幂等 + 乐观锁版本更新）
- [X] 风控变更不需要分布式锁，仅依赖 TransactionTemplate + 乐观锁兜底
- [X] 风控变更在 TransactionTemplate 事务中完成
- [X] 冻结/解冻/注销/风控变更操作的 reason 通过 SLF4J MDC 记录到日志
- [X] 日志格式统一：`[ACCOUNT_STATUS]` 前缀 + accountNo + operation + reason + fromStatus + toStatus
  （**注：实际前缀为 `[ACCOUNT_STATUS]`，非初稿中的 `[ACCOUNT-STATUS-CHANGE]`**）
- [X] 风控变更日志记录新值描述（如"止入"、"止出"、"止入止出"、"正常"）
- [X] 单测覆盖：风控状态变更正常流程、账户不存在拦截（同状态幂等由冻结/解冻/注销的幂等路径间接覆盖）
