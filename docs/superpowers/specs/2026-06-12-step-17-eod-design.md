# Step 17: EOD & Trial Balance Design Spec

> 日切与试算平衡引擎设计文档

---

## 1. 概述

实现日切与试算平衡引擎，作为 Phase 6 的日终整体收尾流程。

### 执行流程

```
EodJobHandler (23:55) → EodApplicationService
  ├── Step 1: EodCheckDomainService.checkEodPreconditions    — 5项前置检查
  ├── Step 2: EodDomainService.calculateDailyBalances        — 日余额计算
  ├── Step 3: TrialBalanceDomainService.executeTrialBalance  — 试算平衡
  ├── Step 4: PeriodEndTransferDomainService.executeTransferRules — 期末结转
  └── Step 5: EodDomainService.generateDailySnapshot         — 日/月快照
```

### 异常阻断策略

| 步骤 | 失败行为 |
|------|---------|
| Step 1 前置检查 | 阻断 + 返回 EOD_BLOCKED |
| Step 3 试算平衡 | 阻断 + 返回 TRIAL_BALANCE_FAILED |
| Step 4 期末结转 | 单条失败不阻断，记录 status=3 |
| Step 5 快照生成 | 告警不阻断 |

### 幂等性保证

- 日余额：upsert 语义
- 快照：INSERT IGNORE（唯一键冲突时跳过）
- 结转：检查当日已有结转记录则跳过
- 检查/试算：纯查询天然幂等

---

## 2. 新建文件清单（15 个）

### 2.1 Repository（4 个）

- `accounting-core/.../repository/AccountBalanceRepository.java`
- `accounting-core/.../repository/AccountBalanceSnapshotRepository.java`
- `accounting-core/.../repository/PeriodEndTransferRuleRepository.java`
- `accounting-core/.../repository/PeriodEndTransferRecordRepository.java`

### 2.2 DomainService（4 个）

- `accounting-core/.../domain/service/EodCheckDomainService.java`
- `accounting-core/.../domain/service/TrialBalanceDomainService.java`
- `accounting-core/.../domain/service/EodDomainService.java`
- `accounting-core/.../domain/service/PeriodEndTransferDomainService.java`

### 2.3 Application 层（3 个）

- `accounting-core/.../application/service/EodApplicationService.java`
- `accounting-core/.../application/assembler/EodAssembler.java`
- `accounting-core/.../interfaces/EodController.java`

### 2.4 Job（1 个）

- `accounting-job/.../job/EodJobHandler.java`

### 2.5 DTO（4 个）

- `accounting-api/request/EodExecuteRequest.java`
- `accounting-api/response/EodExecuteResponse.java`
- `accounting-api/response/EodPreCheckResponse.java`
- `accounting-api/response/TrialBalanceResponse.java`

---

## 3. 修改文件清单（8 个）

| 文件 | 变更 |
|------|------|
| AccountBalanceMapper | `batchUpsertBalance` + XML |
| AccountBalanceSnapshotMapper | `batchInsertSnapshot` + XML |
| PeriodEndTransferRuleMapper | `selectEnabledRules` default 方法 |
| PeriodEndTransferRecordMapper | `selectByAccountingDate` default 方法 |
| TransactionMapper | `countByAccountingDateAndStatus` + XML |
| AccountingVoucherEntryMapper | `sumEntriesBySubject` + XML |
| AccountingVoucherMapper | `countByAccountingDateAndStatus` default 方法 |
| ResultCode | 新增 2026~2029 错误码 |

---

## 4. 关键业务规则

### 4.1 日切前置检查（5 项）

1. 缓冲明细：当日无 status IN (1,2) 的记录
2. 事务：当日无 status=1 的记录
3. 凭证：当日无 status IN (1,2) 的记录
4. 业务流水：当日无 status=1 的记录
5. 冻结记录：无过期未解冻记录

### 4.2 日余额计算

```
begin_balance = 前一日 end_balance (或 opening_balance)
debit_amount = SUM(amount WHERE debit_credit=1 AND status=2)
credit_amount = SUM(amount WHERE debit_credit=2 AND status=2)
end_balance (借方账户) = begin + debit - credit
end_balance (贷方账户) = begin + credit - debit
校验: end_balance >= 0, 否则告警
```

### 4.3 试算平衡

```
total_debit_all = SUM(total_debit across all subjects)
total_credit_all = SUM(total_credit across all subjects)
diff = |total_debit_all - total_credit_all|
diff <= 0.000001 → 通过
```

### 4.4 期末结转

- 按 execute_order 升序逐条执行
- 通配符解析: `6*` → `6%`
- 结转方向: DEBIT_TO_CREDIT / CREDIT_TO_DEBIT
- 单条失败不中断
- 幂等: 当日已有记录则跳过

---

## 5. 事务规范

- 严禁使用 `@Transactional`，全部使用 `TransactionTemplate`
- 每条结转规则独立事务
- 日余额批量 upsert 独立事务
- 快照批量 insert 独立事务
