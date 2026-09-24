# 示例：正误对照表（Dos & Don'ts）

> 可作为 Code Review Checklist 直接使用。

---

## 财务计算类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| BigDecimal 初始化 | `new BigDecimal(100.0)` | `new BigDecimal("100.000000")` |
| BigDecimal 比较 | `amount.equals(other)` | `amount.compareTo(other) == 0` |
| 余额更新（SQL） | `SET balance = balance + ?` | Java 内存计算后 `SET balance = ?` |
| 余额减少 | 直接 `subtract`，无校验 | 先校验 `old.compareTo(amount) >= 0`，不足抛异常 |
| 负数冲正 | `amount.negate()` 写入账户 | 方向对调红冲（借贷方向互换，金额保持正数） |

---

## 事务与锁类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| 声明式事务 | `@Transactional` 注解 | `TransactionTemplate` 编程式事务 |
| 多账户加锁顺序 | 任意顺序 `SELECT FOR UPDATE` | 按 `account_no` **升序**批量 `SELECT FOR UPDATE` |
| 幂等控制 | 无锁直接查询再插入 | 分布式锁 + 唯一索引双重保障 |
| MQ 可靠性 | 直接 `producer.send()`，不做补偿 | 本地消息表 + Job 补偿重试 |
| 锁 Key 租户隔离 | 无 tenantId 前缀 | 自动拼接 `accounting:{tenantId}:` 前缀 |

---

## POJO / 实体类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| 布尔字段命名（PO） | `Boolean isLeaf` | `Boolean leaf`（无 `is` 前缀） |
| 基本数据类型 | `int status` | `Integer status`（包装类） |
| 状态魔法值 | `if (status == 1)` | `if (status == VoucherStatusEnum.PENDING)` |
| 链式调用 | 无 `@Accessors`，手动 setter | `@Accessors(chain = true)` + 链式 `.setXxx()` |

---

## 逻辑删除类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| 删除标记值 | `is_delete = 1` | `is_delete = System.currentTimeMillis()` |
| 带唯一索引的表 | 唯一索引不含 `is_delete` | 唯一索引包含 `is_delete` 字段 |

---

## 架构与模块类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| api 模块依赖 | `accounting-api` 含 mybatis-plus | 仅含 swagger / jackson / validation |
| 科目号 | `"1001"` 硬编码 | 动态解析 `t_accounting_rule` |
| 分层调用 | Controller 直调 Mapper | Controller → Application → Domain → Infrastructure |

---

## API 文档类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| Controller 无文档 | 裸 `@RestController` | 加 `@Tag(name=..., description=...)` |
| 方法无说明 | 裸 `@PostMapping` | 加 `@Operation(summary=...)` |
| DTO 字段无说明 | 裸字段声明 | 加 `@Schema(description=..., example=...)` |
| 分页返回 | 直接返回 `Page<PO>` | 封装为 `ApiResponse<PageResponse<XxxResponse>>` |

---

## 代码质量类

| 场景 | 错误写法 ❌ | 正确写法 ✅ |
|------|-----------|-----------|
| 省略实现 | `// ... 省略其余代码` | 完整实现 |
| TODO 无说明 | `// TODO` | `// TODO: [原因] [预期实现方向]` |
| 修改只读文件 | 修改 `docs/sql/` 或 `docs/design/` | 只读，仅可修改 `FIN-Core_Blueprint.md` |
