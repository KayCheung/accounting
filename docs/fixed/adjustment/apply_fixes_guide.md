# SQL 修复应用指南

**日期**: 2026-03-01  
**目标文件**: `docs/fixed/sql/1-init-schema-fixed-v2.sql`

---

## 📋 需要应用的修复

### 1. 在 t_transaction 表后新增本地消息表

**位置**: 在 `CREATE TABLE t_transaction` 之后

**插入内容**:
```sql
-- ========================================
-- 本地消息表（保证 MQ 消息发送的可靠性）
-- ========================================

CREATE TABLE IF NOT EXISTS t_local_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    message_id VARCHAR(64) NOT NULL COMMENT '消息ID（唯一标识）',
    topic VARCHAR(64) NOT NULL COMMENT 'MQ Topic',
    tag VARCHAR(32) NOT NULL DEFAULT '' COMMENT 'MQ Tag',
    message_key VARCHAR(64) NOT NULL COMMENT '消息Key（用于消息追踪）',
    message_body TEXT NOT NULL COMMENT '消息体（JSON格式）',
    business_type VARCHAR(32) NOT NULL COMMENT '业务类型（如：ASYNC_POSTING-异步过账）',
    business_id VARCHAR(64) NOT NULL COMMENT '业务ID（如：entry_id）',
    trace_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '系统跟踪号',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '消息状态：1-待发送,2-发送中,3-发送成功,4-发送失败',
    retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry_count INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    next_retry_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
    send_time TIMESTAMP NOT NULL DEFAULT '1970-01-01 00:00:00' COMMENT '发送成功时间',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '发送失败原因',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_message_id (message_id),
    KEY idx_business (business_type, business_id),
    KEY idx_status_retry (status, next_retry_time),
    KEY idx_trace_no (trace_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地消息表（保证MQ消息发送的可靠性）';
```

---

### 2. 修改 t_transaction 表定义

**查找**: `CREATE TABLE t_transaction`

**修改内容**:
1. 删除以下字段：
   - `total_entry_count`
   - `success_entry_count`
   - `pending_entry_count`
   - `fail_entry_count`

2. 修改 status 字段注释：
   ```sql
   status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-处理中(PROCESSING),2-成功(SUCCESS),3-失败(FAILED)',
   ```

---

### 3. 修改 t_accounting_voucher 表定义

**查找**: `CREATE TABLE t_accounting_voucher`

**修改内容**:
1. 修改 status 字段注释：
   ```sql
   status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败,5-已冲销',
   ```

2. 修改 orig_voucher_no 字段定义：
   ```sql
   orig_voucher_no VARCHAR(32) NULL DEFAULT NULL COMMENT '原凭证号（红冲凭证关联原凭证）',
   ```

---

### 4. 修改 t_buffer_posting_detail 表定义

**查找**: `CREATE TABLE t_buffer_posting_detail`

**修改内容**:
1. 在 `voucher_no` 字段后新增字段：
   ```sql
   accounting_date DATE NOT NULL COMMENT '会计日期',
   ```

2. 修改索引：
   ```sql
   -- 删除原索引
   -- KEY idx_status (status, account_no)
   
   -- 新增索引
   KEY idx_accounting_date_status (accounting_date, status, account_no)
   ```

---

### 5. 在文件末尾新增期末结转相关表

**位置**: 文件末尾

**插入内容**:
```sql
-- ========================================
-- 期末结转相关表
-- ========================================

-- 期末结转规则表
CREATE TABLE IF NOT EXISTS t_period_end_transfer_rule (
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
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人ID',
    create_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '创建人姓名',
    update_id VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人ID',
    update_name VARCHAR(32) NOT NULL DEFAULT '' COMMENT '更新人姓名',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_rule_code (rule_code, is_delete),
    KEY idx_transfer_type (transfer_type, status),
    KEY idx_execute_order (execute_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转规则表';

-- 期末结转记录表
CREATE TABLE IF NOT EXISTS t_period_end_transfer_record (
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
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识：0-未删除，不等于0为已删除',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_transfer_no (transfer_no),
    KEY idx_accounting_date (accounting_date, status),
    KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='期末结转记录表';
```

---

## ✅ 验证清单

应用修复后，请验证以下内容：

- [ ] t_local_message 表已创建
- [ ] t_transaction 表的 4 个字段已删除
- [ ] t_transaction.status 注释已更新
- [ ] t_accounting_voucher.status 注释已更新
- [ ] t_accounting_voucher.orig_voucher_no 已改为可空
- [ ] t_buffer_posting_detail.accounting_date 字段已新增
- [ ] t_buffer_posting_detail 索引已更新
- [ ] t_period_end_transfer_rule 表已创建
- [ ] t_period_end_transfer_record 表已创建

---

## 📝 注意事项

1. 修改前请备份原文件
2. 修改后请检查 SQL 语法是否正确
3. 建议使用 SQL 格式化工具格式化代码
4. 修改完成后更新 `02-resource-alignment.md` 中的表清单
