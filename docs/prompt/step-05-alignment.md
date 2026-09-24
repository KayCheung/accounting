# step-05-alignment · 领域模型对齐

## 资源声明

| 类型 | 文件 | 读取时机 |
|------|------|----------|
| 领域模型速查 | `docs/design/domain-model.md` | 必读，本 Step 核心输入 |
| 账户域 DDL | `docs/sql/1-account.sql` | Java-A 补充自定义 Mapper 时必读 |
| 凭证域 DDL | `docs/sql/2-voucher.sql` | Java-B 补充自定义 Mapper 时必读 |
| 规则域 DDL | `docs/sql/3-rule.sql` | Java-B 补充自定义 Mapper 时必读 |
| 科目域 DDL | `docs/sql/4-subject.sql` | Java-C 补充自定义 Mapper 时必读 |
| 支撑域 DDL | `docs/sql/6-infra.sql` | Java-C 补充自定义 Mapper 时必读 |
| Java 编码规范 | `docs/ai-rules/java.md` | 每次生成前必读 |
| Step 3 产出 | `accounting-core/.../mapper/` | 在已有 Mapper 基础上补充自定义方法 |

---

## 1. 任务目标（Mission）

在 Step 3 生成的基础 Mapper 之上，补充业务开发真正需要的自定义查询方法，
验证所有查询走索引，为 Phase 3 配置管理模块和后续记账引擎奠定持久层基础。

三位工程师按业务域并行执行，互不依赖，TL 统一做索引验证。

**任务分配**：

| 工程师 | 详细文件 | 负责域 |
|--------|---------|-------|
| Java-A | `docs/prompt/tasks/step-05-java-a.md` | 账户域自定义 Mapper |
| Java-B | `docs/prompt/tasks/step-05-java-b.md` | 凭证域 + 规则域自定义 Mapper |
| Java-C | `docs/prompt/tasks/step-05-java-c.md` | 科目域 + 字典域自定义 Mapper |

---

## 2. 通用规范

### 2.1 自定义 Mapper 编写规范

- 自定义方法定义在 Java 接口中，SQL 写在对应 XML 文件里
- `SELECT FOR UPDATE` 查询必须单独定义方法，不得复用普通查询方法
- 分页查询使用 MyBatis-Plus `Page<T>` 对象，不得手写 `LIMIT`
- 联查字段必须明确列出，禁止 `SELECT *`

### 2.2 Repository 封装规范

- 每个自定义 Mapper 方法必须在对应 Repository 中封装一层
- Repository 负责组装参数、处理空值，Mapper 只做纯 SQL 执行
- Repository 存放路径：`accounting-core/src/main/java/com/kltb/accounting/core/infrastructure/persistence/repository/`

### 2.3 索引验证规范（TL 执行）

- 所有自定义查询必须通过 `EXPLAIN` 验证，`type` 不得为 `ALL`（全表扫描）
- 验证通过后在 XML 注释中标注：`<!-- EXPLAIN verified: type=ref, key=idx_xxx -->`

---

## 3. 完成标准（Checklist）

### Java-A
- [ ] 账户域 6 个自定义 Mapper 方法实现完毕，含 XML SQL
- [ ] `selectForUpdate`、`selectWithSubAccounts` 方法验证可正常调用
- [ ] 对应 Repository 封装完毕
- [ ] 单测覆盖：`selectForUpdate` 返回数据正确；`selectByOwner` 返回含子账户结构

### Java-B
- [ ] 凭证域 5 个、规则域 4 个自定义 Mapper 方法实现完毕
- [ ] 缓冲扫描查询支持分片参数
- [ ] 对应 Repository 封装完毕
- [ ] 单测覆盖：凭证联查返回分录和辅助核算项；缓冲扫描分片结果互不重叠

### Java-C
- [ ] 科目域 3 个、字典域 2 个自定义 Mapper 方法实现完毕
- [ ] 科目树形查询支持递归加载
- [ ] 对应 Repository 封装完毕
- [ ] 单测覆盖：科目树查询层级正确；字典按类型分组查询结果正确

### TL Review
- [ ] 全部自定义查询 `EXPLAIN` 验证通过，无全表扫描
- [ ] XML 文件中已标注 `EXPLAIN` 验证结果注释

---

## 4. 下一步行动

进入 **Step 6 · Dict & Subject API**，详见 `docs/prompt/step-06-dict-subject.md`。
