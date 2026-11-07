# step-14-java-a · P0 补充 + FreezeDomainService + AutoUnfreezeJobHandler

> **Step 14 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 13（账户状态管理）、Step 8（账户开户）

---

## 1. 任务目标

完成 Step 14 六项前置补充任务（P0-1~P0-6），并实现 FreezeDomainService 领域服务
和 AutoUnfreezeJobHandler 定时任务。

核心职责：
1. 补充 Mapper/Repository 余额更新与查询方法
2. 新建 FreezeIdGenerator（冻结编号生成器）
3. 新建 FreezeDetailRepository（冻结明细仓储）
4. 实现 FreezeDomainService（资金冻结/解冻/扣款核心逻辑）
5. 实现 AutoUnfreezeJobHandler（超时自动解冻定时任务）

---

## 2. P0 前置补充任务

### P0-1: SubAccountMapper 补充 `updateBalance` / `selectByAccountNoAndType`

已在 `SubAccountMapper.java` 中追加：
- `updateBalance(accountNo, balanceType, newBalance, version)` — 乐观锁更新
- `selectByAccountNoAndType(accountNo, balanceType)` — 按账户+类型查询单个子账户

对应 XML 已追加至 `SubAccountMapper.xml`。

### P0-2: SubAccountMapper 按账户查询

`selectByAccountNo` 已存在（Step 5 已实现）。

### P0-3: SubAccountRepository 封装

已追加 `updateBalance`（含乐观锁冲突拦截）和 `selectByAccountNoAndType`。

### P0-4: SubAccountDetailMapper

`SubAccountDetailMapper` 继承 BaseMapper，`insert` 方法已可用，无需额外追加。

### P0-5: AccountFreezeDetailMapper 补充

已追加 `insert`（继承 BaseMapper）、`selectByVoucherNo`、`updateStatus`（乐观锁）。
对应 XML 已追加至 `AccountFreezeDetailMapper.xml`。

### P0-6: PO/DDL 确认

`AccountFreezeDetailPO` 已存在，映射 `t_account_freeze_detail` 表。

---

## 3. FreezeIdGenerator

新建 `FreezeIdGenerator.java`：
- 格式：`FRZ + yyyyMMdd + seq6`，如 `FRZ20260611000001`
- Redis key：`frz:seq:{yyyyMMdd}`，TTL 25h
- Lua 脚本原子化初始化，消除 TOCTOU 竞态
- 复用 `VoucherNoGenerator` 模式

---

## 4. FreezeDetailRepository

新建 `FreezeDetailRepository.java`：
- `insert(AccountFreezeDetailPO)` → 封装 Mapper
- `selectByVoucherNo(String)` → 封装 Mapper
- `selectExpiredRecords(LocalDateTime)` → 封装 Mapper.selectExpired
- `updateStatus(String, FreezeStatusEnum, Integer)` → 封装 Mapper，含乐观锁拦截
- `selectByCondition(LambdaQueryWrapper)` → 封装 Mapper.selectList

---

## 5. FreezeDomainService

新建 `FreezeDomainService.java`，依赖：
- `AccountRepository`（主账户存在性 + 状态 NORMAL 校验）
- `SubAccountRepository`（余额更新 + 查询）
- `FreezeDetailRepository`（冻结记录 CRUD）
- `SubAccountDetailMapper`（子账户明细写入）
- `DistributedLockTemplate`（锁 Key：`account:fund:{accountNo}`）
- `TransactionTemplate`（事务控制）
- `FreezeIdGenerator`（冻结编号生成）

### 5.1 freezeFund — 资金冻结

```
输入：accountNo + freezeAmount + expireTime? + reason?
  ↓
1. 校验 freezeAmount > 0
2. 查询主账户 → 不存在 → ACCOUNT_NOT_FOUND
3. 主账户状态 != NORMAL → ACCOUNT_FROZEN_CANNOT_FREEZE
4. 查询可用子账户 → 不存在 → FREEZE_AMOUNT_INVALID
5. 可用余额 < freezeAmount → FREEZE_AMOUNT_INVALID
6. 分布式锁 + 事务：
   a. 双重检查可用余额
   b. 可用子账户余额 -= freezeAmount
   c. 冻结子账户余额 += freezeAmount
   d. 写子账户明细（preBalance/amount/postBalance）
   e. 创建冻结记录（FRZ编号 + status=FROZEN + expireTime默认2099-12-31）
      注：通过 business_code 字段存储 accountNo（DDL 无 account_no 字段）
  ↓
7. 返回 AccountFreezeDetailPO
```

### 5.2 unfreezeFund — 资金解冻

```
输入：freezeId + unfreezeAmount + reason
  ↓
1. 校验 unfreezeAmount > 0
2. 查询冻结记录 → 不存在 → FREEZE_RECORD_NOT_FOUND
3. 状态 != FROZEN → FREEZE_STATUS_INVALID
4. unfreezeAmount > freezeAmount → FREEZE_AMOUNT_EXCEEDED
5. 分布式锁 + 事务：
   a. 双重检查冻结记录状态
   b. 冻结子账户余额 -= unfreezeAmount
   c. 可用子账户余额 += unfreezeAmount
   d. 写子账户明细
   e. 完全解冻（==freezeAmount）→ status=UNFROZEN
  ↓
6. 完成
```

### 5.3 deductFromFreeze — 冻结扣款

```
输入：freezeId + deductAmount + reason
  ↓
1. 校验 deductAmount > 0
2. 查询冻结记录 → 不存在 → FREEZE_RECORD_NOT_FOUND
3. 状态 != FROZEN → FREEZE_STATUS_INVALID
4. deductAmount > freezeAmount → FREEZE_AMOUNT_EXCEEDED
5. 分布式锁 + 事务：
   a. 双重检查冻结记录状态
   b. 冻结子账户余额 -= deductAmount
   c. 写子账户明细
   d. 更新冻结记录 status=UNFROZEN
   注：主账户余额减少和凭证生成在后续阶段实现
  ↓
6. 完成
```

### 5.4 其他方法

- `queryFreezeRecord(freezeId)` — 查询冻结记录
- `queryFreezeRecords(accountNo, status)` — 查询冻结记录列表（通过 business_code 匹配）
- `autoUnfreezeOne(expiredRecord)` — 单笔自动解冻（供 Job 调用）

---

## 6. AutoUnfreezeJobHandler

新建 `AutoUnfreezeJobHandler.java`（accounting-job 模块）：
- XXL-JOB Handler，每 5 分钟执行
- `freezeDetailRepository.selectExpiredRecords(now)` → 逐笔调用 `autoUnfreezeOne`
- 单笔失败不中断，记录错误日志

---

## 7. 错误码

| Code | Enum | Description |
|------|------|-------------|
| `"3016"` | `FREEZE_AMOUNT_INVALID` | 冻结金额无效（<=0 或超过可用余额） |
| `"3017"` | `FREEZE_RECORD_NOT_FOUND` | 冻结记录不存在 |
| `"3018"` | `FREEZE_STATUS_INVALID` | 冻结记录状态非法（非冻结中） |
| `"3019"` | `FREEZE_AMOUNT_EXCEEDED` | 解冻/扣款金额超过冻结金额 |
| `"3020"` | `ACCOUNT_FROZEN_CANNOT_FREEZE` | 账户已冻结，无法执行资金冻结 |

---

## 8. 完成标准（Checklist）

- [X] P0-1: `SubAccountMapper.updateBalance` + `selectByAccountNoAndType` + XML
- [X] P0-2: `SubAccountMapper.selectByAccountNo` 已存在
- [X] P0-3: `SubAccountRepository.updateBalance`（含乐观锁拦截）+ `selectByAccountNoAndType`
- [X] P0-4: `SubAccountDetailMapper.insert` 通过 BaseMapper 可用
- [X] P0-5: `AccountFreezeDetailMapper.selectByVoucherNo` + `updateStatus` + XML
- [X] P0-6: `AccountFreezeDetailPO` 映射确认
- [X] FreezeIdGenerator（Lua + Redis INCR，FRZ + yyyyMMdd + seq6）
- [X] FreezeDetailRepository（insert/selectByVoucherNo/selectExpiredRecords/updateStatus/selectByCondition）
- [X] `FreezeDomainService.freezeFund`（可用→冻结 + 明细记录 + 分布式锁 + 双重检查 + 乐观锁）
- [X] `FreezeDomainService.unfreezeFund`（冻结→可用 + 状态更新）
- [X] `FreezeDomainService.deductFromFreeze`（冻结余额减少 + 子账户明细）
- [X] 严禁负数运算（先校验后运算）
- [X] 子账户明细完整记录（preBalance / postBalance）
- [X] 冻结记录含过期时间（expireTime 默认 2099-12-31）
- [X] 并发安全：`DistributedLockTemplate`（`account:fund:{accountNo}`）+ 双重检查 + 乐观锁
- [X] `AutoUnfreezeJobHandler` 定时任务（扫描过期记录，单笔失败不中断）
- [X] ResultCode 新增 3016~3020 错误码
- [X] 编译通过（mvn compile -DskipTests SUCCESS）
- [X] 架构决策：锁和事务控制权在 DomainService 内部管理（沿用 Step 13 模式）
