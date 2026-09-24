# step-03-codegen · 持久层批量生成

## 资源声明

| 类型 | 文件 | 读取时机 |
|------|------|----------|
| 领域模型速查 | `docs/design/domain-model.md` | 每次生成前必读 |
| 账户域 DDL | `docs/sql/1-account.sql` | Java-A 生成时必读 |
| 凭证域 DDL | `docs/sql/2-voucher.sql` | Java-B 生成时必读 |
| 规则域 DDL | `docs/sql/3-rule.sql` | Java-B 生成时必读 |
| 科目域 DDL | `docs/sql/4-subject.sql` | Java-C 生成时必读 |
| 流水域 DDL | `docs/sql/5-journal.sql` | Java-C 生成时必读 |
| 支撑域 DDL | `docs/sql/6-infra.sql` | Java-C 生成时必读 |
| Java 编码规范 | `docs/ai-rules/java.md` | 每次生成前必读 |
| Step 2 产出 | `accounting-core/.../BaseEntity.java` | 所有 PO 继承此类 |

---

## 1. 任务目标（Mission）

基于六域 DDL，完整生成全部 27 张表的 PO / Mapper / XML 及枚举体系，
为 Phase 3 配置管理模块的开发提供可靠的持久层基础。

三位工程师按业务域并行执行，互不依赖，TL 统一 Review。

**任务分配**：

| 工程师 | 详细任务文件 | 域 | 表数 | 枚举数 |
|--------|------------|---|------|--------|
| Java-A | `docs/prompt/step-03-java-a.md` | 账户域 | 7 | 8 |
| Java-B | `docs/prompt/step-03-java-b.md` | 凭证域 + 规则域 | 9 | 9（复用 2） |
| Java-C | `docs/prompt/step-03-java-c.md` | 科目域 + 流水域 + 支撑域 | 11 | 12（复用 3） |

---

## 2. 通用生成规范（三人共同遵守）

### 2.1 PO 规范

- 所有 PO 必须继承 `BaseEntity`（含 `id` / `tenantId` / `isDelete` / `createTime` / `updateTime`）
- `BaseEntity` 已有字段，PO 中**不得重复定义**
- 布尔字段禁止 `is` 前缀：`is_leaf` → `leaf`、`is_system` → `system`
- `TINYINT` 状态字段必须映射为对应枚举，标注 `@EnumValue`
- `version` 字段必须标注 `@Version`
- `DECIMAL(18,6)` 字段必须映射为 `BigDecimal`
- 所有属性使用包装类，禁止基本数据类型
- 强制 `@Accessors(chain = true)`

存放路径：`accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/entity/`

### 2.2 Mapper 规范

- 继承 `BaseMapper<XxxPO>`
- XML 路径：`accounting-core/src/main/resources/mapper/`
- 接口路径：`accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/mapper/`

### 2.3 枚举规范

- 路径：`accounting-core/src/main/java/com/kltb/accounting/core/domain/enums/`
- 每个枚举值标注 `@EnumValue`（持久化）+ `@JsonValue`（序列化）

### 2.4 特殊说明

| 场景 | 处理方式 |
|------|----------|
| 乐观锁 | `version` 字段标注 `@Version` |
| 逻辑删除 | 已在 `BaseEntity` 中统一配置，PO 不重复 |
| 自增主键 | 已在 `BaseEntity` 中配置，PO 不重复 |
| 枚举复用 | Java-A 定义 `DebitCreditEnum` 等，Java-B/C 直接复用，不重复定义 |
| 无 tenant_id 的表 | `t_local_message` / `t_message_receipt` 不继承 `BaseEntity`，单独处理 |

---

## 3. 完成标准（Checklist）

### Java-A
- [ ] 账户域 7 个 PO 生成完毕，继承 `BaseEntity`，无重复字段
- [ ] 7 个 Mapper 接口和 XML 生成完毕
- [ ] 8 个枚举类生成完毕，含 `@EnumValue` + `@JsonValue`
- [ ] 含 `version` 字段的表（`t_account` / `t_sub_account` / `t_account_freeze_detail`）已标注 `@Version`

### Java-B
- [ ] 凭证域 + 规则域 9 个 PO 生成完毕，无重复字段
- [ ] 9 个 Mapper 接口和 XML 生成完毕
- [ ] 9 个枚举类生成完毕，未重复定义 `DebitCreditEnum` / `ChangeDirectionEnum`
- [ ] 含 `version` 字段的表（`t_accounting_voucher` / `t_accounting_voucher_entry` / `t_buffer_posting_detail`）已标注 `@Version`

### Java-C
- [ ] 科目域 + 流水域 + 支撑域 11 个 PO 生成完毕
- [ ] 11 个 Mapper 接口和 XML 生成完毕
- [ ] 12 个枚举类生成完毕，未重复定义复用枚举
- [ ] 含 `version` 字段的表（`t_business_record` / `t_transaction`）已标注 `@Version`
- [ ] `LocalMessagePO` / `MessageReceiptPO` 未继承 `BaseEntity`，已单独处理

### TL Review
- [ ] 全部 27 张表 PO 数量与 DDL 一致
- [ ] 跨人复用枚举无重复定义，引用路径正确
- [ ] 所有 `TINYINT` 状态字段均有枚举映射
- [ ] `@Version` 覆盖全部含 `version` 字段的 9 张表
- [ ] 无布尔字段 `is` 前缀，无基本数据类型

---

## 4. 下一步行动

进入 **Step 4 · Middleware Integration**，详见 `docs/prompt/step-04-middleware.md`。
