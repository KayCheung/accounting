# SQL V2 修复应用报告 - Part 2

**日期**: 2026-03-01  
**文件**: `docs/fixed/sql/2-init-schema-fixed.sql`  
**状态**: ✅ 已完成

## 应用的修复内容

### 1. 缓冲明细表会计日期字段（✅ 已包含）

**修复内容**: 缓冲明细表已包含 `accounting_date` 字段和优化的索引

**当前定义**:
```sql
CREATE TABLE t_buffer_posting_detail (
    ...
    accounting_date DATE NOT NULL COMMENT '会计日期',
    ...
    KEY idx_accounting_date_status (accounting_date, status, account_no),
    ...
);
```

**验证结果**:
- ✅ `accounting_date` 字段已存在
- ✅ 索引 `idx_accounting_date_status` 已正确配置
- ✅ 支持日切流程按会计日期扫描

---

### 2. 本地消息表（✅ 已包含）

**修复内容**: 本地消息表和消息回执表已完整定义

**当前定义**:
```sql
-- 本地消息表（用于可靠事件发布 / 事务性发件箱模式）
CREATE TABLE t_local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID',
    topic VARCHAR(32) NOT NULL COMMENT '消息主题/队列名',
    tag VARCHAR(32) NOT NULL DEFAULT '' COMMENT '消息标签（可选）',
    business_key VARCHAR(64) NOT NULL COMMENT '业务唯一键（如订单号、流水号），用于幂等与对账',
    payload TEXT NOT NULL DEFAULT '' COMMENT '消息体（JSON格式，支持结构化数据）',
    status TINYINT NOT NULL DEFAULT '1' COMMENT '消息状态：1-待发送,2-已发送,3-发送失败,4-已确认',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    send_time DATETIME NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '实际发送时间',
    ...
    UNIQUE KEY uk_message_id (message_id),
    UNIQUE KEY uk_business_key (business_key),
    KEY idx_create_time (create_time, topic, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（事务性发件箱）';

-- 消息回执表：记录下游消费者对本地消息的处理结果
CREATE TABLE t_message_receipt (
    ...
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息回执表（记录消费者处理结果）';
```

**验证结果**:
- ✅ `t_local_message` 表已完整定义
- ✅ `t_message_receipt` 表已完整定义
- ✅ 支持事务性发件箱模式
- ✅ 支持消息重试和幂等

---

### 3. 期末结转规则表和记录表（✅ 已添加）

**修复内容**: 新增期末结转规则表和记录表

**新增定义**:
```sql
-- 期末结转规则表
CREATE TABLE t_period_end_transfer_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
    rule_name VARCHAR(64) NOT NULL COMMENT '规则名称',
    transfer_type TINYINT NOT NULL COMMENT '结转类型：1-损益结转,2-成本结转,3-自定义结转',
    source_subject_code VARCHAR(32) NOT NULL COMMENT '源科目编码（支持通配符，如：6*表示所有6开头的科目）',
    target_subject_code VARCHAR(32) NOT NULL COMMENT '目标科目编码',
    transfer_direction TINYINT NOT NULL COMMENT '结转方向：1-借方余额结转到贷方,2-贷方余额结转到借方',
    summary_template VARCHAR(128) NOT NULL DEFAULT '' COMMENT '摘要模板（支持变量，如：{year}年{month}月损益结转）',
    execute_order INT NOT NULL DEFAULT 0 COMMENT '执行顺序（数字越小越先执行）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-启用,2-停用',
    ...
    UNIQUE KEY uk_rule_code (rule_code, is_delete),
    KEY idx_transfer_type (transfer_type, status),
    KEY idx_execute_order (execute_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转规则表';

-- 期末结转记录表
CREATE TABLE t_period_end_transfer_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    transfer_no VARCHAR(32) NOT NULL COMMENT '结转流水号',
    accounting_date DATE NOT NULL COMMENT '会计日期',
    transfer_type TINYINT NOT NULL COMMENT '结转类型：1-损益结转,2-成本结转,3-自定义结转',
    rule_code VARCHAR(32) NOT NULL COMMENT '规则编码',
    voucher_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '生成的凭证号',
    total_amount DECIMAL(18,6) NOT NULL DEFAULT 0.00 COMMENT '结转总金额',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1-处理中,2-成功,3-失败',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    execute_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
    finish_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '完成时间',
    ...
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_accounting_date (accounting_date, status),
    KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转记录表';
```

**验证结果**:
- ✅ `t_period_end_transfer_rule` 表已添加
- ✅ `t_period_end_transfer_record` 表已添加
- ✅ 支持损益结转、成本结转、自定义结转
- ✅ 支持通配符匹配科目（如 6* 匹配所有 6 开头的科目）
- ✅ 支持执行顺序控制
- ✅ 支持摘要模板（变量替换）

---

## 完整表清单

Part 2 文件包含以下表：

1. ✅ `t_account_detail` - 账户明细表
2. ✅ `t_sub_account_detail` - 子账户明细表
3. ✅ `t_account_freeze_detail` - 账户资金冻结明细表
4. ✅ `t_buffer_posting_rule` - 缓冲入账规则表
5. ✅ `t_buffer_posting_detail` - 缓冲记账明细表（含 accounting_date 字段）
6. ✅ `t_account_balance` - 账户日余额表
7. ✅ `t_account_balance_snapshot` - 账户余额快照表
8. ✅ `t_dictionary` - 字典表
9. ✅ `t_local_message` - 本地消息表（V2 新增）
10. ✅ `t_message_receipt` - 消息回执表（V2 新增）
11. ✅ `t_period_end_transfer_rule` - 期末结转规则表（V2 新增）
12. ✅ `t_period_end_transfer_record` - 期末结转记录表（V2 新增）

---

## 验证检查

### 1. 缓冲明细表验证
- [x] `accounting_date` 字段已存在
- [x] 索引 `idx_accounting_date_status` 已正确配置
- [x] 支持日切流程扫描

### 2. 本地消息表验证
- [x] `t_local_message` 表结构完整
- [x] `t_message_receipt` 表结构完整
- [x] 唯一索引正确（`uk_message_id`, `uk_business_key`）
- [x] 支持重试机制

### 3. 期末结转表验证
- [x] `t_period_end_transfer_rule` 表结构完整
- [x] `t_period_end_transfer_record` 表结构完整
- [x] 唯一索引正确（`uk_rule_code`, `uk_transfer_no`）
- [x] 支持执行顺序和状态管理

---

## 总结

Part 2 文件（`2-init-schema-fixed.sql`）的 V2 修复已全部应用完成，包括：

1. ✅ 缓冲明细表会计日期字段（已包含）
2. ✅ 本地消息表和消息回执表（已包含）
3. ✅ 期末结转规则表和记录表（已添加）

所有修改都符合 V2 修复脚本（`4-schema-adjustment-20260301-v2.sql`）的要求。

---

## 与 Steering 文件对齐

根据 `02-resource-alignment.md` 中的业务模型映射：

| 业务概念 | 物理表 | 状态 |
|---------|--------|------|
| 本地消息 (LocalMessage) | `t_local_message` | ✅ 已添加 |
| 期末结转规则 (PeriodEndTransferRule) | `t_period_end_transfer_rule` | ✅ 已添加 |
| 期末结转记录 (PeriodEndTransferRecord) | `t_period_end_transfer_record` | ✅ 已添加 |

所有新增表都已在 Steering 文件中定义，确保了资源与逻辑的对齐。

---

**完成状态**: ✅ 所有 V2 修复已应用完成
