# step-14-java-b · 冻结扣款凭证生成 + 余额校验强化

> **Step 14 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 14 Java-A（领域服务 + P0 补充已就绪）

---

## 1. 任务目标

在 Java-A 的基础上，强化冻结/解冻/扣款的金额校验逻辑，
并为冻结扣款场景预留凭证生成能力。

---

## 2. 金额校验

### 2.1 冻结金额合法性

- `freezeAmount > 0`
- `freezeAmount <= availableSub.balance`
- 可用子账户必须存在
- 主账户状态必须为 NORMAL

### 2.2 解冻金额合法性

- `unfreezeAmount > 0`
- `unfreezeAmount <= freezeRecord.freezeAmount`
- 冻结记录状态必须为 FROZEN

### 2.3 扣款金额合法性

- `deductAmount > 0`
- `deductAmount <= freezeRecord.freezeAmount`
- 冻结记录状态必须为 FROZEN
- 冻结子账户余额 >= deductAmount

---

## 3. 冻结扣款凭证生成

当前阶段 `deductFromFreeze` 方法已完成子账户侧的余额转移操作。
主账户余额减少和凭证生成需在后续阶段实现，涉及：
- 委托 Step 10 `VoucheringDomainService` 生成扣款凭证
- 写入主账户明细 `t_account_detail`
- 主账户余额 `t_account.balance` 减少

---

## 4. 完成标准（Checklist）

- [X] 冻结金额合法性校验（> 0 + 可用余额充足 + 主账户状态 NORMAL）
- [X] 解冻金额合法性校验（> 0 + <= 冻结金额 + 状态 FROZEN）
- [X] 扣款金额合法性校验（> 0 + <= 冻结金额 + 冻结余额充足 + 状态 FROZEN）
- [X] 严禁负数运算（`compareTo` 先校验后 `subtract/add`）
- [X] 冻结扣款子账户侧余额转移已完成（冻结余额减少 + 状态更新为 UNFROZEN）
- [X] 主账户余额减少（deductFromFreeze 已实现双重检查 + 乐观锁更新 + 主账户明细写入）
- [ ] 扣款凭证生成委托 Step 10（待后续阶段实现）
- [X] 主账户明细写入 `t_account_detail`（已实现 insertAccountDetail）
