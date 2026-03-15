# .claude/agents · Sub Agent 说明

## Agent 清单

| Agent | 调用方式 | 职责 | 适用场景 |
|-------|----------|------|----------|
| TL | `@TL` | 代码 Review、架构决策、进度更新 | PR 合并前、架构讨论、进度同步 |
| Java | `@Java` | 后端业务代码开发 | Service / Repository / Controller 实现 |
| Frontend | `@Frontend` | Vue 3 页面开发 | 页面组件、接口对接 |
| Test | `@Test` | 测试用例编写 | 单测、接口测试、前端功能测试 |
| BA | `@BA` | 业务需求梳理 | 功能澄清、验收标准定义 |
| Prototype | `@Prototype` | 交互原型设计 | 页面结构、交互细节输出给前端 |

## 典型工作流

```
新功能开发
    @BA 梳理需求 → 输出需求文档
         ↓
    @Prototype 设计原型 → 输出页面规格
         ↓
    @Java 实现后端 + @Frontend 实现前端（并行）
         ↓
    @Test 编写测试
         ↓
    @TL Code Review → 通过后更新进度
```

## 使用示例

```
# 需求分析
@BA 请分析字典管理模块的业务需求，参考 docs/prompt/step-06-dict-subject.md

# 后端开发
@Java 请实现字典管理的分页查询接口，参考 docs/prompt/step-06-dict-subject.md

# 前端开发
@Frontend 请实现字典管理页面，参考原型文档和 Swagger 接口文档

# 测试
@Test 请为字典管理接口编写测试用例，覆盖正常路径和异常路径

# Code Review
@TL 请 Review 以下代码：
git diff main...feature/step-06

# 进度更新
@TL Step 6 所有 Checklist 已全部完成，请更新进度
```

## 中间产物传递规则

各 Agent 之间独立使用，人工传递中间产物：

```
@BA 输出 → 复制需求文档 → 粘贴给 @Prototype / @Java / @Frontend / @Test
@Prototype 输出 → 复制原型规格 → 粘贴给 @Frontend
@Java / @Frontend 输出代码 → 提交 PR → @TL Review
```
