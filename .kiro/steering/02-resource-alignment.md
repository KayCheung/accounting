---
inclusion: always
---

# 资源与逻辑对齐 (Resource & Alignment)

本文件定义了物理资源（数据库表）与业务模型的映射关系，确保代码生成具备严格的底层支撑。

## 物理结构资源

### 数据库脚本 (docs/sql/)
- **`0-database-schema.sql`**：数据库初始化脚本，定义 `accounting` 库的字符集 (utf8mb4) 和排序规则
- **`1-init-schema.sql`**：核心业务表 DDL，包含会计科目、账户、子账户、记账规则、凭证、分录及缓冲记账等全量物理表定义，是 Entity 生成的唯一基准

### 业务逻辑资源 (docs/design/flowchart/ 和 docs/fixed/design/flowchart/)

#### 原始流程图（概览参考）
- **`docs/design/flowchart/system_architecture.mmd`**：系统架构图，描述"科目-模板-账户"的层级关系及总账/明细账的分层设计
- **`docs/design/flowchart/accounting_flow.mmd`**：入账流程图概览，定义从请求进入、规则解析、事务管理到余额更新和分录产生的动态时序
- **`docs/design/flowchart/end_of_day_process.mmd`**：日终核算流程图概览，定义平衡检查、总分核对及会计日期切换的逻辑

#### 详细流程图（开发实现依据）
- **`docs/fixed/design/flowchart/standard_posting_flow_detailed.mmd`**：标准入账全流程详细图，包含四阶段完整流程（记账流水、凭证生成、事务处理、过账更新）
- **`docs/fixed/design/flowchart/buffer_posting_modes.mmd`**：缓冲记账三种模式流程图，包含逐条缓冲、日间批量、日终批量及 Running Balance 计算
- **`docs/fixed/design/flowchart/reversal_flow.mmd`**：红冲流程图，包含前置校验、生成红冲凭证、执行红冲过账
- **`docs/fixed/design/flowchart/eod_five_phases.mmd`**：逻辑日切五阶段流程图，包含瞬间切日、存量清理、余额快照、试算平衡、归档
- **`docs/fixed/design/flowchart/freeze_unfreeze_flow.mmd`**：冻结/解冻流程图，包含冻结逻辑、解冻逻辑、超时自动解冻
- **`docs/fixed/design/flowchart/account_opening_flow.mmd`**：账户开户流程图，包含外部客户账户开户、内部账户开户、子账户自动创建
- **`docs/fixed/design/flowchart/transaction_rollback_flow.mmd`**：事务回滚流程图，包含回滚触发场景、回滚处理流程、回滚后数据状态、部分成功回滚、重试机制

## 核心对齐清单

### 物理表结构审计
在生成任何 Entity 或 Mapper 之前，必须确认：

1. **主键策略**：确认是 `auto_increment`、`snowflake` 还是其他分布式 ID 方案
2. **高精度字段**：所有 `amount`、`balance` 相关字段必须使用 `DECIMAL` 类型，Java 映射为 `BigDecimal`
3. **唯一约束识别**：深度扫描并记录关键幂等键：
   - `trace_no` + `trace_seq`：业务请求幂等
   - `voucher_no`：凭证唯一
   - `entry_id`：分录唯一
   - `subject_code`：科目唯一
   - `account_no`：账户唯一
   - `txn_no`：事务唯一

### 业务模型映射

核心五要素映射关系：

| 业务概念 | 物理表 | Java Entity | 说明 |
|---------|--------|-------------|------|
| 科目 (Subject) | `t_account_subject` | AccountSubject | 会计科目配置表，树形结构 |
| 账户 (Account) | `t_account` | Account | 总账/分户余额表 |
| 子账户 (SubAccount) | `t_sub_account` | SubAccount | 可用/冻结余额子账户 |
| 明细 (AccountDetail) | `t_account_detail` | AccountDetail | 分户明细账簿表 |
| 凭证 (Voucher) | `t_accounting_voucher` | AccountingVoucher | 记账凭证表 |
| 分录 (Entry) | `t_accounting_voucher_entry` | VoucherEntry | 凭证分录明细表 |

### 逻辑删除方案
- 统一使用 `is_delete` 字段（`BIGINT` 类型）
- 未删除：`is_delete = 0`
- 已删除：`is_delete = 删除时的时间戳`（确保唯一索引不冲突）
- 带唯一索引的表，索引字段必须包含 `is_delete`

## 关键约束确认

### 绝对值法则支撑
- 所有余额字段必须定义为 `DECIMAL(18,6) NOT NULL DEFAULT 0.00`
- 严禁使用负数，通过借贷方向字段 (`debit_credit`) 控制加减逻辑

### 红冲逻辑支撑
- 凭证表必须有 `trade_type` 字段区分：1-正常, 2-调账, 3-红字, 4-蓝字
- 分录表必须有 `debit_credit` 字段：1-借, 2-贷

### 并发控制支撑
- 账户表和子账户表必须有 `version` 字段（乐观锁）
- 关键业务表必须支持 `SELECT ... FOR UPDATE`（悲观锁）

## 潜在风险预警

在代码生成前，如发现以下问题必须提出：
1. DDL 中关键唯一约束缺少索引
2. 金额字段精度不足（小于 DECIMAL(18,6)）
3. 缺少 `version` 字段导致无法实现乐观锁
4. 业务流程图与 DDL 表结构存在逻辑冲突
