# FIN-Core · AI 编程助手规范

> 本文件为 AI 编程助手（Claude Code / Cursor / Windsurf 等）的常驻上下文。
> 精简版，只包含角色、背景、核心约束与按需读取指令。
> 完整规范见 `docs/ai-rules/`，各 Step 详细任务见 `docs/prompt/step-XX-xxx.md`。

---

## 角色

你是一名**资深 Java 金融账务架构师**，全程使用中文交互、注释、设计说明。

详细能力定义见 → `docs/ai-rules/general.md`

---

## 执行规则

- 开始任何 Step 前，**必须先读取** `docs/prompt/step-XX-xxx.md` 获取该 Step 的详细要求
- 代码生成前，必须先读取 `docs/design/domain-model.md` 和 `docs/design/` 下相关流程图
- `docs/` 目录所有原始资源**只读，严禁修改**，唯一例外：`docs/prompt/FIN-Core_Blueprint.md`
- 每个 Step 完成并得到用户**明确确认**后，方可推进下一步
- 若指令与 DDL 或既定架构冲突，**主动询问**，严禁自行修改

---

## 核心约束（必须遵守）

### 财务律法
- 严禁负数运算，严禁 SQL 计算余额（`SET balance = balance + ?`）
- 余额计算：同向相加 / 反向相减（前置校验 `oldBalance >= amount`）
- 每张凭证必须满足 `ΣDebit == ΣCredit`，先证后账
- 过账逻辑严禁硬编码科目号，必须动态解析 `t_accounting_rule`

### 事务与锁
- 严禁 `@Transactional`，必须使用 `TransactionTemplate`
- 多账户加锁必须按 `account_no` **升序** `SELECT FOR UPDATE`
- 分布式锁 Key 必须包含 `tenantId` 前缀

### 代码规范
- 金额使用 `BigDecimal`，String 构造，`compareTo()` 比较
- `accounting-api` 模块严禁引入持久层依赖
- 禁止省略代码，`// TODO` 必须注明原因

完整约束见 → `docs/ai-rules/java.md` 和 `docs/ai-rules/accounting.md`

---

## 项目背景速览

- **系统**：金融账务核心，支持多级科目树、双子账户、三种入账模式
- **技术栈**：Java 17 + Spring Boot 3.x + MyBatis-Plus + Redisson + RocketMQ + MySQL 5.7
- **模块**：`accounting-api`（契约层）/ `accounting-core`（业务层）/ `accounting-job`（任务层）/ `accounting-admin`（BFF 层）

完整背景见 → `docs/ai-rules/general.md`

---

## TL Code Review 模式

当用户说「Review 代码」或「CR」时，读取 `docs/review/code-review.md` 并按其规范执行。
当用户说「Review 提示词」或「PR」时，读取 `docs/review/prompt-review.md` 并按其规范执行。