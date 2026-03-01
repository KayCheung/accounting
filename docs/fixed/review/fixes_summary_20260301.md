# 问题修复与需求补充总结

**日期**: 2026-03-01  
**状态**: 已完成

---

## ✅ 已完成的修复

### 高优先级修复

#### 1. 补充本地消息表 DDL ✅
- **文件**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **内容**: 新增 `t_local_message` 表
- **用途**: 保证 MQ 消息发送的可靠性，支持消息重试机制
- **关键字段**:
  - `message_id`: 消息唯一标识
  - `status`: 1-待发送, 2-发送中, 3-发送成功, 4-发送失败
  - `retry_count`: 重试次数
  - `next_retry_time`: 下次重试时间

#### 2. 缓冲明细表新增会计日期字段 ✅
- **文件**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **修改**: `t_buffer_posting_detail` 新增 `accounting_date` 字段
- **索引**: 新增 `idx_accounting_date_status (accounting_date, status, account_no)`
- **用途**: 支持日切流程按会计日期扫描缓冲明细

#### 3. 明确冻结/解冻流程 ✅
- **决策**: 冻结/解冻不生成凭证，直接操作子账户
- **理由**: 
  - 冻结/解冻是子账户间的余额划转，不涉及会计科目变动
  - 不生成凭证可以避免影响日终试算平衡
  - 通过 `t_account_freeze_detail` 和 `t_sub_account_detail` 保留审计追溯
- **流程图**: `docs/fixed/design/flowchat/freeze_unfreeze_flow.mmd` 已更新

### 中优先级修复

#### 4. 简化事务状态枚举 ✅
- **文件**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **修改**: 
  - 事务状态简化为：1-处理中(PROCESSING), 2-成功(SUCCESS), 3-失败(FAILED)
  - 删除字段：`total_entry_count`, `success_entry_count`, `pending_entry_count`, `fail_entry_count`
- **理由**: 通过凭证和分录的状态来判断是否"部分成功"，简化状态机管理

#### 5. 统一状态枚举值 ✅
- **文件**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **修改**: 凭证状态调整为：1-未过账, 2-过账中, 3-已过账, 4-过账失败, 5-已冲销
- **理由**: 避免凭证状态和分录状态的枚举值 3 含义不同（凭证是"已过账"，分录是"过账失败"）

#### 6. orig_voucher_no 改为可空 ✅
- **文件**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **修改**: `orig_voucher_no VARCHAR(32) NULL DEFAULT NULL`
- **理由**: 正常凭证为 NULL，红冲凭证为原凭证号，便于区分

#### 7. 补充子账户明细表用途说明 ✅
- **文件**: 待更新到 Steering 文件
- **说明**: 
  - 子账户明细表（`t_sub_account_detail`）用于记录子账户的余额变化
  - 主要场景：冻结/解冻、充值/扣款
  - 与账户明细表（`t_account_detail`）的区别：
    - 账户明细：记录主账户的余额变化，用于日终试算平衡
    - 子账户明细：记录子账户的余额变化，用于冻结/解冻审计

---

## 🆕 已补充的新需求

### 需求 1: 记账接口支持自动开户 ✅

#### 业务场景
- 解决已经在线上运行的业务，没有事先开户的问题
- 记账时自动检查账户是否存在，不存在则根据开户模板自动开户

#### 实现要点
1. **账户存在性检查**: 在记账流程的"阶段二：凭证生成"之前，检查涉及的账户是否存在
2. **匹配开户模板**: 根据 `business_code + customer_type + subject_code` 匹配开户模板
3. **检查自动开户标识**: 检查模板的 `auto_open` 字段是否为 1
4. **执行自动开户**: 调用开户接口，创建 `t_account` 和 `t_sub_account`
5. **失败处理**: 
   - 模板不存在：抛出 `ACCOUNT_TEMPLATE_NOT_FOUND`
   - 模板不支持自动开户：抛出 `AUTO_OPEN_NOT_SUPPORTED`
   - 开户失败：抛出 `AUTO_OPEN_FAILED`

#### 产出物
- **流程图**: `docs/fixed/design/flowchat/auto_account_opening_flow.mmd`
- **Steering 文件**: 待更新到 `03-architecture-requirements.md`

---

### 需求 2: 补充期末结转流程 ✅

#### 业务场景
- 会计期末需要将损益类科目余额结转到本年利润科目
- 支持自定义结转规则，如成本结转、自定义结转

#### 实现要点
1. **前置校验**: 
   - 检查当前会计期间是否已关账
   - 检查是否已执行期末结转（避免重复执行）
2. **加载结转规则**: 查询启用的结转规则，按执行顺序排序
3. **执行结转**: 
   - 遍历结转规则
   - 查询源科目余额（支持通配符匹配，如 6* 匹配所有 6 开头的科目）
   - 计算结转金额
   - 生成结转凭证（借：源科目 贷：目标科目，或反向）
   - 执行标准入账流程
4. **结转后处理**: 
   - 检查所有规则执行结果
   - 全部成功则标记期末结转完成
   - 部分失败则触发告警，人工介入

#### 数据库设计
- **期末结转规则表**: `t_period_end_transfer_rule`
  - `rule_code`: 规则编码
  - `transfer_type`: 结转类型（1-损益结转, 2-成本结转, 3-自定义结转）
  - `source_subject_code`: 源科目编码（支持通配符）
  - `target_subject_code`: 目标科目编码
  - `transfer_direction`: 结转方向（1-借方余额结转到贷方, 2-贷方余额结转到借方）
  - `execute_order`: 执行顺序

- **期末结转记录表**: `t_period_end_transfer_record`
  - `transfer_no`: 结转流水号
  - `accounting_date`: 会计日期
  - `rule_code`: 规则编码
  - `voucher_no`: 生成的凭证号
  - `status`: 状态（1-处理中, 2-成功, 3-失败）

#### 产出物
- **流程图**: `docs/fixed/design/flowchat/period_end_transfer_flow.mmd`
- **SQL DDL**: `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
- **Steering 文件**: 待更新到 `03-architecture-requirements.md`

---

## 📋 待更新的文档

### 1. Steering 文件更新
- **文件**: `.kiro/steering/03-architecture-requirements.md`
- **内容**:
  - 补充子账户明细表用途说明
  - 补充记账自动开户流程
  - 补充期末结转流程
  - 更新状态枚举值说明

### 2. 流程图 README 更新
- **文件**: `docs/fixed/design/flowchat/README.md`
- **内容**:
  - 新增"记账自动开户流程图"说明
  - 新增"期末结转流程图"说明
  - 更新流程图关系图

### 3. 资源对齐文件更新
- **文件**: `.kiro/steering/02-resource-alignment.md`
- **内容**:
  - 新增流程图引用：`auto_account_opening_flow.mmd`, `period_end_transfer_flow.mmd`
  - 更新业务模型映射表（新增本地消息表、期末结转相关表）

---

## 🎯 SQL 脚本应用指南

### 应用顺序
1. 备份当前数据库
2. 执行 `docs/fixed/adjustment/4-schema-adjustment-20260301-v2.sql`
3. 验证表结构和索引
4. 更新业务代码中的枚举类定义

### 回滚方案
如需回滚，执行 `docs/fixed/adjustment/5-schema-rollback-20260301-v2.sql`

### 手动应用到完整 SQL 文件
参考 `docs/fixed/adjustment/apply_fixes_guide.md` 手动应用修复到 `1-init-schema-fixed-v2.sql`

---

## ✅ 验证清单

- [x] 本地消息表 DDL 已创建
- [x] 缓冲明细表会计日期字段已新增
- [x] 事务状态枚举已简化
- [x] 凭证状态枚举已统一
- [x] orig_voucher_no 已改为可空
- [x] 期末结转相关表已创建
- [x] 记账自动开户流程图已创建
- [x] 期末结转流程图已创建
- [ ] Steering 文件已更新（待完成）
- [ ] 流程图 README 已更新（待完成）
- [ ] 完整 SQL 文件已更新（待完成）

---

## 📝 后续工作

1. 更新 Steering 文件，补充新需求和修复说明
2. 更新流程图 README，补充新流程图说明
3. 手动应用修复到完整 SQL 文件
4. 更新枚举类定义（Java 代码）
5. 更新业务代码，支持记账自动开户和期末结转

---

**修复人**: Kiro AI  
**审核状态**: 待用户确认  
**下次更新**: 完成 Steering 文件和 README 更新
