# FIN-Core · 项目规范

> 本文件为项目级全局规范，适用于所有 AI 编程助手。
> 具体开发规范已下沉至各 Sub Agent 文件，按角色调用对应 Agent 即可。

---

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
