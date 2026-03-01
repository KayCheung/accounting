# SQL V2 修复应用报告 - Part 1

**日期**: 2026-03-01  
**文件**: `docs/fixed/sql/1-init-schema-fixed.sql`  
**状态**: ✅ 已完成

## 应用的修复内容

### 1. 简化事务表（✅ 已完成）

**修复内容**: 删除 4 个统计字段，简化事务状态枚举

**修改前**:
```sql
CREATE TABLE t_transaction (
    ...
    total_entry_count INT NOT NULL DEFAULT 0 COMMENT '本次事务总记账明细条数',
    success_entry_count INT NOT NULL DEFAULT 0 COMMENT '已成功记账的明细条数',
    pending_entry_count INT NOT NULL DEFAULT 0 COMMENT '处理中/未提交的明细条数',
    fail_entry_count INT NOT NULL DEFAULT 0 COMMENT '记账失败的明细条数',
    relate_account_count INT NOT NULL DEFAULT 0 COMMENT '本次事务涉及的账户总数',
    ...
    status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-未提交,2-部分提交,3-全部提交,4-部分回滚,5-全部回滚,6-失败',
    ...
);
```

**修改后**:
```sql
CREATE TABLE t_transaction (
    ...
    relate_account_count INT NOT NULL DEFAULT 0 COMMENT '本次事务涉及的账户总数',
    ...
    status TINYINT NOT NULL DEFAULT '1' COMMENT '事务状态：1-处理中(PROCESSING),2-成功(SUCCESS),3-失败(FAILED)',
    ...
);
```

**影响**:
- 删除了 4 个统计字段：`total_entry_count`, `success_entry_count`, `pending_entry_count`, `fail_entry_count`
- 简化了事务状态枚举：从 6 个状态简化为 3 个状态
- 保留了 `relate_account_count` 字段

---

### 2. 调整凭证状态枚举（✅ 已完成）

**修复内容**: 凭证状态从 4 个调整为 5 个

**修改前**:
```sql
status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销',
```

**修改后**:
```sql
status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败,5-已冲销',
```

**影响**:
- 新增了 `4-过账失败` 状态
- 原来的 `4-已冲销` 变更为 `5-已冲销`
- 避免了与分录状态的冲突

---

### 3. orig_voucher_no 改为可空（✅ 已完成）

**修复内容**: 原凭证号字段改为可空，正常凭证为 NULL

**修改前**:
```sql
orig_voucher_no VARCHAR(32) NOT NULL DEFAULT '' COMMENT '原凭证号(红冲/蓝补/调账时，记录被冲销的原凭证号或原纸质或电子凭证号)',
```

**修改后**:
```sql
orig_voucher_no VARCHAR(32) NULL DEFAULT NULL COMMENT '原凭证号（红冲凭证关联原凭证）',
```

**影响**:
- 正常凭证：`orig_voucher_no = NULL`
- 红冲凭证：`orig_voucher_no = 原凭证号`
- 更符合业务语义

---

## 未应用的修复（在 Part 2 中）

以下修复内容在 `2-init-schema-fixed.sql` 文件中：

1. ✅ **缓冲明细表新增会计日期字段** - 已包含在 Part 2 文件中
2. ✅ **本地消息表** - 已包含在 Part 2 文件中
3. ✅ **期末结转规则表和记录表** - 已添加到 Part 2 文件末尾

---

## 验证检查

### 1. 事务表验证
- [x] 删除了 4 个统计字段
- [x] 状态枚举简化为 3 个
- [x] 保留了 `relate_account_count` 字段
- [x] 索引定义正确

### 2. 凭证表验证
- [x] 状态枚举调整为 5 个
- [x] 新增了 `4-过账失败` 状态
- [x] `orig_voucher_no` 改为可空
- [x] 注释更新正确

---

## 总结

Part 1 文件（`1-init-schema-fixed.sql`）的 V2 修复已全部应用完成，包括：

1. ✅ 事务表简化（删除 4 个统计字段，简化状态枚举）
2. ✅ 凭证状态枚举调整（4 个 → 5 个）
3. ✅ orig_voucher_no 改为可空

所有修改都符合 V2 修复脚本（`4-schema-adjustment-20260301-v2.sql`）的要求。

---

**下一步**: 查看 Part 2 应用报告（`sql_v2_application_part2_20260301.md`）
