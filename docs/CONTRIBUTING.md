# 团队协作说明（CONTRIBUTING.md）

> 本文档定义 FIN-Core 项目的多人协作规范，包含角色分工、提示词写法、进度管理与 Step 文件维护规则。
> 所有团队成员在开始开发前必须阅读并遵守。

---

## 一、角色说明

| 角色 | 职责 |
|------|------|
| TL（Tech Lead） | 架构决策、高风险模块主攻、Code Review 把关、Blueprint 进度维护 |
| BE-A（后端工程师 A） | 账户侧、冻结、余额查询相关模块 |
| BE-B（后端工程师 B） | 凭证侧、规则、中间件、MQ 相关模块 |
| VC（Vibe Coder） | 前端页面、Prompt 管理、低风险代码生成 |

---

## 二、进度管理：两级结构

### 第一级：`docs/prompt/FIN-Core_Blueprint.md`（TL 专属维护）

- 记录 **Step 级别**的完成状态，粒度到 Step，不到子任务
- **只有 TL 有权修改**，执行人不得直接改动
- TL 确认该 Step 所有 Checklist 全部打完、Code Review 通过后，才在 Blueprint 标记 `[X]`

```markdown
- [X] Step 2 · Project Initialization   ← TL 确认后才标记
- [ ] Step 3 · Code Generation
```

### 第二级：`docs/prompt/step-XX-xxx.md`（执行人维护）

- 记录**子任务级别**的完成状态，是执行人的个人进度
- 执行人完成一条子任务，立即在对应 Step 文件的 Checklist 打 `[X]`
- TL 通过查看 Step 文件 Checklist 是否全绿来判断是否可以更新 Blueprint

---

## 三、Step 文件结构规范

每个 Step 文件统一使用以下结构，**不得随意增删章节**：

```markdown
# step-XX-xxx · 步骤名称

## 资源声明
（本 Step 需要读取的文件清单）

## 1. 任务目标（Mission）
（一段话说清楚本 Step 要做什么）

## 2. 详细子任务列表
（按负责人分组，含技术要点）

## 3. 完成标准（Checklist）
（按负责人分组，含 TL Review 项）

## 4. 下一步行动
（一句话，指向下一个 Step）
```

### Checklist 分组写法

并行任务（多人同时执行）的 Step，Checklist 必须按负责人分组，每人只关注自己的条目：

```markdown
## 3. 完成标准（Checklist）

### BE-A
- [ ] 账户侧 8 张表 PO/Mapper 生成完成
- [ ] 基础 CRUD 单测通过

### BE-B
- [ ] 凭证+规则侧 17 张表 PO/Mapper 生成完成
- [ ] 基础 CRUD 单测通过

### TL Review
- [ ] 分区表注解配置确认
- [ ] @Version / @TableLogic 注解确认
- [ ] 联合唯一索引映射确认
```

串行任务（单人执行）的 Step，Checklist 无需分组，直接列条目即可：

```markdown
## 3. 完成标准（Checklist）

- [ ] xxx 完成
- [ ] xxx 单测通过
```

---

## 四、AI 会话规范

### 4.1 会话基本原则

- **单会话单 Step**：每个 Step 独立开启新会话，避免上下文污染
- **单会话单角色**：每次会话只做自己负责的子任务，不跨越边界
- **上下文超限信号**：当 AI 开始遗忘早期约束或输出矛盾内容时，立即开新会话并重新注入规范

### 4.2 每次会话的注入结构

开启新会话时，按以下顺序注入内容：

```
1. CLAUDE.md 全文（或 docs/ai-rules/all-in-one.md）
2. 当前执行人声明
3. 当前任务四段式 Prompt
```

### 4.3 执行人声明模板

```markdown
## 当前执行人
我是 [角色]，负责 [负责模块]。
本次任务：Step [N] · [Step 名称]
详细任务见：docs/prompt/step-[N]-xxx.md
```

示例：

```markdown
## 当前执行人
我是 BE-A，负责账户侧开发。
本次任务：Step 8 · Account Auto-Opening（自动化开户）
详细任务见：docs/prompt/step-08-account-opening.md
```

### 4.4 四段式 Prompt 模板

```markdown
【任务】
[动词开头，一句话说清楚做什么]

【输入】
- 领域模型：docs/design/domain-model.md（[相关域]部分）
- 完整 DDL：docs/sql/[N]-xxx.sql（生成 PO/Mapper 时必读，其他时候读摘要即可）
- Step 详情：docs/prompt/step-[N]-xxx.md

【输出】
[期望的产出物形式：完整 Java 文件 / 伪代码 / 设计说明 / 单测]

【不要做】
[本次任务的边界限制，防止 AI 发散]
```

示例：

```markdown
【任务】
实现自动化开户逻辑，包含账户存在性检查、模板匹配开户、并发幂等处理

【输入】
- 领域模型：docs/design/domain-model.md（账户域、科目域部分）
- 完整 DDL：docs/sql/1-account.sql、docs/sql/4-subject.sql
- Step 详情：docs/prompt/step-08-account-opening.md

【输出】
完整 Java 实现文件，含：
- AccountOpeningService 及其实现
- 相关 Repository 调用
- 单元测试（含 100 并发幂等场景）

【不要做】
- 不要生成 Controller 层
- 不要修改 DDL 文件
- 不要实现 Step 9 的流水入库逻辑
```

### 4.5 复述确认技巧

发起复杂任务前，先让 AI 复述理解后再执行：

```
请先复述你的执行计划（包含将要创建哪些文件、核心逻辑思路），
确认无误后再开始生成代码。
```

---

## 五、进度更新流程

### 5.1 标准流程

```
执行人完成子任务
      ↓
在 step-XX.md 对应 Checklist 条目打 [X]
      ↓
所有子任务完成后，通知 TL
      ↓
TL Code Review + 确认 Checklist 全绿
      ↓
TL 在 FIN-Core_Blueprint.md 标记该 Step [X]
```

### 5.2 并行任务流程（多人同时执行同一 Step）

```
BE-A 完成自己的子任务，打 [X]
BE-B 完成自己的子任务，打 [X]
            ↓
双方均完成后，通知 TL
            ↓
TL 执行 Code Review
TL 完成 Review 条目，打 [X]
            ↓
TL 在 Blueprint 标记该 Step [X]
```

### 5.3 权限边界

| 操作 | 执行人 | TL |
|------|--------|----|
| 修改 `step-XX.md` Checklist | ✅ | ✅ |
| 修改 `FIN-Core_Blueprint.md` | ❌ | ✅ |
| 修改 `docs/ai-rules/` 规则文件 | ❌ | ✅ |
| 修改 `docs/sql/` DDL 文件 | ❌ | ✅（需评审） |
| 修改 `docs/design/domain-model.md` | ❌ | ✅ |

---

## 六、常见问题

**Q：子任务完成了但 AI 输出质量不好，要重新生成，Checklist 怎么处理？**
重新生成完成后再打 `[X]`，未确认质量前不打勾。

**Q：发现 Step 文件里的子任务描述有问题怎么办？**
不要自行修改，反馈给 TL，由 TL 统一修正后再继续执行。

**Q：自己的子任务依赖另一个人的产出，但对方还没完成怎么办？**
在自己的会话里先做不依赖对方的部分，依赖项挂起等待，不要猜测对方的实现自行填充。

**Q：AI 在会话中开始输出与规范矛盾的内容怎么办？**
立即停止，开新会话，重新注入 `CLAUDE.md` + 执行人声明 + 四段式 Prompt。
