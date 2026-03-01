---
inclusion: always
---

# 业务架构与需求对齐 (Architecture & Requirements)

本文件定义了核心业务架构、领域模型职责边界和完整的功能清单。

## 核心架构对齐

### 会计科目与账户关系
- **科目驱动开户**：`t_account_subject` 定义会计准则，客户账户（`t_account`）及其子账户（`t_sub_account`）必须挂载在末级科目下
- **辅助核算项**：通过 `t_account_subject_auxiliary` 定义科目所需的扩展核算维度（如部门、项目、关联方），在凭证产生时自动关联 `t_accounting_voucher_auxiliary`，实现多维核算

### 核心模型职责
- **开户规则 (`t_account_template`)**：定义开户规则，驱动系统自动初始化客户账户结构
- **记账规则 (`t_accounting_rule`)**：系统的"大脑"，定义业务事件（如支付、退款）应如何生成借贷分录
- **缓冲规则 (`t_buffer_posting_rule`)**：定义高并发场景下允许非实时异步汇总入账的业务类型

### 单边记账定义
- **单边记账**：指部分账户实时更新余额，部分账户异步更新余额的记账模式
- **应用场景**：如用户充值时，客户账户需要实时更新（保证用户体验），但内部清算账户可以异步更新（不影响业务）
- **实现机制**：通过 `t_accounting_rule_detail.is_unilateral` 字段标识
  - `is_unilateral=1`：该分录实时处理，直接更新账户余额
  - `is_unilateral=0`：该分录发送 MQ 异步处理
- **注意事项**：删除 `t_accounting_rule.accounting_mode` 字段，统一通过分录级别的 `is_unilateral` 判断

### 核心模型层级

基于系统架构图，代码实现必须遵循以下层次：

#### 1. 账户层
- **结构**：`Account` (总账) → `SubAccount` (子账户：可用/冻结)
- **逻辑**：实现账户余额的物理隔离（可用 vs 冻结）
- **余额关系**：
  - 冻结场景：主账户余额不变，可用子账户余额减少，冻结子账户余额增加
  - 充值场景：主账户余额和可用子账户余额都增加，冻结子账户余额不变
  - 查询可用余额：直接查询可用子账户
- **同步更新**：除纯冻结/解冻外，涉及余额变动的业务必须同时更新总账与子账户

#### 2. 凭证分录层
- **结构**：`AccountingVoucher` (凭证) → `VoucherEntry` (借贷分录) → `Auxiliary` (辅助核算明细)
- **逻辑**：由记账规则驱动，凭证必须满足借贷平衡
- **先证后账**：凭证在入库或进入缓冲池前必须已完成借贷平衡校验并持久化

#### 3. 明细账簿层
- **结构**：`AccountDetail` (余额变动明细)
- **逻辑**：记录单个账户资金变动的轨迹，包含变动前后的余额快照（Pre/Post Balance），用于日终核对与审计对账
- **区别**：`t_accounting_voucher_entry` 关注交易平衡，`t_account_detail` 关注账户变动轨迹

## 功能清单

### 配置管理模块 (Configuration - CRUD)
- **F-1 字典管理**：系统基础参数、业务类型、币种等字典维护
- **F-2 会计科目管理**：树形科目维护，包含科目性质（借贷方向）、是否末级、是否允许过账、辅助核算项配置
- **F-3 记账规则管理**：定义业务事件与会计分录、辅助核算项的映射模板
- **F-4 开户模板管理**：定义不同客户类型在对应业务场景默认开启的账户组合及所属科目
- **F-5 缓冲记账规则**：配置哪些高并发业务走异步汇总入账，定义汇总周期与维度

### 记账引擎模块 (Posting Engine)
- **F-6 规则解析引擎**：实时解析业务请求，匹配记账规则配置，基于 SpEL 脚本计算分录
- **F-7 凭证生成组件**：负责凭证号生成、借贷平衡校验（ΣDebit == ΣCredit）
- **F-8 实时同步入账**：支持悲观锁模式下的强一致性过账处理
- **F-9 异步准实时入账 (MQ)**：利用本地消息表确保业务流水与会计凭证的最终一致性
- **F-10 缓冲汇总入账**：针对海量高频小额交易，通过缓冲表由 Job 汇总入账
- **F-11 冲账与红冲**：支持基于原凭证号的自动对调红冲，保留业务追溯链
- **F-12 单边记账场景**：支持受限场景下的单边调账，记录特种传票流水

### 账户与冻结 (Account & Freeze)
- **F-13 自动化开户**：基于模板自动初始化分户账及对应科目的挂载
- **F-14 状态控制**：实现账户/子账户级别的冻结、止付、止收状态机管理
- **F-15 资金冻结/解冻**：处理可用余额与冻结余额之间的物理划转与流水记录
- **F-16 冻结扣款**：直接在冻结额度上进行销账支取
- **F-17 余额实时查询**：提供包含可用、冻结、明细在内的聚合查询接口

### 核心核算模块 (Core Reconciliation)
- **F-18 借贷平衡校验**：全局性凭证借贷平衡核算
- **F-19 总分核对**：实现总账科目余额 vs 分户余额合计，以及余额 vs 流水明细的勾稽核对
- **F-20 会计日切 (EOD)**：会计日期变更及日终余额快照存档

## 核心业务流逻辑

### 标准入账全流程

#### 阶段一：记账流水 (Journaling)
1. 幂等校验：查询 `t_business_record`，若 `trace_no` + `trace_seq` 存在则幂等返回
2. 持久化请求：记录至 `t_business_record` 和 `t_business_detail`
3. 确定会计日期：在记账接口调用时确定会计日期，记录在 `t_business_record.accounting_date`（需新增字段）

#### 阶段二：凭证生成 (Vouchering)
1. 规则匹配：根据 `business_code`、`trading_code`、`pay_channel` 等获取 `t_accounting_rule`
2. 匹配分录模板（`funds_type`）与辅助核算模板，执行 SpEL 脚本计算
3. 试算校验：计算借贷方向与金额，预填辅助核算项，强制执行 `ΣDebit == ΣCredit`
4. 辅助核算项分摊：在凭证生成阶段处理分摊逻辑
   - 按比例分摊：前 N-1 个按比例计算，最后一个用总金额减去前面的（避免精度损失）
5. 缓冲规则匹配：在凭证生成阶段匹配缓冲规则（方案 A）
   - 若匹配到缓冲规则，生成凭证和分录（status=1 未过账），写入 `t_buffer_posting_detail`
   - 若未匹配到缓冲规则，继续正常记账流程

#### 阶段三：事务处理 (Transaction Management)
1. 检查事务记录：若不存在则创建 `t_transaction`，初始状态为 `PROCESSING`
2. 凭证关联事务：凭证先创建（txn_no 为空），事务创建后回填 txn_no
3. 开启编程式事务 `TransactionTemplate`

#### 阶段四：过账更新 (Posting)
1. **单边记账判断**：遍历分录，根据 `is_unilateral` 字段判断
   - `is_unilateral=1`：实时处理，执行步骤 2-4
   - `is_unilateral=0`：发送 MQ 异步处理，跳过步骤 2-4
2. **锁控制**：按 `account_no` 升序执行 `SELECT FOR UPDATE`
3. **余额计算**：`newBalance = balance ± amount`（绝对值法则）
   - 同向相加：`newBalance = oldBalance + amount`
   - 反向相减：必须前置校验 `oldBalance >= amount`，否则抛出 `ServiceException(ResultCode.INSUFFICIENT_BALANCE)`
4. **变动与记录**：
   - 更新 `t_account` 并记录 `t_account_detail`（含 Pre/Post 余额快照）
   - 更新 `t_sub_account` 并记录 `t_sub_account_detail`（含 Pre/Post 余额快照）
5. **事务收尾**：更新 `t_transaction` 为 `SUCCESS`，提交事务

### 缓冲记账完整流程

#### 缓冲规则匹配与写入
1. **凭证生成阶段**：在凭证生成阶段（阶段二）匹配缓冲规则
2. **凭证预生成**：匹配到缓冲规则后，生成凭证和分录（status=1 未过账）
3. **缓冲写入**：将分录信息写入 `t_buffer_posting_detail`（status=1 待入账）
4. **不生成账户明细**：此时不更新账户余额，不生成 `t_account_detail`

#### 三种缓冲模式

**模式 1：逐条缓冲（buffer_mode=1）**
- **触发时机**：实时触发，每条缓冲明细独立处理
- **处理逻辑**：
  1. 扫描 `t_buffer_posting_detail`，status=1 待入账
  2. 逐条处理，按 `account_no` 加悲观锁或乐观锁
  3. 更新账户余额（`t_account` 和 `t_sub_account`）
  4. 生成账户明细（`t_account_detail` 和 `t_sub_account_detail`，含 Pre/Post 余额）
  5. 更新缓冲明细 status=3 入账成功
- **适用场景**：需要准实时入账，但可以容忍秒级延迟

**模式 2：日间批量（buffer_mode=2）**
- **触发时机**：定时触发（如每 5 分钟）
- **处理逻辑**：
  1. 扫描 `t_buffer_posting_detail`，status=1 待入账，按 `account_no` 分组
  2. 按账户汇总借贷发生额：`SUM(CASE WHEN debit_credit=1 THEN amount ELSE 0 END) AS debit_sum`
  3. 一次性更新账户余额（按汇总金额更新 `t_account` 和 `t_sub_account`）
  4. 逐条生成账户明细（每条缓冲明细对应一条 `t_account_detail`，通过 running balance 计算 Pre/Post）
  5. 更新所有处理的缓冲明细 status=3 入账成功
- **适用场景**：高频小额交易，减少账户锁竞争

**模式 3：日终批量（buffer_mode=3）**
- **触发时机**：日终触发（日切流程中的存量清理阶段）
- **处理逻辑**：与模式 2 相同，按账户汇总更新余额，逐条生成明细
- **适用场景**：对实时性要求最低的业务，如内部核算账户

#### 锁升级策略
1. **默认乐观锁**：使用 `version` 字段更新账户余额
2. **失败重试**：乐观锁更新失败后，重试（最多 3 次）
3. **升级悲观锁**：重试达阈值后，升级为 `SELECT FOR UPDATE` 悲观锁
4. **失败处理**：若仍失败，更新 status=4 入账失败，记录失败原因，触发告警

#### 批量处理的 Running Balance 计算
- **问题**：批量汇总更新余额后，如何为每条明细计算 Pre/Post 余额？
- **解决方案**：
  1. 查询账户当前余额作为起始余额（begin_balance）
  2. 按时间顺序排序缓冲明细（`ORDER BY create_time ASC`）
  3. 逐条计算：
     - 第 1 条：pre_balance = begin_balance, post_balance = begin_balance ± amount_1
     - 第 2 条：pre_balance = post_balance_1, post_balance = post_balance_1 ± amount_2
     - 第 N 条：pre_balance = post_balance_(N-1), post_balance = post_balance_(N-1) ± amount_N
  4. 最终 post_balance_N 应等于汇总更新后的账户余额（用于校验）

### 逻辑日切流程 (Logical EOD)

#### 阶段 1：瞬间切日
- **操作**：系统将全局会计日期状态更新为 `T+1`
- **影响**：所有新请求均写入 `T+1` 会计日
- **实现**：更新配置表或缓存中的当前会计日期

#### 阶段 2：存量清理（必须完成）
- **目标**：处理所有 `T` 日未完成的业务
- **处理内容**：
  1. 扫描 `t_buffer_posting_detail`，处理所有 `accounting_date=T` 且 `status=1` 的缓冲明细
  2. 扫描 `t_transaction`，处理所有 `accounting_date=T` 且 `status=PROCESSING` 的事务
  3. 强制执行缓冲记账（按模式 3 日终批量处理）
  4. 确保所有 `T` 日业务都已过账或标记失败
- **失败处理**：若存在无法处理的记录，触发告警，人工介入

#### 阶段 3：余额快照
- **前置条件**：存量清理完成
- **操作**：
  1. 保存 `T` 日所有账户余额快照至 `t_account_balance_snapshot`
  2. 记录快照类型（snapshot_type=1 日快照）
  3. 记录快照日期（snapshot_date=T）和生成时间（snapshot_time=当前时间）
- **数据来源**：从 `t_account` 和 `t_sub_account` 读取当前余额

#### 阶段 4：试算平衡（存量清理后执行）
- **前置条件**：存量清理完成，余额快照已生成
- **执行内容**：
  1. 生成 `T` 日账户日余额表（`t_account_balance`）
  2. 执行借贷平衡校验
  3. 执行总分核对
- **详见**：下文"试算平衡详细流程"

#### 阶段 5：归档
- **操作**：
  1. 标记 `T` 日账务关闭（更新日切状态表）
  2. 记录日切完成时间
  3. 生成日切报告（可选）
- **后续**：系统正常运行在 `T+1` 会计日

### 试算平衡详细流程

#### 步骤 1：生成账户日余额数据
- **数据来源**：`t_account_detail` 表（`T` 日所有账户明细）
- **生成逻辑**：
  ```sql
  INSERT INTO t_account_balance (accounting_date, subject_code, account_no, currency, 
                                  balance_direction, begin_balance, debit_amount, 
                                  credit_amount, end_balance)
  SELECT 
      accounting_date,
      subject_code,
      account_no,
      currency,
      -- 期末余额方向（根据最后一条明细的 post_balance 判断）
      CASE WHEN MAX(post_balance) >= 0 THEN 1 ELSE 2 END AS balance_direction,
      -- 期初余额（第一条明细的 pre_balance）
      MIN(pre_balance) AS begin_balance,
      -- 借方发生额
      SUM(CASE WHEN debit_credit=1 THEN amount ELSE 0 END) AS debit_amount,
      -- 贷方发生额
      SUM(CASE WHEN debit_credit=2 THEN amount ELSE 0 END) AS credit_amount,
      -- 期末余额（最后一条明细的 post_balance）
      MAX(post_balance) AS end_balance
  FROM t_account_detail
  WHERE accounting_date = 'T'
  GROUP BY accounting_date, subject_code, account_no, currency;
  ```

#### 步骤 2：借贷平衡校验
- **校验规则**：全局借方发生额 = 全局贷方发生额
- **校验 SQL**：
  ```sql
  SELECT 
      SUM(debit_amount) AS total_debit,
      SUM(credit_amount) AS total_credit,
      SUM(debit_amount) - SUM(credit_amount) AS diff
  FROM t_account_balance
  WHERE accounting_date = 'T';
  ```
- **判断**：若 `diff != 0`，则借贷不平衡，触发告警

#### 步骤 3：总分核对
- **核对 1：科目总账 vs 分户余额合计**
  ```sql
  -- 按科目汇总分户余额
  SELECT 
      subject_code,
      SUM(end_balance) AS total_sub_balance
  FROM t_account_balance
  WHERE accounting_date = 'T'
  GROUP BY subject_code;
  
  -- 与科目总账余额对比（科目总账 = 该科目下所有账户余额之和）
  -- 若不一致，触发告警
  ```

- **核对 2：账户余额 vs 流水明细**
  ```sql
  -- 校验每个账户的余额是否等于明细计算结果
  SELECT 
      account_no,
      end_balance AS balance_from_summary,
      (SELECT post_balance FROM t_account_detail 
       WHERE account_no = t_account_balance.account_no 
         AND accounting_date = 'T' 
       ORDER BY id DESC LIMIT 1) AS balance_from_detail
  FROM t_account_balance
  WHERE accounting_date = 'T'
  HAVING balance_from_summary != balance_from_detail;
  
  -- 若有不一致记录，触发告警
  ```

#### 步骤 4：校验失败处理
- **失败类型**：
  1. 借贷不平衡：全局借方 ≠ 全局贷方
  2. 总分不符：科目总账 ≠ 分户余额合计
  3. 余额不符：账户余额 ≠ 流水明细计算结果
- **处理流程**：
  1. 记录校验失败详情（失败类型、差异金额、涉及账户）
  2. 触发告警（邮件、短信、监控平台）
  3. 阻止日切归档，等待人工处理
  4. 人工排查并修正数据后，重新执行试算平衡

## 关键状态机

### 凭证状态
- 1-待提交 → 2-已提交 → 3-已入账 → 4-已冲销

### 事务状态
- 1-未提交 → 2-部分提交 → 3-全部提交 → 4-部分回滚 → 5-全部回滚 → 6-失败

### 账户状态
- 1-正常 → 2-冻结 → 3-注销

### 风控状态
- 1-正常 → 2-止入 → 3-止出 → 4-止入止出


### 红冲逻辑完整流程

#### 前置校验
1. **原凭证状态校验**：
   - 查询原凭证（`orig_voucher_no`）的状态
   - 必须为 `status=3` 已过账，否则拒绝红冲
2. **分录过账校验**：
   - 查询原凭证的所有分录（`t_accounting_voucher_entry`）
   - 必须所有分录都为 `status=2` 已过账，否则拒绝红冲
3. **重复红冲校验**：
   - 查询是否已存在红冲凭证（`orig_voucher_no = 原凭证号`）
   - 若已存在，拒绝重复红冲

#### 生成红冲凭证
1. **凭证生成**：
   - 生成新凭证号（`voucher_no`）
   - 设置 `trade_type=3` 红字
   - 设置 `orig_voucher_no` 关联原凭证
   - 其他字段复制原凭证（`business_code`、`trading_code`、`pay_channel` 等）
2. **分录生成（方向对调）**：
   - 原凭证"借 A 贷 B"，红冲凭证生成"借 B 贷 A"
   - 金额保持正数（不使用负数）
   - 示例：
     ```
     原凭证：
       借：客户账户 100
       贷：内部账户 100
     
     红冲凭证：
       借：内部账户 100
       贷：客户账户 100
     ```
3. **辅助核算项**：
   - 复制原凭证的辅助核算项（`t_accounting_voucher_auxiliary`）
   - 调整 `change_direction`（增减方向对调）

#### 执行红冲过账
1. **按标准流程过账**：
   - 执行"阶段四：过账更新"流程
   - 更新账户余额（按红冲分录的借贷方向）
   - 生成账户明细（`t_account_detail`，含 Pre/Post 余额）
2. **更新原凭证状态**：
   - 更新原凭证 `status=4` 已冲销
   - 记录红冲时间和操作人

#### 红冲后的科目总账统计
- **借贷发生额真实准确**：
  - 原凭证：借方发生额 +100，贷方发生额 +100
  - 红冲凭证：借方发生额 +100（内部账户），贷方发生额 +100（客户账户）
  - 科目总账统计时，借贷发生额都会增加，真实反映业务活动
- **余额正确**：
  - 原凭证：客户账户 +100
  - 红冲凭证：客户账户 -100
  - 最终余额：0（正确冲销）

### 冻结与特殊场景

#### 冻结逻辑
- **冻结场景**：
  - 主账户（`t_account`）余额不变
  - 可用子账户（`balance_type=1`）余额减少
  - 冻结子账户（`balance_type=2`）余额增加
- **记账处理**：
  - 通过记账规则配置冻结/解冻凭证类型（`voucher_type`）
  - 匹配到冻结/解冻凭证类型时，执行冻结解冻记账流程
  - 必须记录子账户明细（`t_sub_account_detail`，含 Pre/Post 余额）
  - 必须创建冻结记录（`t_account_freeze_detail`）
- **冻结有效期**：
  - 根据记账规则配置的 `freeze_duration` 设置有效期
  - 超过有效期未解冻，由兜底任务自动解冻

#### 解冻逻辑
- **解冻场景**：
  - 主账户（`t_account`）余额不变
  - 冻结子账户（`balance_type=2`）余额减少
  - 可用子账户（`balance_type=1`）余额增加
- **记账处理**：
  - 更新冻结记录（`t_account_freeze_detail`）`status=2` 已解冻
  - 记录子账户明细（`t_sub_account_detail`）

#### 单边记账
- **定义**：跳过借贷平衡检查，直接驱动单向余额变更
- **应用场景**：部分账户实时更新，部分账户异步更新
- **实现**：通过 `t_accounting_rule_detail.is_unilateral` 字段标识

## 数据库设计调整建议

基于上述业务逻辑澄清，需要对数据库设计进行以下调整：

### 必须调整的字段

1. **删除字段**：
   - `t_accounting_rule.accounting_mode`：删除此字段，统一通过分录级别的 `is_unilateral` 判断

2. **新增字段**：
   - `t_business_record.accounting_date DATE NOT NULL`：记录会计日期，在记账接口调用时确定

3. **调整枚举值**：
   - `t_accounting_voucher.status`：调整为 1-未过账, 2-过账中, 3-已过账, 4-已冲销（新增已冲销状态）

### 建议调整的字段

1. **凭证状态优化**：
   - 当前 DDL：`status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-过账失败'`
   - 建议调整：`status TINYINT NOT NULL DEFAULT '1' COMMENT '凭证状态：1-未过账,2-过账中,3-已过账,4-已冲销'`
   - 理由：红冲逻辑需要标记原凭证为"已冲销"状态

### 索引优化建议

1. **日切相关索引**：
   - `t_buffer_posting_detail` 增加索引：`KEY idx_accounting_date_status (accounting_date, status, account_no)`
   - `t_transaction` 增加索引：`KEY idx_accounting_date_status (accounting_date, status)`

2. **红冲查询索引**：
   - `t_accounting_voucher` 增加索引：`KEY idx_orig_voucher_no (orig_voucher_no)` （若不存在）

## 余额查询一致性说明

### 缓冲期间的余额查询
- **一致性级别**：最终一致性
- **查询逻辑**：
  1. 查询账户当前余额（`t_account.balance` 或 `t_sub_account.balance`）
  2. 查询未入账的缓冲明细（`t_buffer_posting_detail`，status=1 待入账）
  3. 计算预估余额：`estimated_balance = current_balance + SUM(pending_amount)`
- **注意事项**：
  - 缓冲期间余额可能不是实时的
  - 如需实时一致性，将记账规则设置为单边记账（`is_unilateral=1`）
