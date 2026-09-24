# FIN-Core · 开发进度锚点（Execution Blueprint）

> 本文件为项目唯一执行进度的事实来源，也是 `docs/` 目录下**唯一允许修改**的文件。
> 每完成一个 Step，将对应项标记为 `[X]`，并引导进入下一个 Step。

---

## Phase 1：契约、规范与架构

- [ ] **Step 1** · Governance & Constraints｜开发契约与规范
  → 详见 `docs/prompt/step-01-governance.md`

---

## Phase 2：工程基础

- [ ] **Step 2** · Project Initialization｜工程从零初始化
  → 详见 `docs/prompt/step-02-project-init.md`

- [ ] **Step 3** · Code Generation｜持久层批量生成
  → 详见 `docs/prompt/step-03-codegen.md`

- [ ] **Step 4** · Middleware Integration｜中间件集成
  → 详见 `docs/prompt/step-04-middleware.md`

- [ ] **Step 5** · Domain Alignment｜领域模型对齐
  → 详见 `docs/prompt/step-05-alignment.md`

---

## Phase 3：配置管理模块

- [ ] **Step 6** · Dict & Subject API｜字典与科目接口
  → 详见 `docs/prompt/step-06-dict-subject.md`

- [ ] **Step 7** · Template & Rule API｜模板与规则接口
  → 详见 `docs/prompt/step-07-template-rule.md`

---

## Phase 4：记账核心引擎（⚠️ 最高风险，严格串行）

- [ ] **Step 8** · Account Auto-Opening｜自动化开户
  → 详见 `docs/prompt/step-08-account-opening.md`

- [ ] **Step 9** · Journaling｜业务流水入库
  → 详见 `docs/prompt/step-09-journaling.md`

- [ ] **Step 10** · Vouchering｜凭证生成引擎
  → 详见 `docs/prompt/step-10-vouchering.md`

- [ ] **Step 11** · Transaction Management｜事务管理
  → 详见 `docs/prompt/step-11-transaction.md`

- [ ] **Step 12** · Posting Engine｜过账引擎
  → 详见 `docs/prompt/step-12-posting.md`

---

## Phase 5：账户与冻结模块

- [ ] **Step 13** · Account Status Control｜账户状态管理
  → 详见 `docs/prompt/step-13-account-status.md`

- [ ] **Step 14** · Freeze & Unfreeze｜资金冻结与解冻
  → 详见 `docs/prompt/step-14-freeze.md`

- [ ] **Step 15** · Balance Query API｜余额查询接口
  → 详见 `docs/prompt/step-15-balance-query.md`

---

## Phase 6：缓冲记账、日切与红冲

- [ ] **Step 16** · Buffer Posting｜缓冲记账
  → 详见 `docs/prompt/step-16-buffer-posting.md`

- [ ] **Step 17** · EOD & Trial Balance｜日切与试算平衡
  → 详见 `docs/prompt/step-17-eod.md`

- [ ] **Step 18** · Reversal & Red Offset｜冲账与红冲
  → 详见 `docs/prompt/step-18-reversal.md`

---

## Phase 7：MCP 接入

- [ ] **Step 19** · MCP Server Integration｜MCP 接入
  → 详见 `docs/prompt/step-19-mcp.md`

---

## Phase 8：存量数据迁移

- [ ] **Step 20** · Data Migration｜存量数据迁移
  → 详见 `docs/prompt/step-20-migration.md`

---

## Phase 9：前端集中交付

- [ ] **Step 21** · Frontend Infrastructure｜前端工程初始化
  → 详见 `docs/prompt/step-21-frontend-init.md`

- [ ] **Step 22** · Config Pages｜配置管理页面
  → 详见 `docs/prompt/step-22-config-pages.md`

- [ ] **Step 23** · Business Pages｜业务功能页面
  → 详见 `docs/prompt/step-23-business-pages.md`

---

## Phase 10：联调、性能验收与上线

- [ ] **Step 24** · Integration Testing｜全链路联调
  → 详见 `docs/prompt/step-24-integration.md`

- [ ] **Step 25** · Performance & Go Live｜性能验收与上线
  → 详见 `docs/prompt/step-25-golive.md`
