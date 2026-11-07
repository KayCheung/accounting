# step-07-template-rule · 模板与规则接口

## 资源声明

| 类型 | 文件 | 读取时机 |
|------|------|----------|
| 领域模型速查 | `docs/design/domain-model.md` | 必读（科目域、规则域、凭证域部分） |
| 科目域 DDL | `docs/sql/4-subject.sql` | Java-A 实现开户模板接口时必读 |
| 规则域 DDL | `docs/sql/3-rule.sql` | Java-B / Java-C 实现时必读 |
| Java 编码规范 | `docs/ai-rules/java.md` | 每次生成前必读 |
| 账务领域规范 | `docs/ai-rules/accounting.md` | 规则约束、状态机必读 |
| Step 5 产出 | `SubjectRepository / AccountingRuleRepository / AccountingVoucherRepository / AccountRepository 等` | Java-A / Java-B / Java-C 直接调用 |
| Step 6 产出 | `SubjectController / DictController 等` | 参考接口风格与路径规范 |

---

## 1. 任务目标（Mission）

实现开户模板管理（F-3）、记账规则管理（F-4）与缓冲入账规则管理（F-5）的完整后端接口，
涵盖模板的自动开户校验、规则的科目联动校验、SpEL 脚本预加载校验、以及规则状态切换等业务逻辑，
为配置管理前端页面提供模板与规则的维护能力。

**任务分配**：

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-07-java-a.md` | 开户模板管理接口（F-3） |
| Java-B | `docs/prompt/tasks/step-07-java-b.md` | 记账规则管理接口（F-4）含明细与辅助核算项 |
| Java-C | `docs/prompt/tasks/step-07-java-c.md` | 缓冲入账规则管理接口（F-5） |

> **依赖关系**：Java-A 和 Java-B 无互相依赖，可并行。
> Java-C 可参考 Java-B 的唯一键校验模式，但时间区间重叠校验是独立的 SQL 逻辑，无直接代码依赖。

---

## 2. 通用规范

### 2.1 接口层规范

- Controller 只做参数校验（`@Valid`）和格式转换，不含业务逻辑
- 接口路径前缀：`/accounting/config`
- 统一返回 `ApiResponse<T>`，分页返回 `ApiResponse<PageResponse<T>>`

### 2.2 开户模板业务约束

```
关联科目必须为末级且 allow_open_account=1
auto_open=1 时系统自动开户（Step 8 实现引擎，本次只存状态）
同一 business_code + customer_type + subject_code 组合不可重复
停用联动：停用前必须校验无关联已开户账户
```

### 2.3 记账规则业务约束

```
业务线 + 交易编码 + 支付渠道组合唯一（uk_accounting_rule）
明细行必须借贷平衡（Σdebit_amount == Σcredit_amount）
同一规则内，同一科目 + 交易款项类型不可重复
辅助核算项按比例分摊时：前 N-1 条按比例计算，最后一条补差
规则启用（status=2）时预加载到内存缓存（预留接口，Step 9 实现）
规则停用（status=3）时检查无已关联凭证
```

### 2.4 缓冲入账规则业务约束

```
subject_code 与 account_no 必须有一个不为空
同一 business_code + trading_code + pay_channel 时间区间不得重叠
buffer_mode 决定入账模式（1-逐条 / 2-日间批量 / 3-日终批量）
生效时间 effective_time 不得晚于失效时间 expiration_time
```

### 2.5 Converter 规范

- 每个 Service 对应一个 Converter，负责 PO ↔ DTO 转换
- 不得在 Controller 或 Service 中直接操作 PO 字段

---

## 3. 完成标准（Checklist）

### Java-A
- [ ] 开户模板 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 关联科目末级 + allow_open_account=1 校验
- [ ] 唯一键组合校验（business_code + customer_type + subject_code）
- [ ] 停用联动校验（无关联已开户账户）
- [ ] 单测全通，含科目联动校验场景

### Java-B
- [ ] 记账规则 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 记账规则明细行借贷平衡校验
- [ ] 辅助核算项按比例分摊补差校验（前 N-1 按比例，最后一条补差）
- [ ] 规则停用时检查无关联凭证引用
- [ ] SpEL 脚本预加载校验（语法校验，非法脚本拒绝保存）
- [ ] 单测全通，含借贷平衡校验与分摊补差场景

### Java-C
- [ ] 缓冲入账规则 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询 / 分页查询）
- [ ] 时间区间不重叠校验（同一 business_code + trading_code + pay_channel）
- [ ] subject_code 与 account_no 至少一个不为空校验
- [ ] 生效时间不晚于失效时间校验
- [ ] 单测全通，含时间区间重叠校验场景

### TL Review
- [ ] 接口路径符合规范，无多余层级
- [ ] 记账规则借贷平衡校验逻辑正确（ΣDebit == ΣCredit）
- [ ] 缓冲规则时间区间重叠校验覆盖边界情况
- [ ] SpEL 脚本校验逻辑正确（使用 SpelExpressionParser 预解析）

---

## 4. 下一步行动

进入 **Step 8 · Account Auto-Opening**，详见 `docs/prompt/step-08-account-opening.md`。
