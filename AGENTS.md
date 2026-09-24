# AGENTS.md AI Agent 规范指南

## Sub Agent 调用入口

| 任务类型 | 调用 Agent | 说明 |
|----------|-----------|------|
| 业务需求梳理 | `@BA` | 输出需求文档，是协作链路起点 |
| 交互原型设计 | `@Prototype` | 输入：`@BA` 需求文档 |
| 后端开发 | `@Java` | 输入：`@BA` 需求文档 + Step 文件 |
| 前端开发 | `@Frontend` | 输入：`@Prototype` 页面规格 |
| 测试 | `@Test` | 输入：`@BA` 需求文档 + 后端/前端产物 |
| Code Review / 进度更新 | `@TL` | 输入：Git diff + 测试报告 |

Agent 详细规范见 `docs/ai-rules/agents/`，使用说明见 `docs/ai-rules/agents/README.md`。

---

## 全局执行规则

- 每个 Step 开始前必须读取 `docs/prompt/step-XX-xxx.md` 获取详细任务
- `docs/` 目录所有原始资源**只读，严禁修改**
- 唯一可写文件：`docs/prompt/FIN-Core_Blueprint.md`（由 `@TL` 维护）
- 每个 Step 完成并得到用户明确确认后，方可推进下一步

---

## 项目背景速览

- **系统**：金融账务核心，支持多级科目树、双子账户、三种入账模式
- **技术栈**：Java 17 + Spring Boot 3.x + MyBatis-Plus + Redisson + RocketMQ + MySQL 5.7
- **模块**：`accounting-api`（契约层）/ `accounting-core`（业务层）/ `accounting-job`（任务层）/ `accounting-admin`（BFF 层）
- **完整背景**：`docs/ai-rules/general.md`

---

## 核心约束速查（完整规范见各 Agent 文件）

```
财务律法：严禁负数运算 / SQL 计算余额 / 硬编码科目号
事务规范：严禁 @Transactional → 必须 TransactionTemplate
并发控制：多账户加锁必须 account_no 升序 / 锁 Key 含 tenantId
架构隔离：accounting-api 严禁引入持久层依赖
```

---

## Agent 职责范围

### @BA（Business Analyst）

**职责**：业务需求梳理与分析
- 解析业务需求，输出结构化需求文档
- 界定需求边界，识别依赖关系
- 输出可执行的开发规格说明

**输入**：业务需求描述、用户故事、变更请求
**输出**：需求文档（包含功能描述、验收标准、接口定义）

---

### @Prototype（交互原型设计）

**职责**：交互原型设计
- 设计用户界面交互流程
- 定义页面组件和交互逻辑
- 输出前端可用的页面规格说明

**输入**：`@BA` 需求文档
**输出**：页面规格（包含组件结构、状态管理、数据流）

---

### @Java（后端开发）

**职责**：后端代码开发
- 实现业务逻辑层代码
- 编写单元测试和集成测试
- 遵循 Java 编码规范和财务约束

**输入**：`@BA` 需求文档 + Step 文件
**输出**：Java 源码、测试代码、API 文档

**必须遵守**：
- 严禁 `@Transactional`，必须使用 `TransactionTemplate`
- 金额使用 BigDecimal，String 构造，compareTo() 比较
- 遵循绝对值法则，禁止负数运算和 SQL 计算余额
- 使用中文注释，中文异常提示

---

### @Frontend（前端开发）

**职责**：前端页面开发
- 实现管理后台页面
- 集成后端 API
- 实现状态管理和数据绑定

**输入**：`@Prototype` 页面规格
**输出**：前端代码（Vue 3 + Vite + Element Plus + Pinia）

**技术栈**：Vue 3、Vite、Element Plus、Pinia、axios

---

### @Test（测试）

**职责**：测试用例编写与执行
- 编写单元测试、集成测试、端到端测试
- 验证业务逻辑正确性
- 输出测试报告

**输入**：`@BA` 需求文档 + 后端/前端产物
**输出**：测试代码、测试报告

**测试覆盖**：
- 单元测试：Service 层、Domain 层核心逻辑
- 集成测试：完整记账链路（流水→凭证→过账）
- 性能测试：实时入账 P99 ≤ 500ms，余额查询 P99 ≤ 100ms

---

### @TL（Tech Lead）

**职责**：技术决策、Code Review、进度管理
- 架构设计与技术选型
- Code Review 和质量把关
- 维护 `FIN-Core_Blueprint.md`
- 协调各 Agent 协作流程

**输入**：Git diff + 测试报告
**输出**：Review 意见、架构决策、进度更新

**Review 重点**：
- 财务核心约束是否遵守（绝对值法则、先证后账、红冲原则）
- 并发控制和幂等设计是否正确
- 事务边界和异常处理是否合理
- 代码质量（注释、命名、结构）

---

## 协作流程

### 标准开发流程

1. **@BA** 接收业务需求 → 输出需求文档
2. **@Prototype** 基于需求文档 → 设计交互原型
3. **@Java** 基于需求文档 + Step 文件 → 开发后端代码
4. **@Frontend** 基于页面规格 → 开发前端页面
5. **@Test** 基于需求文档 + 代码产物 → 编写测试
6. **@TL** 执行 Code Review → 输出 Review 意见
7. 循环直到质量达标 → 推进下一步

### 关键检查点

- 每个 Step 开始前必须确认前一个 Step 已完成并通过验收
- 涉及财务核心约束的代码必须经过 **@TL** 严格 Review
- 高风险模块（凭证生成、过账引擎、日终核算）必须由 **@TL** 主攻
- Code Review 发现的 P0/P1 问题必须修复后才允许合并

---

## Vibe Coding 规范

### Prompt 四段式模板

```
任务：[一句话描述任务目标]

输入：
- [输入文件/数据源]
- [依赖的前置条件]

输出：
- [期望的产出物格式]
- [具体的验收标准]

不要做：
- [明确告知不需要做的事情]
```

### 会话拆分原则

- 每个会话专注单一任务，避免上下文混乱
- 复杂任务拆分为多个 Step，逐个完成
- 每次切换任务前先确认当前任务已完成

### 复述确认

AI 在执行任务前必须先复述任务理解，等待用户确认后再执行，避免理解偏差。

---

## 禁止行为

| Agent | 禁止行为 |
|-------|----------|
| 所有 Agent | 修改 `docs/` 目录只读文件（`FIN-Core_Blueprint.md` 除外） |
| @Java | 使用 `@Transactional`、硬编码科目号、SQL 计算余额 |
| @Frontend | 未与后端确认接口就直接开发 |
| @Test | 省略边界测试、异常场景测试 |
| @TL | 未完成验收就允许推进下一步 |
| 所有 Agent | 在不理解业务逻辑的情况下生成代码 |

---

## 参考文档

- **全局规范**：`docs/ai-rules/general.md`
- **Java 编码**：`docs/ai-rules/java.md`
- **账务领域**：`docs/ai-rules/accounting.md`
- **团队协作**：`TEAM.md`
- **项目规范**：`CLAUDE.md`
- **常用命令**：`CODEBUDDY.md`
