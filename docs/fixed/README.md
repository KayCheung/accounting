# Fixed 目录说明

本目录包含账务核心系统设计的修复版本和补充内容。

---

## 📂 目录结构

```
docs/fixed/
├── adjustment/              # 数据库调整脚本
│   ├── 2-schema-adjustment-20260301.sql
│   ├── 3-schema-rollback-20260301.sql
│   ├── 4-schema-adjustment-20260301-v2.sql
│   ├── 5-schema-rollback-20260301-v2.sql
│   ├── apply_fixes_guide.md
│   ├── database_adjustment_recommendations.md
│   ├── database_adjustment_summary.md
│   └── step_5_8_corrections.md
├── design/                  # 设计文档
│   └── flowchat/           # 流程图（Mermaid 格式）
│       ├── account_opening_flow.mmd
│       ├── auto_account_opening_flow.mmd
│       ├── buffer_posting_modes.mmd
│       ├── eod_five_phases.mmd
│       ├── freeze_unfreeze_flow.mmd
│       ├── period_end_transfer_flow.mmd
│       ├── reversal_flow.mmd
│       ├── standard_posting_flow_detailed.mmd
│       ├── transaction_rollback_flow.mmd
│       └── README.md
├── prompt/                  # 提示词文档
│   ├── step_1_Project_Initialization_API.md
│   ├── step_2_Middleware_Integration.md
│   ├── step_3_Domain_Persistence_Layer.md
│   └── step_4_Configuration_Module_Dict_Subject.md
├── review/                  # 检查与修复报告
│   ├── design_review_20260301.md
│   ├── fix_plan_20260301.md
│   ├── fixes_summary_20260301.md
│   ├── completion_report_20260301.md
│   ├── WORK_SUMMARY.md
│   └── QUICK_REFERENCE.md
├── sql/                     # 完整 SQL 文件
│   ├── 0-database-schema.sql
│   └── 1-init-schema-fixed.sql
└── README.md               # 本文档
```

---

## 🚀 快速开始

### 1. 查看设计检查报告
```bash
# 查看完整的设计检查报告
cat docs/fixed/review/design_review_20260301.md
```

### 2. 查看修复总结
```bash
# 查看修复总结
cat docs/fixed/review/fixes_summary_20260301.md
```

### 3. 查看快速参考
```bash
# 查看快速参考指南
cat docs/fixed/review/QUICK_REFERENCE.md
```

### 4. 应用 SQL 修复
```bash
# 方式 1: 执行调整脚本
mysql -u root -p accounting < docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql

# 方式 2: 手动应用（参考应用指南）
cat docs/fixed/adjustment/apply_fixes_guide.md
```

### 5. 查看流程图
```bash
# 使用 Mermaid 工具查看流程图
# 推荐使用 VS Code 插件：Markdown Preview Mermaid Support
code docs/fixed/design/flowchat/
```

---

## 📋 核心文档索引

### 设计检查与修复
- **设计检查报告**: `review/design_review_20260301.md`
  - 发现的 10 个问题点
  - 3 个待明确的设计决策
  - 下一步行动建议

- **修复计划**: `review/fix_plan_20260301.md`
  - 修复清单
  - 新需求补充
  - 执行顺序

- **修复总结**: `review/fixes_summary_20260301.md`
  - 已完成的修复
  - 已补充的新需求
  - 待更新的文档

- **完成报告**: `review/completion_report_20260301.md`
  - 完成概览
  - 核心修复说明
  - 新需求说明
  - 后续工作

- **工作总结**: `review/WORK_SUMMARY.md`
  - 工作目标
  - 完成情况
  - 产出物清单
  - 核心成果

- **快速参考**: `review/QUICK_REFERENCE.md`
  - 修复速查表
  - 新需求速查表
  - 文件位置速查
  - 常见问题速查

### 数据库调整
- **调整脚本 V2**: `adjustment/4-schema-adjustment-20260301-v2.sql`
  - 新增本地消息表
  - 缓冲明细表新增会计日期字段
  - 简化事务状态枚举
  - 统一凭证状态枚举
  - orig_voucher_no 改为可空
  - 新增期末结转相关表

- **回滚脚本 V2**: `adjustment/5-schema-rollback-20260301-v2.sql`
  - 回滚所有 V2 调整

- **应用指南**: `adjustment/apply_fixes_guide.md`
  - 手动应用修复到完整 SQL 文件的步骤

### 流程图
- **标准入账全流程**: `design/flowchat/standard_posting_flow_detailed.mmd`
- **缓冲记账三种模式**: `design/flowchat/buffer_posting_modes.mmd`
- **红冲流程**: `design/flowchat/reversal_flow.mmd`
- **逻辑日切五阶段**: `design/flowchat/eod_five_phases.mmd`
- **冻结/解冻流程**: `design/flowchat/freeze_unfreeze_flow.mmd`
- **账户开户流程**: `design/flowchat/account_opening_flow.mmd`
- **事务回滚流程**: `design/flowchat/transaction_rollback_flow.mmd`
- **记账自动开户流程**: `design/flowchat/auto_account_opening_flow.mmd` ⭐ 新增
- **期末结转流程**: `design/flowchat/period_end_transfer_flow.mmd` ⭐ 新增
- **流程图索引**: `design/flowchat/README.md`

### 提示词文档
- **Step 1**: `prompt/step_1_Project_Initialization_API.md` - 工程初始化与 API 基础设施
- **Step 2**: `prompt/step_2_Middleware_Integration.md` - 中间件集成与基础设施封装
- **Step 3**: `prompt/step_3_Domain_Persistence_Layer.md` - 领域持久层
- **Step 4**: `prompt/step_4_Configuration_Module_Dict_Subject.md` - 配置管理模块

---

## 🎯 核心修复内容

### 高优先级修复（3 个）
1. ✅ 补充本地消息表 DDL - 保证 MQ 消息发送的可靠性
2. ✅ 缓冲明细表新增会计日期字段 - 支持日切流程按会计日期扫描
3. ✅ 明确冻结/解冻流程 - 决策：不生成凭证，直接操作子账户

### 中优先级修复（4 个）
4. ✅ 简化事务状态枚举 - 从 6 个状态简化为 3 个状态
5. ✅ 统一状态枚举值 - 凭证状态调整为 5 个状态
6. ✅ orig_voucher_no 改为可空 - 便于区分正常凭证和红冲凭证
7. ✅ 补充子账户明细表用途说明 - 用于冻结/解冻审计

---

## 🆕 新增需求

### 需求 1: 记账接口支持自动开户
- **场景**: 解决已经在线上运行的业务，没有事先开户的问题
- **实现**: 记账时检查账户是否存在，不存在则根据开户模板自动开户
- **流程图**: `design/flowchat/auto_account_opening_flow.mmd`

### 需求 2: 补充期末结转流程
- **场景**: 会计期末需要将损益类科目余额结转到本年利润科目
- **实现**: 基于期末结转规则，自动生成结转凭证并执行入账
- **流程图**: `design/flowchat/period_end_transfer_flow.mmd`
- **数据库表**: `t_period_end_transfer_rule`, `t_period_end_transfer_record`

---

## 📊 统计数据

- **修复问题数**: 7 个（3 个高优先级 + 4 个中优先级）
- **新增需求数**: 2 个
- **新增流程图**: 2 个
- **新增数据库表**: 3 个
- **修改数据库表**: 3 个
- **更新文档数**: 5 个

---

## ⏳ 待完成工作

### 高优先级
1. 更新 `.kiro/steering/03-architecture-requirements.md`
   - 补充子账户明细表用途说明
   - 补充记账自动开户流程
   - 补充期末结转流程

2. 手动应用修复到完整 SQL 文件
   - 参考 `adjustment/apply_fixes_guide.md`
   - 更新 `sql/1-init-schema-fixed-v2.sql`

### 中优先级
3. 更新枚举类定义（Java 代码）
4. 更新业务代码（支持新需求）

---

## 📝 使用建议

### 开发阶段
1. 先阅读 `review/QUICK_REFERENCE.md` 快速了解修复内容
2. 查看 `review/design_review_20260301.md` 了解问题背景
3. 参考流程图实现业务逻辑

### 数据库变更
1. 备份当前数据库
2. 执行 `adjustment/4-schema-adjustment-20260301-v2.sql`
3. 验证表结构和索引

### 代码实现
1. 更新枚举类定义
2. 实现记账自动开户功能
3. 实现期末结转功能
4. 实现本地消息表机制

---

## 🔗 相关链接

- **原始设计文档**: `docs/design/`
- **原始 SQL 文件**: `docs/sql/`
- **Steering 文件**: `.kiro/steering/`

---

## 📞 联系方式

如有疑问，请查阅相关文档或联系开发团队。

---

**创建人**: Kiro AI  
**创建时间**: 2026-03-01  
**版本**: v1.0  
**状态**: ✅ 核心工作已完成
