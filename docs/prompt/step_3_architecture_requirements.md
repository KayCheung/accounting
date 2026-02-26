# Step 3. Architecture & Requirements (业务架构与需求对齐)

## 1. 任务目标 (Mission)
本阶段旨在将物理表结构（Step 2）与动态业务流（流程图）进行深度融合。我将在此确认业务逻辑在代码层面的流向，定义核心模型职责边界，明确包含配置管理、实时/异步入账、单边记账及冻结扣款在内的全量功能清单。

## 2. 核心架构对齐 (Architecture Alignment)

### 2.1 会计科目与账户关系
- **科目驱动开户**：`t_account_subject` 定义会计准则。客户账户（`t_account`）及其子账户（`t_sub_account`）必须挂载在末级科目下。
- **辅助核算项 (Auxiliary)**：系统特有的核算维度。通过 `t_account_subject_auxiliary` 定义科目所需的扩展核算维度（如部门、项目、关联方），在凭证产生时自动关联 `t_accounting_voucher_auxiliary`，实现多维核算。

### 2.2 核心模型职责
- **开户规则 (`t_account_template`)**：定义开户规则，驱动系统自动初始化客户账户结构。
- **记账规则 (`t_accounting_rule`)**：系统的“大脑”，定义了业务事件（如：支付、退款）应如何生成借贷分录。
- **缓冲规则 (`t_buffer_posting_rule`)**：定义高并发场景下允许非实时异步汇总入账的业务类型。

### 2.3 核心模型层级
基于 `system_architecture.mmd`，我们需要在代码实现中遵循以下层次：
1. **账户层**：`Account` (总账) -> `SubAccount` (子账户/可用/冻结)。
    - 逻辑：实现账户余额的物理隔离（可用 vs 冻结），对齐 `t_account` 与 `t_sub_account`。
   - **同步更新**：除纯冻结/解冻外，涉及余额变动的业务必须同时更新总账与子账。
2. **凭证分录层**：`AccountingVoucher` (凭证) -> `VoucherEntry` (借贷分录) -> `Auxiliary` (辅助核算明细)。
    - 逻辑：由记账规则驱动，凭证必须满足借贷平衡。**分录**记录凭证拆分后的借贷明细，对齐 `t_accounting_voucher` 和 `t_accounting_voucher_entry`。
    - **先证后账**：凭证在入库或进入缓冲池前必须已完成借贷平衡校验并持久化。
3. **明细账簿层**：`AccountDetail` (余额变动明细)。
    - **逻辑**：记录单个账户资金变动的轨迹，包含变动前后的余额快照（Pre/Post Balance），用于日终核对与审计对账，对齐 `t_account_detail`。
    - **注意**：`t_accounting_voucher_entry` 关注交易平衡，`t_account_detail` 关注账户变动轨迹（含 Pre/Post 余额）。

## 3. 功能列表清单 (Functional Feature List)

### 3.1 配置管理模块 (Configuration - CRUD)
- **F-1 字典管理**：系统基础参数、业务类型、币种等字典维护。
- **F-2 会计科目管理**：树形科目维护，包含科目性质（借贷方向）、是否末级、是否允许过账、辅助核算项配置。
- **F-3 记账规则管理**：定义业务事件（Business Type）与会计分录、辅助核算项的映射模板。
- **F-4 开户模板管理**：定义不同客户类型在对应的业务场景默认开启的账户组合及所属科目。
- **F-5 缓冲记账规则**：配置哪些高并发业务走异步汇总入账，定义汇总周期与维度。

### 3.2 记账引擎模块 (Posting Engine)
- **F-6 规则解析引擎**：实时解析业务请求，匹配 `t_accounting_rule`、`t_accounting_rule_detail` 和 `t_accounting_rule_auxiliary` 配置，基于 `extend_script` 规则引擎计算分录。
- **F-7 凭证生成组件**：负责凭证号生成、借贷平衡校验（ΣDebit == ΣCredit）。
- **F-8 实时同步入账**：支持悲观锁模式下的强一致性过账处理。
- **F-9 异步准实时入账 (MQ)**：利用本地消息表（`t_local_message`）确保业务流水与会计凭证的最终一致性。
- **F-10 缓冲汇总入账**：针对海量高频小额交易，通过 `t_buffer_posting_detail` 由 Job 汇总入账。
- **F-11 冲账与红冲**：支持基于原凭证号的自动对调红冲，保留业务追溯链。
- **F-12 单边记账场景**：支持受限场景下的单边调账，记录特种传票流水。

### 3.3 账户与冻结 (Account & Freeze)
- **F-13 自动化开户**：基于模板自动初始化分户账及对应科目的挂载。
- **F-14 状态控制**：实现账户/子账户级别的冻结、止付、止收状态机管理。
- **F-15 资金冻结/解冻**：处理可用余额与冻结余额之间的物理划转与流水记录。
- **F-16 冻结扣款 (Pay from Freeze)**：直接在冻结额度上进行销账支取。
- **F-17 余额实时查询**：提供包含可用、冻结、明细在内的聚合查询接口。

### 3.4 核心核算模块 (Core Reconciliation)
- **F-18 借贷平衡校验**：全局性凭证借贷平衡核算。
- **F-19 总分核对**：实现总账科目余额 vs 分户余额合计，以及余额 vs 流水明细的勾稽核对。
- **F-20 会计日切 (EOD)**：会计日期变更及日终余额快照存档（基于 DDL 逻辑实现）。

## 4. 核心业务流逻辑确认 (Core Logic Flow)

### 4.1 标准入账全流程 (Standard Posting Sequence)
1. **阶段一：记账流水 (Journaling)**
    - 幂等校验：查询 `t_business_record`。若 `trace_no`和`trace_seq` 存在则幂等返回；
    - 持久化请求：记录至 `t_business_record` 和 `t_business_detail`。
2. **阶段二：凭证生成 (Vouchering)**
    - 规则匹配：
      - 根据 `business_type`、`trading_code`、`pay_channel` 等获取 `t_accounting_rule`。
      - 匹配分录模板（`funds_type`）与辅助核算模板，执行 `extend_script` 脚本计算。
    - 试算校验：
      - 计算借贷方向与金额，预填辅助核算项。
      - 强制执行校验：`ΣDebit == ΣCredit`。
3. **阶段三：事务处理 (Transaction Management)**
    - 在 `t_transaction` 记录开启 `PROCESSING` 状态事务，关联 `voucher_no`。
   - 开启编程式事务 `TransactionTemplate`
4. **阶段四：过账更新 (Posting)**
    - **锁控制**：按 `account_no` 或 `sub_account_no` 升序执行 `SELECT FOR UPDATE`。
    - **余额计算**：`newBalance = balance ± amount`。
    - **变动与记录**：
      - 更新 `t_account` 并记录 `t_account_detail` (含 Pre/Post 余额快照)。
      - 更新 `t_sub_account` 并记录 `t_sub_account_detail` (含 Pre/Post 余额快照)。
    - **事务收尾**：更新 `t_transaction` 为 `SUCCESS`，提交事务。

### 4.2 缓冲记账与锁升级 (Buffered & Lock Escalation)
- **规则预置**：凭证在入缓冲池前已生成。
- **缓冲写入**：业务侧触发入账 -> 匹配缓冲规则 -> 写入 `t_buffer_posting_detail` (Status: PENDING)。
- **触发**：逐条缓冲、日间批量、日终汇总。
- **入账执行**：
    - **Step 1**：乐观锁更新（基于 Version），失败后重试。
    - **Step 2**：若重试达阈值，回退并升级为悲观锁（FOR UPDATE）保证成功。
- **状态回填**：更新 `t_buffer_posting_detail` 为已入账。

### 4.3 逻辑日切流程 (Logical EOD)
1. **逻辑截断**：记录当前 `T` 日最后一个业务序列号，此后新请求标记为 `T+1`。
2. **存量清理**：强制触发所有标记为 `T` 日的缓冲记账进入“过账更新”阶段。
3. **平衡校验**：执行 `T` 日借贷平衡、总分平衡校验。
4. **快照与切日**：保存 `T` 日余额快照，更新全局会计日期至 `T+1`。

### 4.3 逻辑日切流程 (Logical EOD)
1. **瞬间切日**：系统将会计日期状态更新为 `T+1`。此时所有新请求均写入 `T+1` 会计日。
2. **存量清理**：强制扫描处理所有标记为 `T` 且状态为 `PENDING` 的缓冲记录或 `PROCESSING` 的事务。
3. **余额快照**：待 `T` 日存量处理完毕，保存当日所有账户余额快照。
4. **核对阶段**：执行 `T` 日借贷平衡校验及总分余额勾稽。
5. **归档**：彻底关闭 `T` 日账务，标记日终处理完成。

### 4.4 冻结与特殊场景
- **冻结逻辑**：可用子账户余额减，冻结子账户余额加。不强制产生外部凭证，但必须记录 `t_account_detail`。
- **单边记账**：跳过借贷平衡检查，直接驱动单向余额变更。

## 5. 产出物要求 (Deliverables)
执行本 Step 时，架构师必须输出：
- **核心业务域模型 (Domain Model) 定义说明**：明确 Account、Voucher、Entry、Detail 的聚合关系。
- **入账逻辑的时序伪代码 (Sequence Logic)**。
- **关键状态机说明**：（凭证状态、消息表状态、账户冻结状态）。

## 6. 状态同步说明
- 确认本 Step 逻辑与功能清单无误后，方可进入 Step 4 定义编码规范。