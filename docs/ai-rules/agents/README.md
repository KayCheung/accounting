# Sub Agent 使用说明

## 文件结构

```
docs/ai-rules/agents/     ← 通用版（Windsurf / Copilot / Kiro / Trae / Codex 等手动粘贴使用）
  ├── ba.md               @BA 需求分析
  ├── prototype.md        @Prototype 原型设计
  ├── java.md             @Java 后端开发
  ├── frontend.md         @Frontend 前端开发
  ├── test.md             @Test 测试
  └── tl.md               @TL Code Review + 进度管理

.claude/agents/           ← Claude Code 官方 Sub Agent 格式
  ├── ba.md
  ├── prototype.md
  ├── java.md
  ├── frontend.md
  ├── test.md
  └── tl.md

.cursor/rules/            ← Cursor Rules 格式（.mdc）
  ├── ba.mdc
  ├── prototype.mdc
  ├── java.mdc             globs: **/*.java（打开 Java 文件时自动生效）
  ├── frontend.mdc         globs: **/*.vue,**/*.ts
  ├── test.mdc             globs: **/*Test*.java,**/*.spec.ts
  └── tl.mdc
```

---

## Agent 协作链路

```
团队负责人分配任务
       ↓
   @BA 需求分析
   输出：需求文档
       ↓
  ┌────┴────┐
  ↓         ↓
@Prototype  @Java
页面规格    后端接口
  ↓         ↓
@Frontend  @Test ←─────────┐
前端页面    接口测试         │
  └────┬────┘              │
       ↓                   │
   @Test                   │
   UI 功能测试 ─────────────┘
       ↓
     @TL
  Code Review + 进度更新
```

---

## 各工具使用方式

### Claude Code（官方 Sub Agent）

直接在对话中 `@` 调用：

```bash
# 简单任务（人工传递产物）
@BA 请分析字典管理模块的业务需求，参考 docs/prompt/step-06-dict-subject.md

# 复杂任务（自动传递）
@BA 请分析字典管理需求，完成后自动启动后续链路，参考 docs/prompt/step-06-dict-subject.md
```

### Cursor

在 Settings → Rules 中启用对应 `.mdc` 文件，或在对话中直接 `@` 引用：

```
@java.mdc 请实现字典管理的分页查询接口
```

`java.mdc` / `frontend.mdc` / `test.mdc` 配置了 `globs`，打开对应类型文件时会自动生效。

### Windsurf / Copilot / Kiro / Trae / Codex 等

将 `docs/ai-rules/agents/` 下对应 Agent 文件的内容复制，粘贴到工具的系统提示词或 Rules 配置中。

---

## 传递方式

### 人工传递（简单任务）

1. 用 `@BA` 完成需求分析，复制输出的需求文档
2. 粘贴给 `@Prototype`：「基于以上需求文档，设计页面规格」
3. 粘贴给 `@Java`：「基于以上需求文档，实现后端接口」
4. 后端/前端完成后，粘贴给 `@Test`
5. 测试完成后，`@TL` 执行 Code Review

### 自动传递（复杂任务）

在发起任务时加上「完成后自动启动后续」：

```
@BA 请分析 [功能名] 需求，完成后自动启动后续链路
```

`@BA` 会在输出末尾附上「下游指令」，Claude Code 自动依次调用后续 Agent。

---

## 快速上手示例

```bash
# 第一步：需求分析
@BA 请分析字典管理模块的业务需求，参考 docs/prompt/step-06-dict-subject.md

# 第二步：原型设计（粘贴 @BA 输出）
@Prototype 请基于以上需求文档，设计字典管理页面规格

# 第三步：后端开发（粘贴 @BA 输出）
@Java 请实现字典管理的分页查询和 CRUD 接口，参考 docs/prompt/step-06-dict-subject.md

# 第四步：前端开发（粘贴 @Prototype 输出）
@Frontend 请实现字典管理页面，参考以上页面规格

# 第五步：测试
@Test 后端接口和前端页面均已完成，请编写测试用例

# 第六步：Code Review + 进度更新
@TL 请 Review 本次提交，diff 如下：
git diff main...feature/step-06
```
