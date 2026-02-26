# Step 2. Resource & Alignment (资源与逻辑对齐)

## 1. 任务目标 (Mission)
作为架构师，我需要在此阶段对项目已有的物理资源进行“逻辑审计”。通过解析 DDL 和设计文档，建立一张物理表到业务模型的映射全景图，确保后续 Step 生成的任何代码都具备严格的底层支撑。

## 2. 待审计资源清单 (Resource Inventory)

### 2.1 物理结构资源 (docs/sql/)
- **`0-database-schema.sql`**：数据库初始化脚本。定义了 `accounting` 库的字符集 (utf8mb4) 和排序规则，确立底层存储环境。
- **`1-init-schema.sql`**：核心业务表 DDL。包含了会计科目、账户、子账户、记账规则、凭证、分录及缓冲记账等全量物理表定义，是 Entity 生成的唯一基准。

### 2.2 业务逻辑资源 (docs/design/flowchart/)
- **`system_architecture.mmd`**：系统架构图。描述了“科目-模板-账户”的层级关系及总账/明细账的分层设计。
- **`accounting_flow.mmd`**：入账流程图。详细定义了从请求进入、规则解析、事务管理到余额更新和分录产生的动态时序。
- **`end_of_day_process.mmd`**：日终核算流程图。定义了平衡检查、总分核对及会计日期切换的逻辑，是日终任务的设计准则。

## 3. 核心对齐清单 (Alignment Checklist)
1. **物理表结构审计 (Database Schema)**：
    - 深度解析 `docs/sql/` 下的所有 `.sql` 文件。
    - **识别主键策略**：确认是 `snowflake`、`auto_increment` 还是其他分布式 ID 方案。
    - **识别高精度字段**：确认所有 `amount`、`balance` 相关字段是否统一使用 `DECIMAL(20, 6)` 或更高精度（基于 DDL 实际情况）。
    - **识别唯一约束**：深度扫描并记录 `trace_no`（请求幂等）、`voucher_no`（凭证唯一）、`entry_id`（分录唯一）、`subject_code`（科目唯一）、`account_no`（账户唯一）、`txn_no`（事务唯一）等关键幂等键的位置。

2. **业务模型映射 (Business Mapping)**：
    - **识别核心五要素映射**：
        - **科目 (Subject)** -> 映射至会计科目配置表（如 `t_account_subject`）。
        - **账户 (Account)** -> 映射至总账/分户余额表（如 `t_account`）。
        - **明细 (AccountDetail)** -> 映射至分户明细账簿表（如 `t_account_detail`）。
        - **凭证 (Voucher/Journal)** -> 映射至原始记账流水/凭证表（如 `t_accounting_voucher`）。
        - **分录 (Entry)** -> 确认是否有独立的分录明细表（用于支持一借多贷或多借多贷场景）。
    - **逻辑删除方案**：验证 `is_delete` 字段的触发逻辑（Long 类型时间戳方案）。

## 4. 输出产出物 (Deliverables)
执行本 Step 后，架构师必须输出：
- **资源扫描报告**：以表格形式列出解析到的所有表名、对应 Java 实体名及其核心业务属性（主键策略、关键索引）。
- **逻辑基准确认**：明确指出当前 DDL 是否足以支撑“绝对值法则”和“方向对调红冲”逻辑。
- **潜在风险预警**：若发现 DDL 针对上述核心唯一约束（如 `entry_id`）缺少索引、精度不足或与业务流程图冲突，需在此阶段提出。

## 5. 协作约定 (Cooperation)
- 在用户提供 `docs/sql` 和 `docs/design` 内容之前，本 Step 处于挂起状态。
- 一旦内容提供，架构师需立即进行全量解析，不得遗漏任何物理字段或逻辑约束。