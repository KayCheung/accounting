# 数据库设计调整建议

本文档基于业务逻辑澄清，提出数据库设计的调整建议。

## 调整日期
2026-03-01

## 调整原因
基于与用户的业务逻辑澄清（共识达成），需要对数据库设计进行以下调整以支持：
1. 单边记账逻辑（删除规则级别的 accounting_mode，统一使用分录级别的 is_unilateral）
2. 会计日期确定机制（在记账接口调用时确定）
3. 红冲逻辑（需要标记原凭证为"已冲销"状态）

## 必须调整的字段

### 1. 删除字段

#### t_accounting_rule.accounting_mode
- **当前定义**：`accounting_mode TINYINT NOT NULL DEFAULT '1' COMMENT '记账模式：1-实时,2-异步'`
- **调整建议**：删除此字段
- **调整原因**：
  - 单边记账的判断应该在分录级别，而不是规则级别
  - 一个记账规则可能包含多个分录，部分分录实时处理（客户账户），部分分录异步处理（内部账户）
  - 统一通过 `t_accounting_rule_detail.is_unilateral` 字段判断
- **影响范围**：
  - 需要修改记账规则配置界面
  - 需要修改记账引擎代码，改为遍历分录判断 is_unilateral
- **迁移方案**：
  ```sql
  -- 1. 备份数据
  CREATE TABLE t_accounting_rule_backup AS SELECT * FROM t_accounting_rule;
  
  -- 2. 删除字段
  ALTER TABLE t_accounting_rule DROP COLUMN accounting_mode;
  ```

### 2. 新增字段

#### t_business_record.accounting_date
- **当前状态**：不存在此字段
- **调整建议**：新增字段 `accounting_date DATE NOT NULL COMMENT '会计日期'`
- **调整原因**：
  - 会计日期在记账接口调用时确定，需要记录在业务记账流水表中
  - 后续所有操作（凭证生成、过账、缓冲记账）都使用这个会计日期
  - 日切流程需要根据会计日期处理存量数据
- **影响范围**：
  - 需要修改记账接口，增加会计日期参数或自动获取当前会计日期
  - 需要修改业务记账流水表的插入逻辑
- **迁移方案**：
  ```sql
  -- 1. 新增字段（允许为空，用于历史数据）
  ALTER TABLE t_business_record ADD COLUMN accounting_date DATE NULL COMMENT '会计日期';
  
  -- 2. 更新历史数据（根据 trade_time 推算会计日期）
  UPDATE t_business_record SET accounting_date = DATE(trade_time) WHERE accounting_date IS NULL;
  
  -- 3. 修改字段为 NOT NULL
  ALTER TABLE t_business_record MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';
  
  -- 4. 添加索引
  ALTER TABLE t_business_record ADD KEY idx_accounting_date (accounting_date, status);
  ```

### 3. 调整枚举值

#### t_accounting_voucher.status
- **当前定义**：`status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败'`
- **调整建议**：`status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销'`
- **调整原因**：
  - 红冲逻辑需要标记原凭证为"已冲销"状态
  - 原来的"过账失败"状态可以通过事务表的状态来体现，不需要在凭证表中单独标记
- **影响范围**：
  - 需要修改凭证状态枚举类
  - 需要修改红冲逻辑，更新原凭证状态为"已冲销"
  - 需要修改凭证查询界面，增加"已冲销"状态的展示
- **迁移方案**：
  ```sql
  -- 1. 备份数据
  CREATE TABLE t_accounting_voucher_backup AS SELECT * FROM t_accounting_voucher;
  
  -- 2. 更新注释
  ALTER TABLE t_accounting_voucher MODIFY COLUMN status TINYINT NOT NULL DEFAULT '1' 
    COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销';
  
  -- 3. 处理历史数据（如果有 status=4 的记录，需要根据业务逻辑决定如何处理）
  -- 建议：将原来的"过账失败"状态改为"未过账"，并在事务表中标记失败
  UPDATE t_accounting_voucher SET status = 1 WHERE status = 4;
  ```

## 建议调整的索引

### 1. 日切相关索引

#### t_buffer_posting_detail
- **当前索引**：
  ```sql
  KEY idx_trace_no (trace_no),
  KEY idx_accounting_date (accounting_date,account_no),
  KEY idx_create_time (create_time, status)
  ```
- **建议新增**：
  ```sql
  KEY idx_accounting_date_status (accounting_date, status, account_no)
  ```
- **调整原因**：
  - 日切流程需要扫描指定会计日期且状态为"待入账"的缓冲明细
  - 联合索引可以提高查询效率
- **迁移方案**：
  ```sql
  ALTER TABLE t_buffer_posting_detail ADD KEY idx_accounting_date_status (accounting_date, status, account_no);
  ```

#### t_transaction
- **当前索引**：
  ```sql
  UNIQUE KEY uk_txn_no (txn_no),
  KEY idx_trace_no (trace_no)
  ```
- **建议新增**：
  ```sql
  KEY idx_accounting_date_status (accounting_date, status)
  ```
- **调整原因**：
  - 日切流程需要扫描指定会计日期且状态为"处理中"的事务
  - 需要先在 t_transaction 表中新增 accounting_date 字段
- **前置条件**：需要先在 t_transaction 表中新增 accounting_date 字段
- **迁移方案**：
  ```sql
  -- 1. 新增 accounting_date 字段
  ALTER TABLE t_transaction ADD COLUMN accounting_date DATE NULL COMMENT '会计日期';
  
  -- 2. 更新历史数据（从 t_business_record 关联获取）
  UPDATE t_transaction t 
  INNER JOIN t_business_record b ON t.trace_no = b.trace_no 
  SET t.accounting_date = b.accounting_date 
  WHERE t.accounting_date IS NULL;
  
  -- 3. 修改字段为 NOT NULL
  ALTER TABLE t_transaction MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';
  
  -- 4. 添加索引
  ALTER TABLE t_transaction ADD KEY idx_accounting_date_status (accounting_date, status);
  ```

### 2. 红冲查询索引

#### t_accounting_voucher
- **当前索引**：
  ```sql
  UNIQUE KEY uk_voucher_no (voucher_no),
  UNIQUE KEY uk_trace_no (trace_no,trace_seq)
  ```
- **建议新增**：
  ```sql
  KEY idx_orig_voucher_no (orig_voucher_no)
  ```
- **调整原因**：
  - 红冲逻辑需要查询是否已存在红冲凭证（通过 orig_voucher_no 查询）
  - 需要查询原凭证的状态（通过 orig_voucher_no 查询）
- **迁移方案**：
  ```sql
  ALTER TABLE t_accounting_voucher ADD KEY idx_orig_voucher_no (orig_voucher_no);
  ```

## 完整迁移脚本

```sql
-- ========================================
-- 数据库设计调整迁移脚本
-- 执行日期：2026-03-01
-- 执行环境：开发环境 -> 测试环境 -> 生产环境
-- ========================================

-- 1. 备份关键表
CREATE TABLE t_accounting_rule_backup_20260301 AS SELECT * FROM t_accounting_rule;
CREATE TABLE t_business_record_backup_20260301 AS SELECT * FROM t_business_record;
CREATE TABLE t_accounting_voucher_backup_20260301 AS SELECT * FROM t_accounting_voucher;
CREATE TABLE t_transaction_backup_20260301 AS SELECT * FROM t_transaction;

-- 2. 删除 t_accounting_rule.accounting_mode 字段
ALTER TABLE t_accounting_rule DROP COLUMN accounting_mode;

-- 3. 新增 t_business_record.accounting_date 字段
ALTER TABLE t_business_record ADD COLUMN accounting_date DATE NULL COMMENT '会计日期';
UPDATE t_business_record SET accounting_date = DATE(trade_time) WHERE accounting_date IS NULL;
ALTER TABLE t_business_record MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';
ALTER TABLE t_business_record ADD KEY idx_accounting_date (accounting_date, status);

-- 4. 调整 t_accounting_voucher.status 枚举值
ALTER TABLE t_accounting_voucher MODIFY COLUMN status TINYINT NOT NULL DEFAULT '1' 
  COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销';
UPDATE t_accounting_voucher SET status = 1 WHERE status = 4;

-- 5. 新增 t_transaction.accounting_date 字段
ALTER TABLE t_transaction ADD COLUMN accounting_date DATE NULL COMMENT '会计日期';
UPDATE t_transaction t 
INNER JOIN t_business_record b ON t.trace_no = b.trace_no 
SET t.accounting_date = b.accounting_date 
WHERE t.accounting_date IS NULL;
ALTER TABLE t_transaction MODIFY COLUMN accounting_date DATE NOT NULL COMMENT '会计日期';

-- 6. 新增索引
ALTER TABLE t_buffer_posting_detail ADD KEY idx_accounting_date_status (accounting_date, status, account_no);
ALTER TABLE t_transaction ADD KEY idx_accounting_date_status (accounting_date, status);
ALTER TABLE t_accounting_voucher ADD KEY idx_orig_voucher_no (orig_voucher_no);

-- 7. 验证数据完整性
SELECT '验证 t_business_record.accounting_date' AS check_item, COUNT(*) AS total, 
       SUM(CASE WHEN accounting_date IS NULL THEN 1 ELSE 0 END) AS null_count
FROM t_business_record;

SELECT '验证 t_transaction.accounting_date' AS check_item, COUNT(*) AS total, 
       SUM(CASE WHEN accounting_date IS NULL THEN 1 ELSE 0 END) AS null_count
FROM t_transaction;

SELECT '验证 t_accounting_voucher.status' AS check_item, status, COUNT(*) AS count
FROM t_accounting_voucher
GROUP BY status;

-- 8. 清理备份表（可选，建议保留一段时间）
-- DROP TABLE t_accounting_rule_backup_20260301;
-- DROP TABLE t_business_record_backup_20260301;
-- DROP TABLE t_accounting_voucher_backup_20260301;
-- DROP TABLE t_transaction_backup_20260301;
```

## 回滚方案

如果迁移后发现问题，可以使用以下回滚脚本：

```sql
-- ========================================
-- 数据库设计调整回滚脚本
-- ========================================

-- 1. 恢复 t_accounting_rule 表
DROP TABLE t_accounting_rule;
CREATE TABLE t_accounting_rule AS SELECT * FROM t_accounting_rule_backup_20260301;

-- 2. 删除 t_business_record.accounting_date 字段
ALTER TABLE t_business_record DROP KEY idx_accounting_date;
ALTER TABLE t_business_record DROP COLUMN accounting_date;

-- 3. 恢复 t_accounting_voucher.status 枚举值
ALTER TABLE t_accounting_voucher MODIFY COLUMN status TINYINT NOT NULL DEFAULT '1' 
  COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败';

-- 4. 删除 t_transaction.accounting_date 字段
ALTER TABLE t_transaction DROP KEY idx_accounting_date_status;
ALTER TABLE t_transaction DROP COLUMN accounting_date;

-- 5. 删除新增的索引
ALTER TABLE t_buffer_posting_detail DROP KEY idx_accounting_date_status;
ALTER TABLE t_accounting_voucher DROP KEY idx_orig_voucher_no;
```

## 注意事项

1. **执行顺序**：必须先在开发环境测试，再在测试环境验证，最后在生产环境执行
2. **数据备份**：执行前必须备份关键表数据
3. **停机时间**：建议在业务低峰期执行，预计停机时间 30 分钟
4. **代码同步**：数据库调整后，需要同步更新代码：
   - 删除 AccountingRule 实体类的 accountingMode 字段
   - 新增 BusinessRecord 实体类的 accountingDate 字段
   - 新增 Transaction 实体类的 accountingDate 字段
   - 调整 VoucherStatus 枚举类，将 4 改为"已冲销"
5. **配置同步**：需要更新记账规则配置界面，删除"记账模式"配置项
6. **监控告警**：执行后需要监控系统运行情况，确保没有异常

## 验证清单

- [ ] 开发环境执行成功
- [ ] 测试环境执行成功
- [ ] 代码同步完成
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试通过
- [ ] 生产环境执行成功
- [ ] 生产环境监控正常
- [ ] 备份表已保留
