# docs/design · 占位说明

> 本目录存放业务架构图与流程图，为**只读参考资源**，严禁 AI 修改。

## 文件清单

| 文件 | 说明 |
|------|------|
| `flowchart/system_architecture.mmd` | 系统架构图：科目-模板-账户层级关系，总账/明细账分层设计 |
| `flowchart/accounting_flow.mmd` | 入账流程图：请求 → 规则解析 → 余额更新全链路时序 |
| `flowchart/end_of_day_process.mmd` | 日终流程图：EOD 平衡检查、总分核对、会计日期切换 |
| `project_structure.md` | 工程结构说明：模块划分与包路径规范 |
| `standard_posting_flow_detailed.mmd` | 标准入账全流程详细图，包含四阶段完整流程（记账流水、凭证生成、事务处理、过账更新） |
| `buffer_posting_modes.mmd` | 缓冲记账三种模式流程图，包含逐条缓冲、日间批量、日终批量及 Running Balance 计算 |
| `reversal_flow.mmd` | 红冲流程图，包含前置校验、生成红冲凭证、执行红冲过账 |
| `eod_five_phases.mmd` | 逻辑日切五阶段流程图，包含瞬间切日、存量清理、余额快照、试算平衡、归档 |
| `freeze_unfreeze_flow.mmd` | 冻结/解冻流程图，包含冻结逻辑、解冻逻辑、超时自动解冻 |
| `account_opening_flow.mmd` | 账户开户流程图，包含外部客户账户开户、内部账户开户、子账户自动创建 |
| `transaction_rollback_flow.mmd` | 事务回滚流程图，包含回滚触发场景、回滚处理流程、回滚后数据状态、部分成功回滚、重试机制 |
| `auto_account_opening_flow.mmd` | 记账自动开户流程图，包含账户存在性检查、匹配开户模板、执行自动开户 |
| `period_end_transfer_flow.mmd` | 期末结转流程图，包含前置校验、加载结转规则、执行结转、结转后处理 |

> 请将你项目中的实际设计文档放置于此目录。
