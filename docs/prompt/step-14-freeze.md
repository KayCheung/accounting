# step-14-freeze · 资金冻结与解冻

> **Phase 5 第二步** | 归属：`@Java` 工程师
> 前置依赖：Step 13（账户状态管理 — 分布式锁模板 + 事务模式 + 主账户状态管控已就绪）
>
> **与 Step 13 的边界**：Step 13 操作主账户状态（NORMAL/FROZEN/CANCELLED），Step 14 操作子账户余额（可用 ↔ 冻结余额转移）。两者互相独立，但 Step 14 需在主账户 NORMAL 状态下才能执行冻结。

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `SubAccountMapper` 补充余额更新方法 | `SubAccountMapper.java` + `SubAccountMapper.xml` | 按 accountNo + balanceType 更新子账户余额（乐观锁） |
| P0-2 | `SubAccountMapper` 补充按账户查询方法 | `SubAccountMapper.java` | 查询指定账户的所有子账户（balance_type=1 可用 + 2 冻结） |
| P0-3 | `SubAccountRepository` 封装余额更新 + 查询方法 | `SubAccountRepository.java` | 含乐观锁冲突拦截 |
| P0-4 | `SubAccountDetailMapper` 补充明细插入方法 | `SubAccountDetailMapper.java` + XML | 子账户余额变动明细写入 |
| P0-5 | `FreezeDetailMapper` 补充冻结记录 CRUD | `FreezeDetailMapper.java` + XML | 创建/查询/更新冻结明细 |
| P0-6 | PO/DDL 确认 | `AccountFreezeDetailPO.java` | 确认 `t_account_freeze_detail` 映射正确 |

### P0-1: SubAccountMapper 补充余额更新方法

```java
// SubAccountMapper.java
/**
 * 按 accountNo + balanceType 更新子账户余额（乐观锁）
 */
int updateBalance(@Param("accountNo") String accountNo,
    @Param("balanceType") Integer balanceType,
    @Param("newBalance") BigDecimal newBalance,
    @Param("version") Long version);
```

对应 XML：

```xml
<update id="updateBalance">
    UPDATE t_sub_account
    SET balance = #{newBalance}, version = version + 1
    WHERE account_no = #{accountNo}
      AND balance_type = #{balanceType}
      AND version = #{version}
      AND is_delete = 0
</update>
```

### P0-2: SubAccountMapper 补充按账户查询

```java
/**
 * 查询指定账户的所有子账户
 */
List<SubAccountPO> selectByAccountNo(@Param("accountNo") String accountNo);

/**
 * 查询指定账户的指定余额类型子账户
 */
SubAccountPO selectByAccountNoAndType(@Param("accountNo") String accountNo,
    @Param("balanceType") Integer balanceType);
```

### P0-3: SubAccountRepository 封装

```java
// SubAccountRepository.java
public void updateBalance(String accountNo, Integer balanceType, BigDecimal newBalance, Long version) {
    int affected = subAccountMapper.updateBalance(accountNo, balanceType, newBalance, version);
    if (affected == 0) {
        throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED, "子账户余额更新冲突: " + accountNo);
    }
}

public List<SubAccountPO> selectByAccountNo(String accountNo) {
    return subAccountMapper.selectByAccountNo(accountNo);
}

public SubAccountPO selectByAccountNoAndType(String accountNo, Integer balanceType) {
    return subAccountMapper.selectByAccountNoAndType(accountNo, balanceType);
}
```

### P0-4: SubAccountDetailMapper 补充明细插入

```java
// SubAccountDetailMapper.java
int insertSubDetail(SubAccountDetailPO po);
```

### P0-5: FreezeDetailMapper 补充冻结记录 CRUD

```java
// FreezeDetailMapper.java
int insert(AccountFreezeDetailPO po);
AccountFreezeDetailPO selectByVoucherNo(@Param("voucherNo") String voucherNo);
List<AccountFreezeDetailPO> selectExpiredRecords(@Param("now") LocalDateTime now);
int updateStatus(@Param("voucherNo") String voucherNo,
    @Param("status") Integer status, @Param("version") Long version);
```

对应 XML：

```xml
<update id="updateStatus">
    UPDATE t_account_freeze_detail
    SET status = #{status}, version = version + 1, update_time = NOW()
    WHERE voucher_no = #{voucherNo} AND version = #{version} AND is_delete = 0
</update>

<select id="selectExpiredRecords" resultType="AccountFreezeDetailPO">
    SELECT * FROM t_account_freeze_detail
    WHERE expire_time &lt;= #{now}
      AND status = 1
      AND is_delete = 0
    ORDER BY create_time ASC
</select>
```

---

## 1. 任务目标（Mission）

实现资金冻结与解冻操作，支持以下用例：

1. **资金冻结**：从可用子账户转移指定金额到冻结子账户（可用余额减少 → 冻结余额增加）
2. **资金解冻**：基于原冻结记录将冻结余额转回可用余额
3. **超时自动解冻**：定时任务扫描过期冻结记录并自动解冻
4. **冻结扣款**：在冻结额度内直接扣款（冻结余额减少 + 主账户余额减少）

> **核心原则**：冻结/解冻操作不生成凭证和分录，仅操作余额 + 记录明细。冻结扣款需生成凭证。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、余额方向约束、严禁负数运算 |
| 3 | `docs/prompt/step-13-account-status.md` | Step 13 总体任务说明（复用分布式锁 + 事务模式） |
| 4 | `docs/prompt/step-14-freeze.md` | 本文件 |
| 5 | `docs/sql/1-account.sql` | `t_account` / `t_sub_account` / `t_sub_account_detail` / `t_account_freeze_detail` DDL |
| 6 | `docs/design/flowchart/freeze_unfreeze_flow.mmd` | 冻结/解冻流程图 |
| 7 | `accounting-core/.../domain/service/AccountStatusChangeDomainService.java` | Step 13 已实现，参考分布式锁用法 |
| 8 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-14-java-a.md` | P0-1~P0-6 补充 + FreezeDomainService（资金冻结/解冻/扣款核心逻辑） + AutoUnfreezeJobHandler |
| Java-B | `docs/prompt/tasks/step-14-java-b.md` | 冻结余额校验逻辑 + 过期检测 + 冻结扣款凭证生成（委托 Step 10） |
| Java-C | `docs/prompt/tasks/step-14-java-c.md` | Application Service + Controller（5 个接口）+ DTO + Assembler |

> **依赖关系**：Java-A 最先完成（Mapper/Repository 补充 + 领域服务）。Java-B 依赖 Java-A 的领域服务。Java-C 依赖 Java-A/B。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 资金冻结

```
前置校验：
  - 账户必须存在（调用 accountRepository.selectByAccountNo）
  - 主账户状态必须为 NORMAL（非冻结/注销）
  - 可用子账户余额 >= 冻结金额（严禁负数运算）
  - 冻结金额 > 0

执行动作（同一事务中）：
  1. 可用子账户余额减少：balance_type=1, balance -= freezeAmount
  2. 冻结子账户余额增加：balance_type=2, balance += freezeAmount
  3. 记录子账户明细：t_sub_account_detail（含 preBalance / postBalance）
  4. 创建冻结记录：t_account_freeze_detail（status=1 冻结）
     - voucherNo = 系统生成唯一编号（格式：FRZ + 日期 + 序列）
     - freezeAmount = 冻结金额
     - expireTime = 过期时间（默认 2099-12-31 表示永不过期）

后置影响：
  - 主账户余额不变（t_account.balance 保持不变）
  - 不生成凭证和分录
```

### 4.2 资金解冻

```
前置校验：
  - 冻结记录必须存在且 status=1（冻结中）
  - 解冻金额 <= 冻结记录的 freezeAmount
  - 解冻金额 > 0

执行动作（同一事务中）：
  1. 冻结子账户余额减少：balance_type=2, balance -= unfreezeAmount
  2. 可用子账户余额增加：balance_type=1, balance += unfreezeAmount
  3. 记录子账户明细：t_sub_account_detail（含 preBalance / postBalance）
  4. 更新冻结记录：
     - 完全解冻（unfreezeAmount == freezeAmount）→ status=2（已解冻）
     - 部分解冻 → status 保持 1，金额不变（余额变化由子账户记录体现）

后置影响：
  - 主账户余额不变
  - 不生成凭证和分录
```

### 4.3 超时自动解冻

```
定时任务（每 5 分钟执行一次）：
  1. 查询过期冻结记录：expire_time <= NOW() AND status = 1
  2. 对每条冻结记录（独立事务）：
     a. 执行与 4.2 相同的余额转移逻辑
     b. 更新冻结记录 status = 2
     c. 记录自动解冻日志

异常处理：
  - 单笔解冻失败不中断后续记录
  - 失败记录记录错误原因，等待人工介入
```

### 4.4 冻结扣款

```
前置校验：
  - 冻结记录必须存在且 status=1（冻结中）
  - 扣款金额 <= 冻结记录的 freezeAmount
  - 冻结子账户余额 >= 扣款金额

执行动作（同一事务中）：
  1. 冻结子账户余额减少：balance_type=2, balance -= deductAmount
  2. 主账户余额减少：t_account.balance -= deductAmount
  3. 记录子账户明细：t_sub_account_detail
  4. 记录主账户明细：t_account_detail
  5. 更新冻结记录 status = 2（已解冻）
  6. 生成扣款凭证（委托 Step 10 VoucheringDomainService）

> 冻结扣款是唯一需要生成凭证的场景，因为它涉及主账户余额变动。
```

### 4.5 并发安全

所有冻结/解冻/扣款操作必须：
1. 加分布式锁：lockKey = `account:fund:{accountNo}`
2. 双重检查：查询当前余额 → 加锁 → 再次查询 → 执行变更
3. 使用乐观锁版本校验兜底（子账户 `version` 字段）

> **与 Step 13 锁 Key 的区别**：Step 13 使用 `account:status:{accountNo}`（状态管控），Step 14 使用 `account:fund:{accountNo}`（资金操作）。两者互不干扰，可独立执行。

### 4.6 余额关系示例

| 阶段 | 主账户余额 | 可用子账户 | 冻结子账户 | 说明 |
|------|-----------|-----------|-----------|------|
| 初始 | 1000 | 1000 | 0 | 初始状态 |
| 冻结 300 | 1000 | 700 | 300 | 主不变，可用→冻结 |
| 解冻 100 | 1000 | 800 | 200 | 主不变，冻结→可用 |
| 扣款 200 | 800 | 800 | 0 | 冻结减少 + 主减少 |

---

## 5. 接口契约

所有接口路径前缀：`/accounting/account/freeze`

### 5.1 POST `/accounting/account/freeze/fund` — 资金冻结

**请求体** (`FundFreezeRequest`):
```json
{
  "accountNo": "00120260301000001",
  "freezeAmount": 300.00,
  "expireTime": "2026-12-31 23:59:59",
  "reason": "客户异常交易风控"
}
```

**校验规则**：
- `accountNo`：必填，长度 1-32
- `freezeAmount`：必填，> 0，精度 DECIMAL(18,6)
- `expireTime`：选填，默认 2099-12-31（永不过期）
- `reason`：选填，长度 1-128

### 5.2 POST `/accounting/account/freeze/unfreeze` — 资金解冻

**请求体** (`FundUnfreezeRequest`):
```json
{
  "freezeId": "FRZ20260611000001",
  "unfreezeAmount": 100.00,
  "reason": "风控解除"
}
```

**校验规则**：
- `freezeId`：必填（对应 t_account_freeze_detail.voucher_no）
- `unfreezeAmount`：必填，> 0，<= 冻结记录的 freezeAmount

### 5.3 GET `/accounting/account/freeze/{freezeId}` — 查询冻结记录

**响应** (`FreezeDetailResponse`):
```json
{
  "freezeId": "FRZ20260611000001",
  "accountNo": "00120260301000001",
  "freezeAmount": 300.00,
  "status": 1,
  "statusDesc": "冻结",
  "expireTime": "2026-12-31 23:59:59",
  "createTime": "2026-06-11 10:00:00"
}
```

### 5.4 POST `/accounting/account/freeze/deduct` — 冻结扣款

**请求体** (`FreezeDeductRequest`):
```json
{
  "freezeId": "FRZ20260611000001",
  "deductAmount": 200.00,
  "reason": "法院强制执行"
}
```

### 5.5 GET `/accounting/account/freeze/list` — 冻结记录列表

查询指定账户的冻结记录（可按状态过滤）。

---

## 6. 需要创建/修改的文件

### 新建（Java-A）

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       └── FreezeDomainService.java          # 资金冻结/解冻/扣款领域服务
    └── infrastructure/persistence/repository/
        └── FreezeDetailRepository.java           # 冻结明细仓储
```

### 新建（accounting-job）

```
accounting-job/
└── src/main/java/com/kltb/accounting/job/
    └── AutoUnfreezeJobHandler.java               # 超时自动解冻定时任务
```

### 新建（Java-C）

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── FundFreezeRequest.java                # 资金冻结请求
    │   ├── FundUnfreezeRequest.java              # 资金解冻请求
    │   └── FreezeDeductRequest.java              # 冻结扣款请求
    └── response/
        └── FreezeDetailResponse.java             # 冻结记录响应
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `SubAccountMapper.java` | 新增 `updateBalance` / `selectByAccountNo` / `selectByAccountNoAndType` |
| `SubAccountMapper.xml` | 新增对应 SQL |
| `SubAccountRepository.java` | 新增 `updateBalance` / `selectByAccountNo` / `selectByAccountNoAndType` |
| `SubAccountDetailMapper.java` | 新增 `insertSubDetail` |
| `FreezeDetailMapper.java` | 新建 Mapper |
| `FreezeDetailMapper.xml` | 新建 XML |
| `ResultCode.java` | 新增错误码（见下方） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"4001"` | `FREEZE_AMOUNT_INVALID` | 冻结金额无效（<=0 或超过可用余额） |
| `"4002"` | `FREEZE_RECORD_NOT_FOUND` | 冻结记录不存在 |
| `"4003"` | `FREEZE_STATUS_INVALID` | 冻结记录状态非法（非冻结中） |
| `"4004"` | `FREEZE_AMOUNT_EXCEEDED` | 解冻/扣款金额超过冻结金额 |
| `"4005"` | `ACCOUNT_FROZEN_CANNOT_FREEZE` | 账户已冻结，无法执行资金冻结 |

---

## 7. FreezeDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class FreezeDomainService {

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final FreezeDetailRepository freezeDetailRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 资金冻结（可用余额 → 冻结余额）
     */
    public AccountFreezeDetailPO freezeFund(String accountNo, BigDecimal freezeAmount,
        LocalDateTime expireTime, String reason);

    /**
     * 资金解冻（冻结余额 → 可用余额）
     */
    public void unfreezeFund(String freezeId, BigDecimal unfreezeAmount, String reason);

    /**
     * 冻结扣款（冻结余额减少 + 主账户余额减少 + 生成凭证）
     */
    public void deductFromFreeze(String freezeId, BigDecimal deductAmount, String reason);

    /**
     * 查询冻结记录
     */
    public AccountFreezeDetailPO queryFreezeRecord(String freezeId);

    /**
     * 查询指定账户的冻结记录列表
     */
    public List<AccountFreezeDetailPO> queryFreezeRecords(String accountNo, Integer status);
}
```

---

## 8. 编码要点

### 8.1 事务边界

- 冻结/解冻/扣款操作在 `TransactionTemplate` 事务中完成
- 分布式锁包裹整个事务执行
- 自动解冻 Job 中每条记录使用独立事务（单笔失败不中断）

### 8.2 严禁负数运算

```java
// 正确做法：先校验后运算
if (availableBalance.compareTo(freezeAmount) < 0) {
    throw new AccountException(ResultCode.FREEZE_AMOUNT_INVALID,
        "可用余额不足: available=" + availableBalance + ", freeze=" + freezeAmount);
}
BigDecimal newAvailable = availableBalance.subtract(freezeAmount);
BigDecimal newFrozen = frozenBalance.add(freezeAmount);
```

### 8.3 凭证编号生成

冻结记录使用独立编号生成器：

```java
// FreezeIdGenerator
public String generate() {
    // 格式：FRZ + yyyyMMdd + 6位序列号
    // 示例：FRZ20260611000001
    return "FRZ" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
        + String.format("%06d", sequence.next());
}
```

序列号可使用 Redis INCR 实现每日重置。

---

## 9. 完成标准（Checklist）

### Java-A
- [X] P0-1~P0-6 前置补充任务完成
- [X] `FreezeDomainService.freezeFund` 资金冻结（可用→冻结 + 明细记录）
- [X] `FreezeDomainService.unfreezeFund` 资金解冻（冻结→可用 + 状态更新）
- [X] 主账户余额不变（冻结/解冻场景）
- [X] 子账户余额变更含乐观锁校验
- [X] 子账户明细完整记录（preBalance / postBalance）
- [X] 冻结记录含过期时间（expireTime 默认 2099-12-31）
- [X] 并发安全：`DistributedLockTemplate` + 双重检查 + 乐观锁
- [X] `AutoUnfreezeJobHandler` 定时任务（每 5 分钟执行，扫描过期记录）
- [X] 自动解冻单笔失败不中断

### Java-B
- [X] 冻结金额合法性校验（> 0 + 可用余额充足）
- [X] 解冻金额合法性校验（> 0 + <= 冻结金额）
- [X] 扣款金额合法性校验（> 0 + <= 冻结余额）
- [X] `FreezeDomainService.deductFromFreeze` 冻结扣款（冻结余额减少 + 子账户明细 + 主账户明细 + 主账户余额减少 + 状态更新）
- [ ] 扣款生成凭证（委托 Step 10 VoucheringDomainService）—— **后续扩展**：当前扣款已完成余额变更和明细记录，凭证生成作为可选扩展

### Java-C
- [X] `FreezeApplicationService` 编排 5 个用例
- [X] `FreezeController` 实现 5 个接口
- [X] `FundFreezeRequest` / `FundUnfreezeRequest` / `FreezeDeductRequest` DTO
- [X] `FreezeDetailResponse` DTO
- [X] `FreezeAssembler` 完成 PO ↔ DTO 转换
- [X] 参数校验：金额 > 0（`@DecimalMin("0.000001")`）、freezeAmount 非空、expireTime 选填
- [x] 单测全通：FreezeDomainServiceTest（冻结/解冻/扣款/金额校验/状态校验/记录查询）

### TL Review
- [X] 余额方向正确处理（借方/贷方余额增减逻辑正确）
- [X] 严禁负数运算（先校验后运算，`compareTo` 拦截负数场景）
- [X] 事务边界正确（TransactionTemplate，非 @Transactional）
- [X] 锁 Key 与 Step 13 不冲突（`account:fund:{accountNo}` vs `account:status:{accountNo}`）
- [ ] 冻结扣款凭证生成正确（委托 Step 10，凭证类型与业务线匹配）—— **后续扩展**
- [X] 自动解冻 Job 异常处理（单笔失败不中断 + 错误日志）
- [X] 冻结编号唯一性（FRZ + 日期 + 序列，Lua 原子化初始化）
- [X] 子账户明细完整性（每笔余额变动均记录 preBalance / postBalance）
- [X] 扣款场景主账户明细完整（`t_account_detail` preBalance/amount/postBalance）

---

## 10. 与后续 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 15（余额查询 API） | 可查询主账户余额 + 子账户可用/冻结余额 + 冻结记录列表 |
| Step 16（缓冲记账） | 无直接依赖 |
| Step 18（冲账与红冲） | 冲账场景可能需要逆向冻结记录 |

---

## 11. 下一步行动

进入 **Step 15 · Balance Query API（余额查询接口）**，详见 `docs/prompt/step-15-balance-query.md`。
