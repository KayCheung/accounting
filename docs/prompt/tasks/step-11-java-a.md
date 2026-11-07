# step-11-java-a · P0 补充 + PostingDomainService + AccountBalanceCalculator

> **Step 11 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 10（凭证生成引擎已完成）

---

## 1. 任务目标

完成 Step 11 七项前置补充任务（P0-1~P0-7），并实现余额计算器、实时过账领域服务。

核心职责：
1. 补充 Mapper/Repository 方法（TransactionMapper、AccountingVoucherMapper/EntryMapper、AccountMapper/DetailMapper）
2. 新建 AccountDetailRepository / SubAccountDetailRepository 封装
3. 实现 AccountBalanceCalculator 无状态工具类
4. 实现 PostingDomainService 实时过账领域服务（加锁 → 余额计算 → 账户更新 → 明细快照 → 分录状态更新）

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、借贷方向约束、并发控制 |
| 3 | `docs/prompt/step-11-transaction.md` | Step 11 总体任务说明 |
| 4 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` / `t_account_detail` / `t_sub_account_detail` DDL |
| 5 | `docs/sql/2-voucher.sql` | `t_accounting_voucher_entry` DDL（含 is_unilateral / is_buffered） |
| 6 | `docs/sql/5-journal.sql` | `t_transaction` DDL |
| 7 | `docs/design/domain-model.md` | 账户域、凭证域模型 |
| 8 | `accounting-core/.../entity/AccountPO.java` | 账户 PO |
| 9 | `accounting-core/.../entity/SubAccountPO.java` | 子账户 PO |
| 10 | `accounting-core/.../entity/AccountingVoucherEntryPO.java` | 分录 PO（含 unilateral/buffered 字段） |
| 11 | `accounting-core/.../mapper/AccountMapper.java` | **已有**（含 selectForUpdate 单账户版本） |
| 12 | `accounting-core/.../mapper/SubAccountMapper.java` | **已有**（含 selectForUpdate） |
| 13 | `accounting-core/.../mapper/AccountDetailMapper.java` | **已有**（含 selectByVoucherNo，需追加 batchInsert 方法） |
| 14 | `accounting-core/.../mapper/SubAccountDetailMapper.java` | **已有**（空壳，继承 BaseMapper 已提供 insert 方法，无需追加） |
| 15 | `accounting-core/.../config/TransactionConfig.java` | TransactionTemplate Bean |
| 16 | `accounting-core/.../repository/AccountingVoucherRepository.java` | **已有**（含 selectEntriesByVoucherNo 等） |
| 17 | `accounting-core/.../repository/TransactionRepository.java` | **已有**（含 selectByTraceNo） |

---

## 3. P0 前置补充任务（开始编码前必须完成）

### P0-1: TransactionMapper 补充方法

在 `TransactionMapper.java` 中追加两个方法：

```java
/**
 * 按事务编号更新事务状态
 */
int updateStatusByTxnNo(@Param("txnNo") String txnNo,
                         @Param("status") Integer status,
                         @Param("failReason") String failReason,
                         @Param("finishTime") LocalDateTime finishTime);

/**
 * 按事务编号查询事务
 */
TransactionPO selectByTxnNo(@Param("txnNo") String txnNo);
```

对应 XML（在 `resources/mapper/TransactionMapper.xml` 中，若已有则追加）：

```xml
<update id="updateStatusByTxnNo">
    UPDATE t_transaction
    SET status = #{status},
        <if test="failReason != null">
        fail_reason = #{failReason},
        </if>
        <if test="finishTime != null">
        finish_time = #{finishTime},
        </if>
        update_time = NOW()
    WHERE txn_no = #{txnNo} AND is_delete = 0
</update>

<select id="selectByTxnNo" resultType="TransactionPO">
    SELECT * FROM t_transaction WHERE txn_no = #{txnNo} AND is_delete = 0 LIMIT 1
</select>
```

### P0-2: TransactionRepository 补充方法

在 `TransactionRepository.java` 中追加封装方法：

```java
public void updateStatusByTxnNo(String txnNo, TransactionStatusEnum status,
                                 String failReason, LocalDateTime finishTime);
public TransactionPO selectByTxnNo(String txnNo);
```

### P0-3: AccountingVoucherMapper 补充方法

在 `AccountingVoucherMapper.java` 中追加两个方法：

```java
/**
 * 按凭证号更新凭证状态
 */
int updateStatusByVoucherNo(@Param("voucherNo") String voucherNo,
                              @Param("status") Integer status,
                              @Param("postTime") LocalDateTime postTime);

/**
 * 按凭证号查询凭证
 */
AccountingVoucherPO selectByVoucherNo(@Param("voucherNo") String voucherNo);
```

对应 XML（若已有 `AccountingVoucherMapper.xml` 则追加，否则新建）：

```xml
<update id="updateStatusByVoucherNo">
    UPDATE t_accounting_voucher
    SET status = #{status},
        <if test="postTime != null">
        post_time = #{postTime},
        </if>
        update_time = NOW()
    WHERE voucher_no = #{voucherNo} AND is_delete = 0
</update>

<select id="selectByVoucherNo" resultType="AccountingVoucherPO">
    SELECT * FROM t_accounting_voucher
    WHERE voucher_no = #{voucherNo} AND is_delete = 0 LIMIT 1
</select>
```

同时在 `AccountingVoucherRepository.java` 中追加封装：

```java
public void updateStatusByVoucherNo(String voucherNo, Integer status);
public AccountingVoucherPO selectByVoucherNo(String voucherNo);
```

### P0-4: AccountingVoucherEntryMapper 补充方法

在 `AccountingVoucherEntryMapper.java` 中追加方法：

```java
/**
 * 按凭证号和状态查询分录（用于过滤特定状态的分录行）
 */
List<AccountingVoucherEntryPO> selectByVoucherNoWithStatus(
    @Param("voucherNo") String voucherNo,
    @Param("status") Integer status);
```

对应 XML：

```xml
<select id="selectByVoucherNoWithStatus" resultType="AccountingVoucherEntryPO">
    SELECT * FROM t_accounting_voucher_entry
    WHERE voucher_no = #{voucherNo} AND status = #{status} AND is_delete = 0
    ORDER BY row_num ASC
</select>
```

### P0-5: AccountMapper 补充 selectForUpdateBatch（多账户版本）

`AccountMapper.java` 已有 `selectForUpdate(String accountNo)`（单账户版本，Step 8 已实现）。
新增批量加锁版本（需显式 ORDER BY 保证加锁顺序，P1-2 修复）：

```java
/**
 * 按账户编号列表加悲观锁查询
 * 调用方必须保证 accountNos 已按升序排序
 */
List<AccountPO> selectForUpdateBatch(@Param("accountNos") List<String> accountNos);
```

对应 XML：

```xml
<select id="selectForUpdateBatch" resultType="AccountPO">
    SELECT * FROM t_account
    WHERE account_no IN
    <foreach collection="accountNos" item="no" open="(" separator="," close=")">
        #{no}
    </foreach>
    ORDER BY account_no ASC
    FOR UPDATE
</select>
```

> **P1-2 修复**：显式添加 `ORDER BY account_no ASC` 确保 MySQL 按索引顺序加锁，防止 IN 子句的执行计划不确定导致死锁。

### P0-6: 确认 DDL 列已补充

读取 `docs/sql/2-voucher.sql` 和 `docs/sql/all-tables.sql`，确认 `t_accounting_voucher_entry` 已有：
- `is_unilateral` TINYINT NOT NULL DEFAULT 0
- `is_buffered` TINYINT NOT NULL DEFAULT 0

### P0-7: AccountingVoucherEntryPO 补充字段

读取 `AccountingVoucherEntryPO.java`，确认已存在：

```java
@TableField("is_unilateral")
private Integer unilateral;

@TableField("is_buffered")
private Integer buffered;

@TableField("change_direction")
private Integer changeDirection;  // P1-1 修复：新增 changeDirection 字段
```

> 注意：字段名使用 `unilateral` / `buffered` / `changeDirection`（无前缀 is_ / change_），并通过 `@TableField` 显式映射到 DDL 列名。这是为了规避 MyBatis-Plus 对 is_/change_ 前缀列名的特殊布尔处理。

---

## 4. AccountBalanceCalculator（无状态工具类）

新建 `accounting-core/.../account/AccountBalanceCalculator.java`。

> 此类为纯函数工具类，使用 `int` 参数，豁免 java.md "禁止基本数据类型" 规范。

```java
package com.kltb.accounting.core.infrastructure.account;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.shared.exception.AccountException;
import java.math.BigDecimal;

/**
 * 余额计算器，提供过账余额计算的纯函数。
 * 不依赖任何 Spring Bean，不包含任何副作用。
 */
public final class AccountBalanceCalculator {

    private AccountBalanceCalculator() {}

    /**
     * 计算新余额
     *
     * 是否记账：否（纯计算）
     * 异常处理：
     *   - [AccountException] → 余额不足时抛出 INSUFFICIENT_BALANCE
     *
     * @param currentBalance  当前余额（始终 >= 0）
     * @param amount          变更金额（始终 > 0）
     * @param changeDirection 增减方向（1=增, 2=减）
     * @return 新余额
     * @throws AccountException 当余额不足时抛出
     */
    public static BigDecimal calculateNewBalance(
        BigDecimal currentBalance,
        BigDecimal amount,
        int changeDirection) {

        if (changeDirection == 1) {
            // 余额增加：同向相加
            return currentBalance.add(amount);
        } else {
            // 余额减少：反向相减，前置校验余额充足
            if (currentBalance.compareTo(amount) < 0) {
                throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
                    "余额不足: current=" + currentBalance + ", need=" + amount);
            }
            return currentBalance.subtract(amount);
        }
    }
}
```

---

## 5. PostingDomainService（实时过账领域服务）

新建 `accounting-core/.../domain/service/PostingDomainService.java`。

> **职责**：实时过账核心逻辑，**不含事务**，由 Application Service 统一控制事务边界。

```java
@Service
@RequiredArgsConstructor
public class PostingDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final AccountDetailRepository accountDetailRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final AccountBalanceCalculator balanceCalculator;

    /**
     * 实时过账：锁定账户 → 计算余额 → 更新余额 → 记录明细
     *
     * 是否记账：是
     * 异常处理：
     *   - [AccountException] → 余额不足 / 账户状态异常 → 由上层 catch 触发回滚
     *
     * @param entries        需要实时过账的分录列表（is_unilateral=1，按 account_no 升序排列）
     * @param accountingDate 会计日期
     */
    public void executeRealTimePosting(
        List<AccountingVoucherEntryPO> entries,
        LocalDate accountingDate);

    /**
     * 检查凭证所有实时分录是否都已过账
     *
     * 是否记账：否
     */
    public boolean areAllRealTimeEntriesPosted(String voucherNo);
}
```

### executeRealTimePosting 执行流程

```
输入：实时分录列表（is_unilateral=1），会计日期
  ↓
1. 按 account_no 分组（去重），已升序
2. 按 account_no 列表调用 accountMapper.selectForUpdateBatch() 加锁
3. 逐分录处理：
   a. 查询账户 AccountPO（通过 accountRepository 查询，已在锁中）
   b. 查询对应子账户 SubAccountPO（balance_type=1 可用，通过 subAccountRepository 查询，已在锁中）
   c. 读取分录的 changeDirection（从 AccountingVoucherEntryPO.changeDirection 字段获取，
      该字段由 Step 10 凭证生成时从规则配置推导并写入 DDL 列，过账阶段直接使用，P1-1 修复）
   d. 计算新余额：balanceCalculator.calculateNewBalance()
   e. 更新主账户余额 + version（乐观锁兜底）
   f. 更新子账户余额 + version
   g. 写入 t_account_detail（含 preBalance / postBalance 快照）
   h. 写入 t_sub_account_detail
   i. 更新分录 status=2(已过账)
4. 提交（由调用方 TransactionTemplate 控制）
```

> **注意**：`AccountMapper.selectForUpdateBatch` 已在 §3 P0-5 中实现。调用方必须保证传入的 accountNos 已按字符串升序排列，防止死锁。SQL 中已含 `ORDER BY account_no ASC` 保证锁定顺序（P1-2 修复）。

> **P1-1 修复**：`changeDirection` 字段已由 Step 10 写入 `t_accounting_voucher_entry.change_direction` 列，过账阶段直接从 `AccountingVoucherEntryPO.changeDirection` 读取使用。无需在过账时动态查询科目推导。同时需在 `AccountingVoucherEntryPO.java` 中追加 `changeDirection` 属性 + `@TableField("change_direction")` 注解。

---

## 6. AccountDetailRepository / SubAccountDetailRepository（新建）

### AccountDetailRepository

新建 `accounting-core/.../repository/AccountDetailRepository.java`：

```java
@Repository
@RequiredArgsConstructor
public class AccountDetailRepository {

    private final AccountDetailMapper accountDetailMapper;

    /**
     * 批量写入账户明细
     */
    public void batchInsert(List<AccountDetailPO> details);
}
```

### SubAccountDetailRepository

新建 `accounting-core/.../repository/SubAccountDetailRepository.java`：

```java
@Repository
@RequiredArgsConstructor
public class SubAccountDetailRepository {

    private final SubAccountDetailMapper subAccountDetailMapper;

    /**
     * 批量写入子账户明细
     */
    public void batchInsert(List<SubAccountDetailPO> details);
}
```

> 由于 MyBatis-Plus 的 `BaseMapper` 已提供 `insert()` 方法，`batchInsert` 可简单实现为循环 insert。如需批量插入性能优化，可使用 `saveBatch`（MyBatis-Plus ServiceImpl）。

### AccountRepository / SubAccountRepository（补充方法）

在已有 `AccountRepository.java` 中追加：

```java
public List<AccountPO> selectForUpdateBatch(List<String> accountNos);
```

新建 `SubAccountRepository.java`：

```java
@Repository
@RequiredArgsConstructor
public class SubAccountRepository {
    private final SubAccountMapper subAccountMapper;

    public List<SubAccountPO> selectForUpdate(String accountNo);
}
```

---

## 7. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `TransactionMapper.java` | 追加方法 | `updateStatusByTxnNo` + `selectByTxnNo` |
| `TransactionMapper.xml` | 追加 SQL | 对应 XML |
| `TransactionRepository.java` | 追加方法 | 封装 P0-1 |
| `AccountingVoucherMapper.java` | 追加方法 | `updateStatusByVoucherNo` + `selectByVoucherNo` |
| `AccountingVoucherMapper.xml` | 新建或追加 | 对应 XML |
| `AccountingVoucherRepository.java` | 追加方法 | 封装 P0-3 |
| `AccountingVoucherEntryMapper.java` | 追加方法 | `selectByVoucherNoWithStatus` |
| `AccountingVoucherEntryMapper.xml` | 新建或追加 | 对应 XML |
| `AccountMapper.java` | 追加方法 | `selectForUpdateBatch` |
| `AccountMapper.xml` | 新建或追加 | 对应 XML |
| `AccountingVoucherEntryPO.java` | 确认字段 | `unilateral` + `buffered` + `@TableField`（应已完成） |
| `AccountDetailRepository.java` | **新建** | 封装 AccountDetailMapper |
| `SubAccountDetailRepository.java` | **新建** | 封装 SubAccountDetailMapper |
| `AccountBalanceCalculator.java` | **新建** | 无状态余额计算器 |
| `PostingDomainService.java` | **新建** | 实时过账领域服务 |

---

## 8. 完成标准（Checklist）

- [ ] P0-1: `TransactionMapper` 补充 `updateStatusByTxnNo` + `selectByTxnNo` + XML
- [ ] P0-2: `TransactionRepository` 补充 `updateStatusByTxnNo` + `selectByTxnNo`
- [ ] P0-3: `AccountingVoucherMapper` 补充 `updateStatusByVoucherNo` + `selectByVoucherNo` + XML
- [ ] P0-4: `AccountingVoucherEntryMapper` 补充 `selectByVoucherNoWithStatus` + XML
- [ ] P0-5: `AccountMapper` 补充 `selectForUpdateBatch` + XML
- [ ] P0-6: 确认 DDL 中 `is_unilateral` + `is_buffered` 列已存在
- [ ] P0-7: 确认 `AccountingVoucherEntryPO` 含 `unilateral` + `buffered` 字段 + `@TableField`
- [ ] `AccountBalanceCalculator` 无状态工具类（纯函数，无副作用）
- [ ] `PostingDomainService` 实时过账领域服务（独立类，**不含事务**）
- [ ] 按 account_no 升序 SELECT FOR UPDATE 批量加锁
- [ ] 余额计算（change_direction 增减判断，前置校验余额充足）
- [ ] 更新 t_account.balance + t_sub_account.balance（含 version 乐观锁）
- [ ] 写入 t_account_detail（含 Pre/Post 余额快照）
- [ ] 写入 t_sub_account_detail
- [ ] 更新分录 status=2(已过账)
- [ ] `areAllRealTimeEntriesPosted` 方法
- [ ] 单测：正常过账（单笔分录）、正常过账（多笔分录）、余额不足
- [ ] AccountDetailRepository + SubAccountDetailRepository 新建完成
