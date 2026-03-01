# 快速参考指南

**日期**: 2026-03-01  
**用途**: 快速查阅修复内容和新需求

---

## 📋 修复速查表

| 修复项 | 类型 | 文件 | 说明 |
|-------|------|------|------|
| 本地消息表 | 新增表 | `4-schema-adjustment-20260301-v2.sql` | 保证 MQ 消息可靠性 |
| 缓冲明细会计日期 | 新增字段 | `4-schema-adjustment-20260301-v2.sql` | 支持日切按日期扫描 |
| 事务状态简化 | 修改枚举 | `4-schema-adjustment-20260301-v2.sql` | 3 个状态：处理中/成功/失败 |
| 凭证状态统一 | 修改枚举 | `4-schema-adjustment-20260301-v2.sql` | 5 个状态：未过账/过账中/已过账/过账失败/已冲销 |
| orig_voucher_no | 改为可空 | `4-schema-adjustment-20260301-v2.sql` | 正常凭证 NULL，红冲凭证为原凭证号 |
| 冻结/解冻 | 设计决策 | `freeze_unfreeze_flow.mmd` | 不生成凭证，直接操作子账户 |
| 子账户明细 | 文档说明 | 待更新到 Steering | 用于冻结/解冻审计 |

---

## 🆕 新需求速查表

| 需求 | 流程图 | 数据库表 | 说明 |
|------|--------|---------|------|
| 记账自动开户 | `auto_account_opening_flow.mmd` | 无新增表 | 记账时检查账户，不存在则自动开户 |
| 期末结转 | `period_end_transfer_flow.mmd` | `t_period_end_transfer_rule`<br/>`t_period_end_transfer_record` | 基于规则自动生成结转凭证 |

---

## 📂 文件位置速查

### SQL 脚本
```
docs/fixed/adjustment/
├── 4-schema-adjustment-20260301-v2.sql    # 调整脚本
├── 5-schema-rollback-20260301-v2.sql      # 回滚脚本
└── apply_fixes_guide.md                    # 应用指南
```

### 流程图
```
docs/fixed/design/flowchat/
├── auto_account_opening_flow.mmd           # 记账自动开户
├── period_end_transfer_flow.mmd            # 期末结转
└── README.md                                # 流程图索引（已更新）
```

### 文档
```
docs/fixed/review/
├── design_review_20260301.md               # 设计检查报告
├── fix_plan_20260301.md                    # 修复计划
├── fixes_summary_20260301.md               # 修复总结
├── completion_report_20260301.md           # 完成报告
├── WORK_SUMMARY.md                         # 工作总结
└── QUICK_REFERENCE.md                      # 本文档
```

### Steering 文件
```
.kiro/steering/
├── 01-governance-constraints.md            # 开发契约（无修改）
├── 02-resource-alignment.md                # 资源对齐（已更新）
├── 03-architecture-requirements.md         # 业务架构（待更新）
└── 04-technical-standards.md               # 技术规范（无修改）
```

---

## 🔧 SQL 应用步骤

### 方式 1: 执行调整脚本（推荐）
```sql
-- 1. 备份数据库
mysqldump -u root -p accounting > accounting_backup_20260301.sql

-- 2. 执行调整脚本
mysql -u root -p accounting < docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql

-- 3. 验证表结构
SHOW CREATE TABLE t_local_message;
SHOW CREATE TABLE t_buffer_posting_detail;
SHOW CREATE TABLE t_transaction;
SHOW CREATE TABLE t_accounting_voucher;
SHOW CREATE TABLE t_period_end_transfer_rule;
SHOW CREATE TABLE t_period_end_transfer_record;
```

### 方式 2: 手动应用到完整 SQL 文件
参考 `docs/fixed/adjustment/apply_fixes_guide.md`

---

## 📊 状态枚举速查

### 事务状态（简化后）
```java
public enum TransactionStatusEnum {
    PROCESSING(1, "处理中"),
    SUCCESS(2, "成功"),
    FAILED(3, "失败");
}
```

### 凭证状态（统一后）
```java
public enum VoucherStatusEnum {
    UNPOSTED(1, "未过账"),
    POSTING(2, "过账中"),
    POSTED(3, "已过账"),
    POST_FAILED(4, "过账失败"),
    REVERSED(5, "已冲销");
}
```

### 分录状态（不变）
```java
public enum EntryStatusEnum {
    UNPOSTED(1, "未过账"),
    POSTED(2, "已过账"),
    POST_FAILED(3, "过账失败");
}
```

---

## 🎯 关键决策速查

| 决策点 | 决策结果 | 理由 |
|-------|---------|------|
| 冻结/解冻是否生成凭证？ | 否 | 简化流程，避免影响日终试算平衡 |
| 本地消息表是否必须？ | 是 | 保证 MQ 消息发送的可靠性 |
| 子账户明细表是否必须？ | 是 | 用于冻结/解冻场景的审计 |
| 事务状态是否简化？ | 是 | 通过凭证和分录状态判断"部分成功" |
| orig_voucher_no 是否可空？ | 是 | 便于区分正常凭证和红冲凭证 |

---

## 🔍 常见问题速查

### Q1: 为什么要新增本地消息表？
**A**: 保证 MQ 消息发送的可靠性。如果 MQ 发送失败，可以通过本地消息表重试，避免消息丢失导致异步分录永远无法处理。

### Q2: 为什么冻结/解冻不生成凭证？
**A**: 冻结/解冻是子账户间的余额划转，不涉及会计科目变动。如果生成凭证，会影响日终试算平衡（借贷发生额增加，但余额不变）。

### Q3: 为什么要简化事务状态？
**A**: 原来的 6 个状态过于复杂，"部分提交"、"部分回滚"的定义不清晰。简化为 3 个状态后，通过凭证和分录的状态来判断是否"部分成功"。

### Q4: 为什么要统一凭证状态和分录状态？
**A**: 原来凭证状态和分录状态的枚举值 3 含义不同（凭证是"已过账"，分录是"过账失败"），容易混淆。统一后，凭证状态调整为 5 个状态，避免冲突。

### Q5: 记账自动开户的触发时机？
**A**: 在"阶段二：凭证生成"之前，检查涉及的账户是否存在。如果不存在，则根据开户模板自动开户。

### Q6: 期末结转的执行时机？
**A**: 在日切流程的"阶段 5：归档"之前执行。确保当前会计期间已关账，且未执行过期末结转。

---

## 📞 联系方式

如有疑问，请参考以下文档：
- 详细设计：`docs/fixed/review/design_review_20260301.md`
- 修复总结：`docs/fixed/review/fixes_summary_20260301.md`
- 完成报告：`docs/fixed/review/completion_report_20260301.md`
- 工作总结：`docs/fixed/review/WORK_SUMMARY.md`

---

**创建人**: Kiro AI  
**创建时间**: 2026-03-01  
**版本**: v1.0
