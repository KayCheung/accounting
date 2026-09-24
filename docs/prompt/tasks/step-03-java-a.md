# step-03-java-a · 账户域持久层（Java-A）

> 本文件为 Java-A 的独立任务文件，对应 `step-03-codegen.md` 中的账户域部分。
> 执行前请先阅读 `step-03-codegen.md` 中的通用生成规范。

---

## 任务范围

**负责 DDL 文件**：`docs/sql/1-account.sql`
**表数**：7 张 ｜ **枚举数**：8 个

---

## 表与 PO 对照

| 表名 | PO 类名 | 特殊说明 |
|------|---------|----------|
| `t_account` | `AccountPO` | `version` → `@Version`；`status` → `AccountStatusEnum`；`riskStatus` → `RiskStatusEnum` |
| `t_sub_account` | `SubAccountPO` | `version` → `@Version`；`balanceType` → `BalanceTypeEnum` |
| `t_account_detail` | `AccountDetailPO` | `debitCredit` → `DebitCreditEnum`；`changeDirection` → `ChangeDirectionEnum` |
| `t_sub_account_detail` | `SubAccountDetailPO` | `balanceType` → `BalanceTypeEnum`；`debitCredit` → `DebitCreditEnum` |
| `t_account_freeze_detail` | `AccountFreezeDetailPO` | `version` → `@Version`；`status` → `FreezeStatusEnum` |
| `t_account_balance` | `AccountBalancePO` | 分区表；`balanceDirection` → `BalanceDirectionEnum` |
| `t_account_balance_snapshot` | `AccountBalanceSnapshotPO` | 分区表；`snapshotType` → `SnapshotTypeEnum` |

---

## 枚举清单

> ⚠️ 这 8 个枚举由 Java-A 定义，**Java-B / Java-C 会直接复用，请务必完整定义**。

| 枚举类名 | 枚举值 |
|---------|--------|
| `AccountStatusEnum` | 1-正常 / 2-冻结 / 3-注销 |
| `RiskStatusEnum` | 1-正常 / 2-止入 / 3-止出 / 4-止入止出 |
| `BalanceTypeEnum` | 1-可用 / 2-冻结 |
| `BalanceDirectionEnum` | 1-借 / 2-贷 |
| `DebitCreditEnum` | 1-借 / 2-贷 |
| `ChangeDirectionEnum` | 1-增 / 2-减 |
| `FreezeStatusEnum` | 1-冻结 / 2-已解冻 |
| `SnapshotTypeEnum` | 1-DAY / 2-MONTH / 3-YEAR / 4-CUSTOM |

---

## Prompt 模板

```
@Java

【任务】
基于 docs/sql/1-account.sql，生成账户域全部 7 张表的 PO、Mapper 接口、Mapper XML 和枚举

【输入】
- docs/sql/1-account.sql（完整 DDL，必读）
- docs/design/domain-model.md（账户域部分）
- docs/ai-rules/java.md（编码规范）
- docs/prompt/step-03-codegen.md（通用规范）
- docs/prompt/step-03-java-a.md（本文件，表与枚举对照）
- BaseEntity 路径：accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/BaseEntity.java

【输出】
- 7 个 PO 类（继承 BaseEntity，含枚举映射）
- 7 个 Mapper 接口（继承 BaseMapper）
- 7 个 Mapper XML（基础结构）
- 8 个枚举类（含 @EnumValue + @JsonValue）
每个文件首行注释写完整路径

【不要做】
- 不要生成 Service / Controller 层
- 不要在 PO 中重复定义 BaseEntity 已有字段
- 不要使用基本数据类型
- 不要给布尔字段加 is 前缀
```

---

## 完成标准（Checklist）

- [ ] 账户域 7 个 PO 生成完毕，继承 `BaseEntity`，无重复字段
- [ ] 7 个 Mapper 接口和 XML 生成完毕
- [ ] 8 个枚举类生成完毕，含 `@EnumValue` + `@JsonValue`
- [ ] `AccountPO` / `SubAccountPO` / `AccountFreezeDetailPO` 的 `version` 已标注 `@Version`
- [ ] 分区表（`AccountBalancePO` / `AccountBalanceSnapshotPO`）正常映射
- [ ] 通知 Java-B / Java-C 枚举已就绪，可开始复用
