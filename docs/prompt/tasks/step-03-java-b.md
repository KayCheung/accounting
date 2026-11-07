# step-03-java-b · 凭证域 + 规则域持久层（Java-B）

> 本文件为 Java-B 的独立任务文件，对应 `step-03-codegen.md` 中的凭证域 + 规则域部分。
> 执行前请先阅读 `step-03-codegen.md` 中的通用生成规范。
> ⚠️ 依赖 Java-A 的枚举产出（`DebitCreditEnum` / `ChangeDirectionEnum`），请确认 Java-A 已完成后再执行。

---

## 任务范围

**负责 DDL 文件**：`docs/sql/2-voucher.sql` + `docs/sql/3-rule.sql`
**表数**：9 张 ｜ **枚举数**：9 个（另复用 Java-A 的 2 个）

---

## 表与 PO 对照

| 表名 | PO 类名 | 特殊说明 |
|------|---------|----------|
| `t_accounting_voucher` | `AccountingVoucherPO` | `version` → `@Version`；`status` → `VoucherStatusEnum`；`tradeType` → `TradeTypeEnum`；`postingType` → `PostingTypeEnum` |
| `t_accounting_voucher_entry` | `AccountingVoucherEntryPO` | `version` → `@Version`；`status` → `VoucherEntryStatusEnum`；`debitCredit` → 复用 `DebitCreditEnum` |
| `t_accounting_voucher_auxiliary` | `AccountingVoucherAuxiliaryPO` | `changeDirection` → 复用 `ChangeDirectionEnum` |
| `t_accounting_voucher_attachment` | `AccountingVoucherAttachmentPO` | 无特殊枚举 |
| `t_accounting_rule` | `AccountingRulePO` | `status` → `RuleStatusEnum` |
| `t_accounting_rule_detail` | `AccountingRuleDetailPO` | `accountScope` → `AccountScopeEnum`；`debitCredit` → 复用；`isUnilateral` → `Boolean unilateral` |
| `t_accounting_rule_auxiliary` | `AccountingRuleAuxiliaryPO` | `allocationMethod` → `AllocationMethodEnum` |
| `t_buffer_posting_rule` | `BufferPostingRulePO` | `bufferMode` → `BufferModeEnum`；`debitCredit` → 复用 |
| `t_buffer_posting_detail` | `BufferPostingDetailPO` | `version` → `@Version`；`status` → `BufferStatusEnum`；`debitCredit` → 复用 |

---

## 枚举清单

**本人定义（9 个）**：

> ⚠️ `TradeTypeEnum` 由 Java-B 定义，**Java-C 会复用**，请务必完整定义。

| 枚举类名 | 枚举值 |
|---------|--------|
| `VoucherStatusEnum` | 1-未过账 / 2-过账中 / 3-已过账 / 4-过账失败 / 5-已冲销 |
| `VoucherEntryStatusEnum` | 1-未过账 / 2-已过账 / 3-过账失败 |
| `TradeTypeEnum` | 1-正常 / 2-调账 / 3-红 / 4-蓝 |
| `PostingTypeEnum` | 1-手工凭证 / 2-机制凭证 |
| `RuleStatusEnum` | 1-待启用 / 2-启用 / 3-停用 |
| `AccountScopeEnum` | 1-内部分户 / 2-外部分户 |
| `AllocationMethodEnum` | 1-不分摊 / 2-固定金额 / 3-按比例 |
| `BufferModeEnum` | 1-异步逐条 / 2-日间批量 / 3-日终批量 |
| `BufferStatusEnum` | 1-待入账 / 2-处理中 / 3-成功 / 4-失败 |

**复用 Java-A（不重复定义）**：`DebitCreditEnum` / `ChangeDirectionEnum`

---

## Prompt 模板

```
@Java

【任务】
基于 docs/sql/2-voucher.sql 和 docs/sql/3-rule.sql，
生成凭证域（4 张）+ 规则域（5 张）共 9 张表的 PO、Mapper 接口、Mapper XML 和枚举

【输入】
- docs/sql/2-voucher.sql（完整 DDL，必读）
- docs/sql/3-rule.sql（完整 DDL，必读）
- docs/design/domain-model.md（凭证域、规则域部分）
- docs/ai-rules/java.md（编码规范）
- docs/prompt/step-03-codegen.md（通用规范）
- docs/prompt/step-03-java-b.md（本文件，表与枚举对照）
- 复用枚举路径：accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/
  （DebitCreditEnum / ChangeDirectionEnum 由 Java-A 已定义，直接 import 复用）

【输出】
- 9 个 PO 类（继承 BaseEntity，含枚举映射）
- 9 个 Mapper 接口
- 9 个 Mapper XML
- 9 个枚举类（不含复用的 2 个）
每个文件首行注释写完整路径

【不要做】
- 不要生成 Service / Controller 层
- 不要重复定义 DebitCreditEnum / ChangeDirectionEnum
- 不要在 PO 中重复定义 BaseEntity 已有字段
- 不要使用基本数据类型
```

---

## 完成标准（Checklist）

- [ ] 凭证域 + 规则域 9 个 PO 生成完毕，无重复字段
- [ ] 9 个 Mapper 接口和 XML 生成完毕
- [ ] 9 个枚举类生成完毕，未重复定义 `DebitCreditEnum` / `ChangeDirectionEnum`
- [ ] `AccountingVoucherPO` / `AccountingVoucherEntryPO` / `BufferPostingDetailPO` 的 `version` 已标注 `@Version`
- [ ] 通知 Java-C `TradeTypeEnum` 已就绪，可开始复用
