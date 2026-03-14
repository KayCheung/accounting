# docs/sql · DDL 文件说明

> 本目录为**只读参考资源**，严禁 AI 修改。
> 共 27 张表，按业务域拆分为 6 个文件。

---

## 文件清单

| 文件 | 域 | 表数 | 包含表 |
|------|----|------|--------|
| `0-database-schema.sql` | 初始化 | — | 库创建、字符集（utf8mb4）、排序规则 |
| `1-account.sql` | 账户域 | 7 | t_account · t_sub_account · t_account_detail · t_sub_account_detail · t_account_freeze_detail · t_account_balance · t_account_balance_snapshot |
| `2-voucher.sql` | 凭证域 | 4 | t_accounting_voucher · t_accounting_voucher_entry · t_accounting_voucher_auxiliary · t_accounting_voucher_attachment |
| `3-rule.sql` | 规则域 | 5 | t_accounting_rule · t_accounting_rule_detail · t_accounting_rule_auxiliary · t_buffer_posting_rule · t_buffer_posting_detail |
| `4-subject.sql` | 科目域 | 3 | t_account_subject · t_account_subject_auxiliary · t_account_template |
| `5-journal.sql` | 流水域 | 3 | t_business_record · t_business_detail · t_transaction |
| `6-infra.sql` | 支撑域 | 5 | t_dictionary · t_local_message · t_message_receipt · t_period_end_transfer_rule · t_period_end_transfer_record |

---

## 读取策略

| 场景 | 读取内容 |
|------|----------|
| 日常开发（业务逻辑、Service、Controller） | 只读 `docs/design/domain-model.md` |
| 生成 PO / Mapper（Step 3） | 读对应域的完整 DDL 文件 |
| 跨域联查或全量审计（Step 2） | 按需读取相关域文件，无需全量 |
| Code Review 字段对齐校验 | 读对应域 DDL 文件精确比对 |

> **原则**：轻量摘要（`domain-model.md`）覆盖 90% 场景，完整 DDL 按需加载，避免全量注入上下文。
