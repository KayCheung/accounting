# step-08-java-b · 内部账户开户 + 子账户自动创建 + 批量扫描

> **Step 8 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Java-A（`AccountNoGenerator` / `AccountOpeningDomainService` 基础方法）、Step 5（`AccountRepository` / `SubAccountRepository`）

---

## 1. 任务目标

在 `AccountOpeningDomainService` 中补充内部账户开户能力，实现子账户自动创建和初始化余额逻辑，支持批量扫描开户。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范 |
| 2 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` DDL |
| 3 | `docs/sql/4-subject.sql` | `t_account_subject` DDL |
| 4 | `docs/design/flowchart/account_opening_flow.mmd` | 开户流程图（内部账户部分） |
| 5 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 6 | `accounting-core/.../entity/SubAccountPO.java` | 子账户 PO |
| 7 | `accounting-core/.../repository/AccountRepository.java` | 已有 Repository |
| 8 | `accounting-core/.../repository/SubAccountRepository.java` | 已有 Repository |
| 9 | `accounting-core/.../repository/SubjectRepository.java` | 已有 Repository |
| 10 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 11 | Java-A 产出 | `AccountNoGenerator` / `AccountOpeningDomainService` 已有方法 |

---

## 3. 需要修改的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    └── domain/
        └── service/
            └── AccountOpeningDomainService.java  # 修改：新增内部开户方法
```

> 同时需要在 `accounting-core/.../domain/` 下创建 `BatchOpenResult.java` 简单结果类。

---

## 4. 内部账户开户实现要点

### 4.1 openInternalAccount 方法

```java
/**
 * 内部账户开户（完成持久化后返回 AccountPO）
 * 是否记账：否
 * 异常处理：
 *   - 科目不存在 → AccountException(SUBJECT_NOT_FOUND)
 *   - 科目非末级 → AccountException(SUBJECT_NOT_LEAF)
 *   - 科目不允许开户 → AccountException(SUBJECT_NOT_ALLOW_OPEN_ACCOUNT)
 *   - 内部账户已存在 → AccountException(ACCOUNT_ALREADY_EXISTS)
 *
 * @param subjectCode 科目编码
 * @return AccountPO 新创建的内部账户
 */
public AccountPO openInternalAccount(String subjectCode);
```

**执行流程**：
1. 通过 `SubjectRepository.selectByCode()` 查询科目是否存在，校验 `isLeaf=true` 且 `allowOpenAccount=1`
2. 检查内部账户是否已存在（`ownerId=INNER + subjectCode`）
3. 调用 `AccountNoGenerator.generateInternalAccountNo(subjectCode)` 生成编号（**永久递增，不重置**）
4. 构建 AccountPO：
   - `ownerId = "INNER"`
   - `ownerType = OwnerTypeEnum.OTHER`（99）
   - `accountType = "INTERNAL"`（字典 CODE）
   - `subjectCode` = 传入参数
   - `accountName` = subjectCode（内部账户名称简单使用科目编码）
   - `status = AccountStatusEnum.NORMAL`
   - `balance = BigDecimal.ZERO`
   - `openDate = LocalDate.now()`
   - `riskStatus = RiskStatusEnum.NORMAL`
   - `balanceDirection` 继承科目的 `debitCredit`
5. 在 `TransactionTemplate` 事务中：
   a. insert 主账户
   b. 调用 `createSubAccountsInternal()` 创建两个子账户并 insert
6. 返回 AccountPO

### 4.2 scanAndOpenInternalAccounts 方法

```java
/**
 * 批量扫描并创建内部账户
 * 注意：非事务性操作，每个账户独立 try-catch，失败不回滚其他
 * @return BatchOpenResult 包含 totalCount / alreadyExists / newlyCreated / failed
 */
public BatchOpenResult scanAndOpenInternalAccounts();
```

**执行流程**：
1. 调用 `SubjectRepository.selectAllowOpenAccountLeafSubjects()` 获取待扫描科目列表
2. 逐个遍历，每个账户执行：
   ```java
   try {
       distributedLockTemplate.execute(
           "account:open:INNER:" + subjectCode,
           2, -1,
           () -> openInternalAccount(subjectCode)
       );
       result.incrementNewlyCreated();
   } catch (AccountException e) {
       if (e.getResultCode() == ResultCode.ACCOUNT_ALREADY_EXISTS) {
           result.incrementAlreadyExists();
       } else {
           result.incrementFailed();
           result.addFailedReason("科目 " + subjectCode + ": " + e.getMessage());
       }
   } catch (Exception e) {
       result.incrementFailed();
       result.addFailedReason("科目 " + subjectCode + ": 未知异常 - " + e.getMessage());
   }
   ```
3. 返回批量结果

> **P1-4 修复说明**：批量扫描为**非事务性**，每个账户独立 try-catch。使用 `DistributedLockTemplate` 加锁防并发。失败后记录原因 continue，不中断整体流程。

### 4.3 createSubAccountsInternal 方法（内部方法）

```java
/**
 * 为主账户创建子账户（可用 + 冻结）
 * 注意：此方法必须在事务中被调用
 */
private List<SubAccountPO> createSubAccountsInternal(AccountPO account) {
    List<SubAccountPO> subAccounts = new ArrayList<>(2);

    // 可用子账户
    SubAccountPO available = new SubAccountPO();
    available.setAccountNo(account.getAccountNo());
    available.setBalanceType(BalanceTypeEnum.AVAILABLE);  // 1
    available.setBalanceDirection(account.getBalanceDirection());
    available.setBalance(BigDecimal.ZERO);
    available.setVersion(0L);
    subAccountRepository.insert(available);
    subAccounts.add(available);

    // 冻结子账户
    SubAccountPO frozen = new SubAccountPO();
    frozen.setAccountNo(account.getAccountNo());
    frozen.setBalanceType(BalanceTypeEnum.FROZEN);  // 2
    frozen.setBalanceDirection(account.getBalanceDirection());
    frozen.setBalance(BigDecimal.ZERO);
    frozen.setVersion(0L);
    subAccountRepository.insert(frozen);
    subAccounts.add(frozen);

    return subAccounts;
}
```

**约束**：每个主账户必须有且仅有两个子账户，余额方向严格继承主账户。

---

## 5. BatchOpenResult 结果类

在 `accounting-core/.../domain/model/BatchOpenResult.java` 创建：

```java
@Data
@Accessors(chain = true)
public class BatchOpenResult {
    private Integer totalCount;
    private Integer alreadyExists;
    private Integer newlyCreated;
    private Integer failed;
    private List<String> failedReasons;

    public BatchOpenResult() {
        this.totalCount = 0;
        this.alreadyExists = 0;
        this.newlyCreated = 0;
        this.failed = 0;
        this.failedReasons = new ArrayList<>();
    }

    public void incrementAlreadyExists() { this.alreadyExists++; }
    public void incrementNewlyCreated() { this.newlyCreated++; }
    public void incrementFailed() { this.failed++; }
    public void addFailedReason(String reason) { this.failedReasons.add(reason); }
}
```

---

## 6. 编码要点

- `createSubAccountsInternal` 为私有方法，必须在事务中被调用
- 子账户余额方向严格继承主账户，不得自行推断
- 批量扫描时单个账户失败不应中断整体流程
- `INNER` 常量建议统一定义：`public static final String OWNER_INNER = "INNER"`
- 内部账户编号永久递增（不每日重置）

---

## 7. 完成标准

- [ ] `openInternalAccount` 内部账户开户流程正确
- [ ] `scanAndOpenInternalAccounts` 批量扫描正确（非事务性，逐账户 try-catch）
- [ ] `createSubAccountsInternal` 自动创建可用+冻结两个子账户
- [ ] 子账户余额方向正确继承主账户
- [ ] 初始余额为 0
- [ ] 批量扫描时单账户失败不影响其他账户
- [ ] `BatchOpenResult` 统计正确
- [ ] 内部账户编号永久递增（不每日重置）

---

## 8. 下一步

Java-C 开始编排 Application Service 和 Controller，详见 `docs/prompt/tasks/step-08-java-c.md`。
