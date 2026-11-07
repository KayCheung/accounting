# step-03-java-c · 科目域 + 流水域 + 支撑域持久层（Java-C）

> 本文件为 Java-C 的独立任务文件，对应 `step-03-codegen.md` 中的科目域 + 流水域 + 支撑域部分。
> 执行前请先阅读 `step-03-codegen.md` 中的通用生成规范。
> ⚠️ 依赖 Java-A 的 `DebitCreditEnum` / `BalanceDirectionEnum` 和 Java-B 的 `TradeTypeEnum`，请确认两人已完成后再执行。

---

## 任务范围

**负责 DDL 文件**：`docs/sql/4-subject.sql` + `docs/sql/5-journal.sql` + `docs/sql/6-infra.sql`
**表数**：11 张 ｜ **枚举数**：12 个（另复用 Java-A 2 个、Java-B 1 个）

---

## 表与 PO 对照

| 表名 | PO 类名 | 特殊说明 |
|------|---------|----------|
| `t_account_subject` | `AccountSubjectPO` | `subjectCategory` → `SubjectCategoryEnum`；`nature` → `SubjectNatureEnum`；`debitCredit` → 复用；`isLeaf` → `Boolean leaf`；`allowPost` → `Boolean allowPost` |
| `t_account_subject_auxiliary` | `AccountSubjectAuxiliaryPO` | `required` → `Boolean required` |
| `t_account_template` | `AccountTemplatePO` | `customerType` → `CustomerTypeEnum`；`autoOpen` → `Boolean autoOpen`；`balanceDirection` → 复用；`status` → `TemplateStatusEnum` |
| `t_business_record` | `BusinessRecordPO` | `version` → `@Version`；`tradeType` → 复用 `TradeTypeEnum`；`status` → `BusinessRecordStatusEnum` |
| `t_business_detail` | `BusinessDetailPO` | `customerType` → 复用 `CustomerTypeEnum` |
| `t_transaction` | `TransactionPO` | `version` → `@Version`；`status` → `TransactionStatusEnum` |
| `t_dictionary` | `DictionaryPO` | `status` → `AvailableStatusEnum`；`isSystem` → `Boolean system` |
| `t_local_message` | `LocalMessagePO` | ⚠️ **无 `tenant_id`，不继承 `BaseEntity`**，单独定义所有字段；`status` → `MessageStatusEnum` |
| `t_message_receipt` | `MessageReceiptPO` | ⚠️ **无 `tenant_id`，不继承 `BaseEntity`**；`status` → `ReceiptStatusEnum` |
| `t_period_end_transfer_rule` | `PeriodEndTransferRulePO` | `transferType` → `TransferTypeEnum`；`transferDirection` → `TransferDirectionEnum`；`status` → 复用 `AvailableStatusEnum` |
| `t_period_end_transfer_record` | `PeriodEndTransferRecordPO` | `transferType` → 复用；`status` → `TransferRecordStatusEnum` |

---

## 枚举清单

**本人定义（12 个）**：

| 枚举类名 | 枚举值 |
|---------|--------|
| `SubjectCategoryEnum` | 0-表外 / 1-资产 / 2-负债 / 3-权益 / 4-共同 / 5-成本 / 6-损益 |
| `SubjectNatureEnum` | 1-非特殊 / 2-销账 / 3-贷款 / 4-现金 |
| `CustomerTypeEnum` | 1-个人 / 2-企业 / 99-其他 |
| `TemplateStatusEnum` | 1-待启用 / 2-启用 / 3-停用 |
| `BusinessRecordStatusEnum` | 1-处理中 / 2-成功 / 3-失败 |
| `TransactionStatusEnum` | 1-处理中 / 2-成功 / 3-失败 |
| `AvailableStatusEnum` | 1-启用 / 2-停用 |
| `MessageStatusEnum` | 1-待发送 / 2-已发送 / 3-失败 / 4-已确认 |
| `ReceiptStatusEnum` | 1-成功 / 2-失败 |
| `TransferTypeEnum` | 1-损益结转 / 2-成本结转 / 3-自定义 |
| `TransferDirectionEnum` | 1-借转贷 / 2-贷转借 |
| `TransferRecordStatusEnum` | 1-处理中 / 2-成功 / 3-失败 |

**复用（不重复定义）**：
- Java-A：`DebitCreditEnum` / `BalanceDirectionEnum`
- Java-B：`TradeTypeEnum`

---

## Prompt 模板

```
@Java

【任务】
基于 docs/sql/4-subject.sql、docs/sql/5-journal.sql、docs/sql/6-infra.sql，
生成科目域（3 张）+ 流水域（3 张）+ 支撑域（5 张）共 11 张表的 PO、Mapper 接口、Mapper XML 和枚举

【输入】
- docs/sql/4-subject.sql（完整 DDL，必读）
- docs/sql/5-journal.sql（完整 DDL，必读）
- docs/sql/6-infra.sql（完整 DDL，必读）
- docs/design/domain-model.md（科目域、流水域、支撑域部分）
- docs/ai-rules/java.md（编码规范）
- docs/prompt/step-03-codegen.md（通用规范）
- docs/prompt/tasks/step-03-java-c.md（本文件，表与枚举对照）
- 复用枚举路径：accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/
  （DebitCreditEnum / BalanceDirectionEnum 来自 Java-A；TradeTypeEnum 来自 Java-B）

【输出】
- 11 个 PO 类（LocalMessagePO / MessageReceiptPO 不继承 BaseEntity，单独定义）
- 11 个 Mapper 接口
- 11 个 Mapper XML
- 12 个枚举类（不含复用的 3 个）
每个文件首行注释写完整路径

【不要做】
- 不要生成 Service / Controller 层
- 不要重复定义 DebitCreditEnum / BalanceDirectionEnum / TradeTypeEnum
- 不要在 PO 中重复定义 BaseEntity 已有字段
- t_local_message 和 t_message_receipt 无 tenant_id，不继承 BaseEntity
- 不要使用基本数据类型
```

---

## 完成标准（Checklist）

- [ ] 科目域 + 流水域 + 支撑域 11 个 PO 生成完毕
- [ ] 11 个 Mapper 接口和 XML 生成完毕
- [ ] 12 个枚举类生成完毕，未重复定义复用枚举
- [ ] `BusinessRecordPO` / `TransactionPO` 的 `version` 已标注 `@Version`
- [ ] `LocalMessagePO` / `MessageReceiptPO` 未继承 `BaseEntity`，字段完整
