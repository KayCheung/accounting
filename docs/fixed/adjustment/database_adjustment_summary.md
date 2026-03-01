# 数据库调整总结

## 调整日期
2026-03-01

## 调整文件
- **调整脚本**：`docs/sql/2-schema-adjustment-20260301.sql`
- **回滚脚本**：`docs/sql/3-schema-rollback-20260301.sql`
- **详细说明**：`docs/design/database_adjustment_recommendations.md`

## 调整内容概览

### 1. 字段删除
| 表名 | 字段名 | 原因 |
|------|--------|------|
| t_accounting_rule | accounting_mode | 单边记账判断应在分录级别（is_unilateral），而非规则级别 |

### 2. 字段新增
| 表名 | 字段名 | 类型 | 说明 |
|------|--------|------|------|
| t_business_record | accounting_date | DATE NOT NULL | 会计日期，在记账接口调用时确定 |
| t_transaction | accounting_date | DATE NOT NULL | 会计日期，用于日切流程处理存量事务 |

### 3. 字段注释调整
| 表名 | 字段名 | 原注释 | 新注释 |
|------|--------|--------|--------|
| t_accounting_voucher | status | 凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败 | 凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销 |

### 4. 索引新增
| 表名 | 索引名 | 字段 | 说明 |
|------|--------|------|------|
| t_business_record | idx_accounting_date | (accounting_date, status) | 日切流程查询优化 |
| t_buffer_posting_detail | idx_accounting_date_status | (accounting_date, status, account_no) | 日切流程扫描缓冲明细优化 |
| t_transaction | idx_accounting_date_status | (accounting_date, status) | 日切流程扫描事务优化 |
| t_accounting_voucher | idx_orig_voucher_no | (orig_voucher_no) | 红冲查询优化 |

## 执行步骤

### 开发环境
```bash
# 1. 备份数据库
mysqldump -u root -p accounting > accounting_backup_20260301.sql

# 2. 执行调整脚本
mysql -u root -p accounting < docs/sql/2-schema-adjustment-20260301.sql

# 3. 验证调整结果
# 查看脚本输出的验证信息
```

### 测试环境
```bash
# 1. 备份数据库
mysqldump -u root -p accounting > accounting_backup_20260301.sql

# 2. 执行调整脚本
mysql -u root -p accounting < docs/sql/2-schema-adjustment-20260301.sql

# 3. 执行集成测试
# 验证业务功能正常
```

### 生产环境
```bash
# 1. 选择业务低峰期（建议凌晨 2:00-4:00）
# 2. 备份数据库
mysqldump -u root -p accounting > accounting_backup_20260301.sql

# 3. 执行调整脚本
mysql -u root -p accounting < docs/sql/2-schema-adjustment-20260301.sql

# 4. 验证调整结果
# 5. 监控系统运行情况
```

## 回滚方案

如果调整后发现问题，执行回滚脚本：

```bash
mysql -u root -p accounting < docs/sql/3-schema-rollback-20260301.sql
```

## 代码同步要求

数据库调整后，需要同步更新以下代码：

### 1. Entity 类调整
- **AccountingRule.java**：删除 `accountingMode` 字段
- **BusinessRecord.java**：新增 `accountingDate` 字段
- **Transaction.java**：新增 `accountingDate` 字段

### 2. 枚举类调整
- **VoucherStatus.java**：将枚举值 4 从"过账失败"改为"已冲销"

### 3. Mapper 调整
- **AccountingRuleMapper.xml**：删除 `accounting_mode` 字段的映射
- **BusinessRecordMapper.xml**：新增 `accounting_date` 字段的映射
- **TransactionMapper.xml**：新增 `accounting_date` 字段的映射

### 4. Service 调整
- **记账接口**：新增会计日期参数或自动获取当前会计日期
- **红冲逻辑**：更新原凭证状态为"已冲销"（status=4）
- **日切流程**：根据 accounting_date 处理存量数据

## 验证清单

- [ ] 开发环境调整完成
- [ ] 开发环境验证通过
- [ ] 测试环境调整完成
- [ ] 测试环境验证通过
- [ ] 代码同步完成
- [ ] 单元测试通过
- [ ] 集成测试通过
- [ ] 性能测试通过
- [ ] 生产环境调整完成
- [ ] 生产环境验证通过
- [ ] 生产环境监控正常
- [ ] 备份文件已保留

## 注意事项

1. **停机时间**：预计 30 分钟（包括备份、执行、验证）
2. **数据备份**：执行前必须备份数据库
3. **业务影响**：建议在业务低峰期执行
4. **监控告警**：执行后需要监控系统运行情况
5. **回滚准备**：准备好回滚脚本，如有问题立即回滚

## 相关文档

- [数据库设计调整建议](./database_adjustment_recommendations.md)
- [业务架构与需求对齐](./../prompt/step_3_architecture_requirements.md)
- [调整脚本](../sql/2-schema-adjustment-20260301.sql)
- [回滚脚本](../sql/3-schema-rollback-20260301.sql)
