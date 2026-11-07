# Contributing to FIN-Core

English | [简体中文](#简体中文)

---

## 简体中文

感谢您对 **FIN-Core 金融账务核心系统** 的关注与支持！作为一个高可靠、金融级双录（复式记账）核心开源项目，我们非常欢迎社区开发者提交 Issue 和 Pull Request (PR)。

为了保证金融系统的绝对严谨性、数据一致性与代码质量，在您贡献代码前，请务必仔细阅读以下规范。

---

### 一、核心财务律法与编码红线（必须严格遵守）

FIN-Core 处理底层资金核算与账簿记录，任何违反财务准则的代码都将直接导致 PR 被拒绝：

1. **绝对值法则（严禁负数运算）**：
   - 凭证分录金额、流水明细金额必须严格大于 0（`BigDecimal > 0`）。
   - 金额禁止使用负数进行冲抵或减少，借贷方向与增减变动完全由科目借贷属性（`balance_direction`）与分录方向决定。
2. **严禁在 SQL 中计算余额**：
   - 禁止出现 `SET balance = balance - #{amount}` 类似操作。
   - 余额必须由领域模型（`AccountBalanceCalculator`）显式计算，并通过数据库乐观锁（`version` 版本号）保障原子更新。
3. **事务编排红线**：
   - **严禁使用声明式 `@Transactional`**（防范大事务、长事务死锁、异步与动态代理失效）。
   - 统一使用 `TransactionTemplate` 显式控制事务边界，严格遵循“锁在事务外获取、事务在锁内提交”的原则。
4. **并发死锁防范**：
   - 涉及多账户同时加锁扣划的场景，加锁顺序必须按照 `account_no` 字典升序依次获取 Redisson 分布式锁。
   - 分布式锁 Key 必须包含租户标识：`account:fund:{tenantId}:{accountNo}`。
5. **金额精度与类型规范**：
   - 严禁使用 `double` 或 `float` 处理金额。
   - 所有金额强制使用 `BigDecimal`，必须通过 `new BigDecimal(String)` 或 `BigDecimal.valueOf()` 构造，比较必须使用 `compareTo() == 0`，严禁使用 `equals()`。
6. **先证后账与红冲法则**：
   - 任何余额变动必须由合法的会计凭证（`t_accounting_voucher`）及分录驱动，先做借贷平衡校验，后执行账户过账。
   - 严禁物理删除或直接修改已记账的凭证。调账必须采用标准红蓝字凭证冲销。
7. **架构隔离**：
   - `accounting-api` 模块严禁引入持久层（如 MyBatis-Plus）或中间件依赖，保持契约纯粹。

---

### 二、本地开发环境准备

- **JDK**：OpenJDK 17 或 21
- **构建工具**：Apache Maven 3.8+
- **Node.js**：Node.js 18+ (推荐 20.x) 及 npm / pnpm
- **容器环境**：Docker & Docker Compose (Docker Desktop 20.10+)

#### 快速启动基础设施
```bash
cd docker
# Windows:
start.bat
# Linux / macOS:
chmod +x start.sh && ./start.sh
```

---

### 三、分支与 Git Commit 规范

#### 1. 分支策略
- `main`：生产稳定分支，受保护。
- `feature/xxx`：新特性开发分支。
- `fix/xxx`：Bug 修复分支。

#### 2. Commit Message 规范
遵循约定式提交（Conventional Commits）：
```
<type>(<scope>): <subject>

<body>
```
- **Type 类型**：
  - `feat`: 新增特性或业务功能
  - `fix`: 修复 Bug 或逻辑缺陷
  - `docs`: 文档变更（如 README、注释）
  - `style`: 代码格式调整（不影响业务逻辑）
  - `refactor`: 重构优化代码
  - `perf`: 性能调优
  - `test`: 增加或修改单元测试/集成测试
  - `chore`: 构建过程或辅助工具变更
- **示例**：
  - `feat(posting): 增加缓冲入账自动分片合并支持`
  - `fix(freeze): 修复超时未解冻状态联动异常`

---

### 四、PR 提交与验收流程

1. **Fork 本仓库** 到您的个人 GitHub 账号。
2. **基于最新 `main` 分支拉取开发分支**：
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **完成代码编写与测试**：
   - 必须为新增业务逻辑编写单元测试（JUnit 5 + Mockito）。
   - 运行全量单元测试并确保 100% 通过：
     ```bash
     mvn clean test
     ```
   - 若涉及前端修改，确保类型检查与打包无误：
     ```bash
     cd accounting-ui
     npm run type-check
     npm run build
     ```
4. **提交代码并推送至个人 Fork 仓库**。
5. **在 GitHub 提交 Pull Request**：
   - 详细描述该 PR 解决的问题、设计思路、改动文件。
   - 附带单元测试运行结果截图或日志。
6. **响应 Code Review**：
   - 维护团队将在 1-3 个工作日内完成代码评审并给出反馈。

---

## English

Thank you for your interest in **FIN-Core (Smart Accounting Engine)**! As a high-reliability, financial-grade double-entry accounting core project, we welcome community contributions via Issues and Pull Requests (PR).

To ensure extreme financial rigor, data consistency, and code quality, please read the following guidelines before contributing.

### Key Financial Rules
1. **Absolute Value Law**: All monetary amounts in vouchers and journal entries must be strictly positive (`BigDecimal > 0`). No negative numbers for offsets.
2. **No Balance Calculations in SQL**: Never use `balance = balance - #{amount}`. Balances must be calculated in domain models and updated via optimistic locking (`version`).
3. **Transaction Control**: Strictly avoid `@Transactional`. Use `TransactionTemplate` with well-defined boundaries.
4. **Deadlock Prevention**: Lock accounts in ascending order of `account_no` when acquiring distributed locks (Redisson).
5. **BigDecimal Precision**: Never use `float` or `double`. Always construct via `new BigDecimal(String)` and compare using `compareTo()`.
6. **Voucher-First & Reversal Rule**: Postings must be driven by balanced vouchers. Never delete or update recorded vouchers; use reversal vouchers instead.
7. **Layer Isolation**: `accounting-api` must remain pure without persistence or heavy middleware dependencies.

---

### Questions & Discussions
Feel free to open an Issue or start a Discussion on GitHub for any architecture questions or proposal ideas.
