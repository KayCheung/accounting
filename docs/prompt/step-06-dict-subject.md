# step-06-dict-subject · 字典与科目接口

## 资源声明

| 类型 | 文件 | 读取时机 |
|------|------|----------|
| 领域模型速查 | `docs/design/domain-model.md` | 必读（科目域、支撑域部分） |
| 科目域 DDL | `docs/sql/4-subject.sql` | Java-B / Java-C 实现时必读 |
| 支撑域 DDL | `docs/sql/6-infra.sql` | Java-A 实现字典接口时必读 |
| Java 编码规范 | `docs/ai-rules/java.md` | 每次生成前必读 |
| 账务领域规范 | `docs/ai-rules/accounting.md` | 科目约束规则必读 |
| Step 4 产出 | `DictionaryCacheService.java` | Java-A 字典接口直接调用 |
| Step 5 产出 | `AccountSubjectRepository / DictionaryRepository` | Java-B / Java-C / Java-A 直接调用 |

---

## 1. 任务目标（Mission）

实现字典管理（F-1）和会计科目管理（F-2）的完整后端接口，
包含字典二级缓存、科目树形查询、层级校验、末级联动校验等业务逻辑，
为配置管理前端页面提供稳定的接口支撑。

**任务分配**：

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-06-java-a.md` | 字典管理接口（F-1）含缓存 |
| Java-B | `docs/prompt/tasks/step-06-java-b.md` | 科目基础 CRUD 接口 |
| Java-C | `docs/prompt/tasks/step-06-java-c.md` | 科目树形查询 + 辅助核算项接口 |

> **依赖关系**：Java-A 和 Java-B 无互相依赖，可并行。
> Java-C 依赖 Java-B 的科目 Service 中的状态校验方法，建议 Java-B 优先完成核心方法后，Java-C 再开始。

---

## 2. 通用规范

### 2.1 接口层规范

- Controller 只做参数校验（`@Valid`）和格式转换，不含业务逻辑
- 接口路径前缀：`/accounting/config`
- 统一返回 `ApiResponse<T>`，分页返回 `ApiResponse<PageResponse<T>>`

### 2.2 会计科目业务约束（编码前必读）

```
科目编码规则：子科目编码必须以父科目编码为前缀（如父101，子101001）
末级限制：allow_post=1 且 is_leaf=1 才允许记账
停用联动：停用科目前必须校验无子科目、无关联账户模板、无启用记账规则引用
修改限制：已有账户关联的科目，禁止修改 subject_code / debit_credit / subject_category
辅助核算：末级科目的辅助核算项配置，在凭证生成时自动关联
```

### 2.3 Converter 规范

- 每个 Service 对应一个 Converter，负责 PO ↔ DTO 转换
- 不得在 Controller 或 Service 中直接操作 PO 字段

---

## 3. 完成标准（Checklist）

### Java-A
- [ ] 字典 CRUD 接口全部实现（创建 / 更新 / 停用 / 分页查询 / 按类型查询）
- [ ] `is_system=1` 的字典禁止删除，接口层拦截
- [ ] 字典变更后同步清除 Caffeine + Redis 二级缓存
- [ ] 手动刷新缓存接口实现（`POST /accounting/config/dict/cache/refresh`）
- [ ] 单测全通，含缓存命中和缓存清除场景

### Java-B
- [ ] 科目 CRUD 接口全部实现（创建 / 更新 / 停用 / 查询）
- [ ] 科目编码前缀校验实现（子科目编码必须以父科目编码开头）
- [ ] 停用联动校验实现（无子科目 / 无模板引用 / 无规则引用）
- [ ] 末级且 `allow_post=1` 时联动触发开户（预留接口，Step 8 实现）
- [ ] 单测全通，含停用联动校验场景

### Java-C
- [ ] 科目树形接口实现（懒加载 + 全量两种模式）
- [ ] 辅助核算项 CRUD 接口实现
- [ ] 辅助核算项必填校验：`required=1` 的项目不允许删除
- [ ] 单测全通，含三级树形结构正确性验证

### TL Review
- [ ] 接口路径符合规范，无多余层级
- [ ] 科目编码前缀校验逻辑正确（不能只做字符串 startsWith，需同时验证父科目存在）
- [ ] 缓存清除逻辑完整（Caffeine + Redis 两层都清除）
- [ ] 停用联动校验覆盖三个维度（子科目 / 模板 / 规则）

---

## 4. 下一步行动

进入 **Step 7 · Template & Rule API**，详见 `docs/prompt/step-07-template-rule.md`。
