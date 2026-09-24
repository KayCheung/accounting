# 领域模型速查表（domain-model.md）

> **使用说明**：本文件为 AI 日常开发的轻量参考，每张表只保留核心业务字段。
> 公共字段（`id` / `create_time` / `update_time` / `is_delete` / `tenant_id`）
> 通过 `BaseEntity` 统一继承，本文件中省略。
> 需要精确生成 PO / Mapper 时，读取对应域的完整 DDL 文件。

---

## 域一：账户域（`docs/sql/1-account.sql`）

### t_account · 账户表
- **唯一键**：`account_no`(UNIQUE) · `uk_owner_id(owner_id, subject_code)`
- **核心字段**：`subject_code` · `owner_id` · `owner_type` · `account_no` · `account_type` · `currency` · `balance_direction(1借/2贷)` · `balance(DECIMAL18,6)` · `status(1正常/2冻结/3注销)` · `risk_status(1正常/2止入/3止出/4止入止出)` · `version(乐观锁)`
- **约束**：`balance ≥ 0`；`status` 见 `AccountStatusEnum`；`risk_status` 可叠加，不影响主状态
- **关联**：→ `t_sub_account(account_no)` · → `t_account_detail(account_no)` · → `t_account_freeze_detail(voucher_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_sub_account · 子账户表
- **唯一键**：`uk(account_no, balance_type, balance_direction)`
- **核心字段**：`account_no` · `balance_type(1可用/2冻结)` · `balance_direction(1借/2贷)` · `balance(DECIMAL18,6)` · `version(乐观锁)`
- **约束**：每个 `t_account` 必有且仅有两条子账户（balance_type=1 和 balance_type=2）；余额不得为负
- **关联**：← `t_account(account_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_account_detail · 账户明细表
- **唯一键**：`uk(voucher_no, entry_id)`
- **核心字段**：`voucher_no` · `entry_id` · `txn_no` · `trace_no` · `account_no` · `debit_credit(1借/2贷)` · `change_direction(1增/2减)` · `pre_balance` · `amount` · `post_balance` · `accounting_date`
- **约束**：必须记录 Pre/Post 余额快照；`accounting_date` 与凭证一致，不可变更
- **关联**：← `t_accounting_voucher_entry(entry_id)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_sub_account_detail · 子账户明细表
- **唯一键**：`uk(voucher_no, entry_id)`
- **核心字段**：`account_no` · `balance_type(1可用/2冻结)` · `debit_credit` · `change_direction` · `pre_balance` · `amount` · `post_balance` · `accounting_date`
- **约束**：冻结/解冻操作独立记录此表，不走凭证引擎
- **关联**：← `t_account(account_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_account_freeze_detail · 账户资金冻结明细表
- **唯一键**：`uk(voucher_no)`
- **核心字段**：`voucher_no` · `txn_no` · `trace_no` · `freeze_amount(DECIMAL18,6)` · `status(1冻结/2已解冻)` · `expire_time(2099表示永不过期)` · `version(乐观锁)`
- **约束**：冻结时可用子账户减少，冻结子账户增加，主账户余额不变；`expire_time` 由记账规则 `freeze_duration` 决定
- **关联**：← `t_accounting_voucher(voucher_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_account_balance · 账户日余额表（按年分区）
- **唯一键**：`uk(accounting_date, account_no, is_delete)`
- **核心字段**：`accounting_date` · `subject_code` · `account_no` · `balance_direction` · `begin_balance` · `debit_amount` · `credit_amount` · `end_balance`
- **约束**：按年 RANGE 分区；EOD 阶段 4 试算平衡时生成
- **关联**：← `t_account(account_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

### t_account_balance_snapshot · 账户余额快照表（按年分区）
- **唯一键**：`uk(snapshot_date, snapshot_type, account_no, is_delete)`
- **核心字段**：`snapshot_date` · `snapshot_type(1日/2月/3年/4自定义)` · `snapshot_time` · `account_no` · `balance(DECIMAL18,6)` · `ext_json`
- **约束**：EOD 阶段 3 生成日快照（snapshot_type=1）；按年 RANGE 分区
- **关联**：← `t_account(account_no)`
- **所在 DDL**：`docs/sql/1-account.sql`

---

## 域二：凭证域（`docs/sql/2-voucher.sql`）

### t_accounting_voucher · 记账凭证表
- **唯一键**：`uk(voucher_no)` · `uk(trace_no, trace_seq)`
- **核心字段**：`voucher_no` · `txn_no(凭证先创，事后回填)` · `trace_no` · `trace_seq` · `voucher_type` · `trade_type(1正常/2调账/3红/4蓝)` · `amount` · `status(1未过账/2过账中/3已过账/4过账失败/5已冲销)` · `accounting_date` · `orig_voucher_no(红冲关联原凭证)` · `version(乐观锁)`
- **约束**：借贷平衡校验通过后才允许过账；`accounting_date` 在业务流水入库时确定，不可变更
- **关联**：→ `t_accounting_voucher_entry(voucher_no)` · → `t_transaction(txn_no)`
- **所在 DDL**：`docs/sql/2-voucher.sql`

### t_accounting_voucher_entry · 分录流水表
- **唯一键**：`uk(entry_id)`
- **核心字段**：`voucher_no` · `entry_id` · `row_num` · `subject_code` · `account_no` · `debit_credit(1借/2贷)` · `amount(DECIMAL18,6)` · `status(1未过账/2已过账/3过账失败)` · `accounting_date` · `version(乐观锁)`
- **约束**：`is_unilateral=1` 的分录实时过账；`is_unilateral=0` 的分录发 MQ 异步处理（字段来自 `t_accounting_rule_detail`）
- **关联**：← `t_accounting_voucher(voucher_no)` · → `t_account_detail(entry_id)`
- **所在 DDL**：`docs/sql/2-voucher.sql`

### t_accounting_voucher_auxiliary · 凭证辅助核算项目
- **唯一键**：`uk(entry_id, aux_code)`
- **核心字段**：`voucher_no` · `entry_id` · `subject_code` · `aux_type` · `aux_code` · `aux_name` · `change_direction(1增/2减)` · `amount`
- **约束**：由凭证生成引擎自动从 `t_accounting_rule_auxiliary` 分摊生成
- **关联**：← `t_accounting_voucher_entry(entry_id)`
- **所在 DDL**：`docs/sql/2-voucher.sql`

### t_accounting_voucher_attachment · 凭证附件表
- **核心字段**：`voucher_no` · `file_path`
- **关联**：← `t_accounting_voucher(voucher_no)`
- **所在 DDL**：`docs/sql/2-voucher.sql`

---

## 域三：规则域（`docs/sql/3-rule.sql`）

### t_accounting_rule · 记账规则表
- **唯一键**：`uk(business_code, trading_code, pay_channel, is_delete)`
- **核心字段**：`rule_name` · `voucher_type` · `business_code` · `trading_code` · `pay_channel` · `is_open_account(0否/1是)` · `freeze_duration(冻结时长秒)` · `pre_rule_id(前置规则ID)` · `status(1待启用/2启用/3停用)`
- **约束**：启动时预加载 status=2 的规则到内存缓存；规则启用/停用时同步热更新缓存
- **关联**：→ `t_accounting_rule_detail(rule_id)` · → `t_accounting_rule_auxiliary(rule_id)`
- **所在 DDL**：`docs/sql/3-rule.sql`

### t_accounting_rule_detail · 记账规则明细表
- **唯一键**：`uk(rule_id, row_num, is_delete)` · `uk(rule_id, subject_code, funds_type, is_delete)`
- **核心字段**：`rule_id` · `row_num` · `funds_type` · `subject_code` · `account_scope(1内部/2外部)` · `debit_credit(1借/2贷)` · `is_unilateral(0否/1实时更新余额)` · `extend_script(SpEL脚本)`
- **约束**：`is_unilateral=1` 实时过账；`is_unilateral=0` 发 MQ 异步
- **关联**：← `t_accounting_rule(rule_id)` · → `t_accounting_rule_auxiliary(rule_detail_id)`
- **所在 DDL**：`docs/sql/3-rule.sql`

### t_accounting_rule_auxiliary · 规则辅助核算项表
- **唯一键**：`uk(rule_detail_id, aux_code, is_delete)`
- **核心字段**：`rule_id(冗余)` · `rule_detail_id` · `aux_type` · `aux_code` · `allocation_method(1不分摊/2固定金额/3按比例)` · `allocation_value` · `extend_script`
- **约束**：按比例分摊时，前 N-1 条按比例计算，最后一条补差（防精度损失）
- **关联**：← `t_accounting_rule_detail(rule_detail_id)`
- **所在 DDL**：`docs/sql/3-rule.sql`

### t_buffer_posting_rule · 缓冲入账规则表
- **核心字段**：`rule_name` · `buffer_mode(1逐条/2日间批量/3日终批量)` · `business_code` · `trading_code` · `pay_channel` · `subject_code` · `account_no` · `debit_credit` · `effective_time` · `expiration_time`
- **约束**：`subject_code` 与 `account_no` 必须有一个不为空；时间区间不得重叠
- **关联**：→ `t_buffer_posting_detail(rule_id)`
- **所在 DDL**：`docs/sql/3-rule.sql`

### t_buffer_posting_detail · 缓冲记账明细表
- **唯一键**：`uk(voucher_no, entry_id)`
- **核心字段**：`rule_id` · `buffer_mode` · `voucher_no` · `entry_id` · `account_no` · `debit_credit` · `amount` · `accounting_date` · `status(1待入账/2处理中/3成功/4失败)` · `retry_count` · `sharding(分片值)` · `version(乐观锁)`
- **约束**：同一账户必须落同一分片（`sharding` 按 `account_no` 哈希取模）；批量模式末条 `post_balance` 必须等于账户当前余额
- **关联**：← `t_accounting_voucher_entry(entry_id)`
- **所在 DDL**：`docs/sql/3-rule.sql`

---

## 域四：科目域（`docs/sql/4-subject.sql`）

### t_account_subject · 会计科目表
- **唯一键**：`uk(subject_code, is_delete)`
- **核心字段**：`subject_code` · `subject_name` · `subject_level` · `parent_subject_id` · `subject_category(0表外/1资产/2负债/3权益/4共同/5成本/6损益)` · `nature(1非特殊/2销账/3贷款/4现金)` · `debit_credit(1借/2贷)` · `is_leaf(0否/1是)` · `allow_post(0否/1是)` · `allow_open_account` · `status(1启用/2停用)`
- **约束**：子科目编码必须以父科目编码为前缀；末级且 `allow_post=1` 才允许记账；被引用后禁止修改核心属性
- **关联**：→ `t_account_subject_auxiliary(subject_code)` · → `t_account(subject_code)`
- **所在 DDL**：`docs/sql/4-subject.sql`

### t_account_subject_auxiliary · 科目辅助核算项
- **唯一键**：`uk(subject_code, auxiliary_type, is_delete)`
- **核心字段**：`subject_code` · `auxiliary_type(字典CODE)` · `required(0可选/1必填)` · `default_aux_code`
- **关联**：← `t_account_subject(subject_code)`
- **所在 DDL**：`docs/sql/4-subject.sql`

### t_account_template · 开户模板表
- **唯一键**：`uk(business_code, customer_type, subject_code, is_delete)`
- **核心字段**：`business_code` · `customer_type(1个人/2企业/99其他)` · `auto_open(0否/1是)` · `subject_code` · `account_type(字典CODE)` · `currency` · `balance_direction` · `acct_no_rule` · `acct_name_rule` · `status(1待启用/2启用/3停用)`
- **约束**：`auto_open=1` 时系统自动开户；关联科目必须为末级且 `allow_open_account=1`
- **关联**：← `t_account_subject(subject_code)` · → `t_account(开户时使用)`
- **所在 DDL**：`docs/sql/4-subject.sql`

---

## 域五：流水域（`docs/sql/5-journal.sql`）

### t_business_record · 业务记账流水表
- **唯一键**：`uk(trace_no, trace_seq)`
- **核心字段**：`business_code` · `trace_no` · `trace_seq` · `trading_code` · `pay_channel` · `trade_type(1正常/2调账/3红/4蓝)` · `amount` · `trade_time` · `accounting_date(在此确定，全链路不可变更)` · `status(1处理中/2成功/3失败)` · `version(乐观锁)`
- **约束**：`accounting_date` 在此表写入时确定，后续全链路使用此日期
- **关联**：→ `t_business_detail(trace_no)` · → `t_accounting_voucher(trace_no)`
- **所在 DDL**：`docs/sql/5-journal.sql`

### t_business_detail · 业务流水明细表
- **唯一键**：`uk(trace_no, trace_seq, customer_id, item_code)`
- **核心字段**：`trace_no` · `trace_seq` · `customer_type` · `customer_id` · `funds_type(字典CODE)` · `amount`
- **关联**：← `t_business_record(trace_no)`
- **所在 DDL**：`docs/sql/5-journal.sql`

### t_transaction · 事务表
- **唯一键**：`uk(txn_no)`
- **核心字段**：`txn_no` · `trace_no` · `accounting_date` · `relate_account_count` · `amount` · `status(1处理中/2成功/3失败)` · `fail_reason` · `finish_time` · `version(乐观锁)`
- **约束**：凭证先创建（`txn_no` 为空），事务创建后回填；事务回滚时同步更新 `status=FAILED`
- **关联**：← `t_accounting_voucher(txn_no)`
- **所在 DDL**：`docs/sql/5-journal.sql`

---

## 域六：支撑域（`docs/sql/6-infra.sql`）

### t_dictionary · 字典表
- **唯一键**：`uk(dict_type, dict_code, is_delete)`
- **核心字段**：`dict_type` · `dict_code` · `dict_name` · `dict_name_en` · `sort_order` · `group_key` · `status(1启用/2停用)` · `is_system(1系统内置禁删/0可维护)` · `ext_json`
- **约束**：`is_system=1` 的字典禁止删除，只允许停用；变更后同步清除 Caffeine + Redis 二级缓存
- **所在 DDL**：`docs/sql/6-infra.sql`

### t_local_message · 本地消息表（Outbox Pattern）
- **唯一键**：`uk(message_id)` · `uk(business_key)`
- **核心字段**：`message_id` · `topic` · `tag` · `business_key` · `payload(JSON)` · `status(1待发送/2已发送/3失败/4已确认)` · `retry_count` · `max_retry(默认3)` · `next_retry_time`
- **约束**：消息与业务数据同一事务写入；Job 扫描重试，指数退避（10s/30s/60s）；超限标记失败并告警
- **关联**：→ `t_message_receipt(message_id)`
- **所在 DDL**：`docs/sql/6-infra.sql`

### t_message_receipt · 消息回执表
- **唯一键**：`uk(message_id, consumer_group)`
- **核心字段**：`message_id` · `business_key` · `consumer_group` · `topic` · `status(1成功/2失败)` · `error_code` · `error_message` · `received_time` · `processed_time`
- **关联**：← `t_local_message(message_id)`
- **所在 DDL**：`docs/sql/6-infra.sql`

### t_period_end_transfer_rule · 期末结转规则表
- **唯一键**：`uk(rule_code, is_delete)`
- **核心字段**：`rule_code` · `transfer_type(1损益/2成本/3自定义)` · `source_subject_code(支持通配符，如6*)` · `target_subject_code` · `transfer_direction(1借转贷/2贷转借)` · `summary_template` · `execute_order` · `status`
- **约束**：EOD 阶段 4.5 按 `execute_order` 升序执行；可选阶段
- **所在 DDL**：`docs/sql/6-infra.sql`

### t_period_end_transfer_record · 期末结转记录表
- **唯一键**：`uk(transfer_no)`
- **核心字段**：`transfer_no` · `accounting_date` · `rule_code` · `voucher_no(生成的凭证号)` · `total_amount` · `status(1处理中/2成功/3失败)` · `fail_reason`
- **关联**：← `t_period_end_transfer_rule(rule_code)`
- **所在 DDL**：`docs/sql/6-infra.sql`

---

## 表清单汇总

| 域 | 表名 | 说明 |
|----|------|------|
| 账户域 | `t_account` | 总账账户 |
| 账户域 | `t_sub_account` | 子账户（可用/冻结） |
| 账户域 | `t_account_detail` | 账户明细（含Pre/Post快照） |
| 账户域 | `t_sub_account_detail` | 子账户明细 |
| 账户域 | `t_account_freeze_detail` | 资金冻结明细 |
| 账户域 | `t_account_balance` | 账户日余额（按年分区） |
| 账户域 | `t_account_balance_snapshot` | 余额快照（按年分区） |
| 凭证域 | `t_accounting_voucher` | 记账凭证 |
| 凭证域 | `t_accounting_voucher_entry` | 分录流水 |
| 凭证域 | `t_accounting_voucher_auxiliary` | 凭证辅助核算项 |
| 凭证域 | `t_accounting_voucher_attachment` | 凭证附件 |
| 规则域 | `t_accounting_rule` | 记账规则 |
| 规则域 | `t_accounting_rule_detail` | 规则明细 |
| 规则域 | `t_accounting_rule_auxiliary` | 规则辅助核算项 |
| 规则域 | `t_buffer_posting_rule` | 缓冲入账规则 |
| 规则域 | `t_buffer_posting_detail` | 缓冲记账明细 |
| 科目域 | `t_account_subject` | 会计科目 |
| 科目域 | `t_account_subject_auxiliary` | 科目辅助核算项 |
| 科目域 | `t_account_template` | 开户模板 |
| 流水域 | `t_business_record` | 业务记账流水 |
| 流水域 | `t_business_detail` | 流水明细 |
| 流水域 | `t_transaction` | 事务 |
| 支撑域 | `t_dictionary` | 字典 |
| 支撑域 | `t_local_message` | 本地消息（Outbox） |
| 支撑域 | `t_message_receipt` | 消息回执 |
| 支撑域 | `t_period_end_transfer_rule` | 期末结转规则 |
| 支撑域 | `t_period_end_transfer_record` | 期末结转记录 |
