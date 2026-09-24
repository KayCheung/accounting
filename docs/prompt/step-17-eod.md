# step-17-eod · 日切与试算平衡

> **Phase 6 第二步** | 归属：`@Java` 工程师
> 前置依赖：Step 16（缓冲记账）+ Step 14（资金冻结与解冻）
>
> **与 Step 12/16 的边界**：Step 12 负责**实时/异步过账**，Step 16 负责**缓冲明细延迟过账 + Running Balance 校验**。本 Step 负责**日终整体收尾**：检查当日所有过账是否完成 → 计算并持久化日余额 → 执行试算平衡 → 按配置执行期末结转 → 生成余额快照。
>
> **BufferPostingEodJobHandler 已在 Step 16 完成**，负责缓冲日终批量 + Running Balance 校验。本 Step 新建 `EodJobHandler` 作为**日切总调度**，在 BufferPostingEodJobHandler 执行完毕后触发。

---

## 0. 前置补充任务（开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `AccountBalanceMapper` 补充日余额查询/写入方法 | `AccountBalanceMapper.java` + XML | 按会计日期查询/批量 upsert 日余额记录 |
| P0-2 | `AccountBalanceSnapshotMapper` 补充快照写入方法 | `AccountBalanceSnapshotMapper.java` | 批量插入日快照记录 |
| P0-3 | `PeriodEndTransferRuleMapper` 补充规则查询方法 | `PeriodEndTransferRuleMapper.java` | 按状态 + 类型查询启用中的结转规则（按 execute_order 升序） |
| P0-4 | `PeriodEndTransferRecordMapper` 补充记录写入方法 | `PeriodEndTransferRecordMapper.java` | 插入结转记录 + 按结转流水号查询 |
| P0-5 | `TransactionMapper` 补充状态统计方法 | `TransactionMapper.java` + XML | 按会计日期 + 状态统计事务数量 |
| P0-6 | `AccountingVoucherEntryMapper` 补充分录汇总方法 | `AccountingVoucherEntryMapper.java` + XML | 按会计日期 + 科目汇总借贷方发生额（试算平衡用） |
| P0-7 | `AccountingVoucherMapper` 补充凭证汇总方法 | `AccountingVoucherMapper.java` | 按会计日期统计凭证状态分布 |
| P0-8 | DDL 确认 | `docs/sql/all-tables.sql` | 确认 t_account_balance / t_account_balance_snapshot / t_period_end_transfer_rule / t_period_end_transfer_record 表结构完整 |

### P0-1: AccountBalanceMapper 日余额写入（Upsert）

> **注意**：MySQL 5.7 不支持 INSERT ... ON DUPLICATE KEY UPDATE 的优雅写法，但我们的场景是日终批量计算，每天每个账户只写入一次，使用 INSERT IGNORE 或先查后插均可。此处用 XML 实现 INSERT ... ON DUPLICATE KEY UPDATE。

```java
/**
 * 批量 Upsert 日余额记录
 * <p>
 * 涉及 INSERT ... ON DUPLICATE KEY UPDATE，需要写 XML。
 */
int batchUpsertBalance(
    @Param("list") List<AccountBalancePO> list);
```

对应 XML：

```xml
<insert id="batchUpsertBalance">
    INSERT INTO t_account_balance
        (accounting_date, subject_code, account_no, currency,
         balance_direction, begin_balance, debit_amount, credit_amount, end_balance,
         create_time, update_time, is_delete, tenant_id)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.accountingDate}, #{item.subjectCode}, #{item.accountNo}, #{item.currency},
         #{item.balanceDirection.code}, #{item.beginBalance}, #{item.debitAmount}, #{item.creditAmount}, #{item.endBalance},
         NOW(), NOW(), 0, #{item.tenantId})
    </foreach>
    ON DUPLICATE KEY UPDATE
        debit_amount = VALUES(debit_amount),
        credit_amount = VALUES(credit_amount),
        end_balance = VALUES(end_balance),
        balance_direction = VALUES(balance_direction),
        update_time = NOW()
</insert>
```

### P0-2: AccountBalanceSnapshotMapper 快照写入

```java
/**
 * 批量插入日快照记录
 */
int batchInsertSnapshot(
    @Param("list") List<AccountBalanceSnapshotPO> list);
```

```xml
<insert id="batchInsertSnapshot">
    INSERT INTO t_account_balance_snapshot
        (snapshot_date, snapshot_type, snapshot_time, subject_code, account_no,
         currency, balance_direction, balance, ext_json,
         create_time, update_time, is_delete, tenant_id)
    VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.snapshotDate}, #{item.snapshotType.code}, #{item.snapshotTime},
         #{item.subjectCode}, #{item.accountNo}, #{item.currency},
         #{item.balanceDirection.code}, #{item.balance}, #{item.extJson},
         NOW(), NOW(), 0, #{item.tenantId})
    </foreach>
</insert>
```

### P0-3: PeriodEndTransferRuleMapper 规则查询

```java
/**
 * 查询启用中的结转规则（按 execute_order 升序）
 */
default List<PeriodEndTransferRulePO> selectEnabledRules(
        @Param("transferType") Integer transferType) {
    LambdaQueryWrapper<PeriodEndTransferRulePO> wrapper = new LambdaQueryWrapper<PeriodEndTransferRulePO>()
            .eq(PeriodEndTransferRulePO::getStatus, AvailableStatusEnum.ENABLED)
            .eq(PeriodEndTransferRulePO::getIsDelete, 0)
            .orderByAsc(PeriodEndTransferRulePO::getExecuteOrder);
    if (transferType != null) {
        wrapper.eq(PeriodEndTransferRulePO::getTransferType, transferType);
    }
    return this.selectList(wrapper);
}
```

### P0-6: AccountingVoucherEntryMapper 分录汇总（试算平衡核心）

```java
/**
 * 按科目汇总当日借贷方发生额（试算平衡用）
 * <p>
 * 仅统计已过账分录（status=2 POSTED）
 */
List<Map<String, Object>> sumEntriesBySubject(
    @Param("accountingDate") LocalDate accountingDate);
```

```xml
<select id="sumEntriesBySubject" resultType="java.util.HashMap">
    SELECT subject_code,
           SUM(CASE WHEN debit_credit = 1 THEN amount ELSE 0 END) AS total_debit,
           SUM(CASE WHEN debit_credit = 2 THEN amount ELSE 0 END) AS total_credit,
           COUNT(*) AS entry_count
    FROM t_accounting_voucher_entry
    WHERE accounting_date = #{accountingDate}
      AND status = 2
      AND is_delete = 0
    GROUP BY subject_code
</select>
```

---

## 1. 任务目标（Mission）

实现日切与试算平衡引擎，作为 Phase 6 的**日终整体收尾**流程：

1. **日切前置检查**：验证当日所有缓冲记账已完成、无处理中事务、无未过账凭证
2. **日余额计算**：基于当日全部已过账分录 + 子账户明细，计算每个账户的期初/借方发生额/贷方发生额/期末余额，写入 `t_account_balance`
3. **试算平衡**：按科目汇总当日借贷方发生额，验证借贷平衡
4. **期末结转执行**：按配置的结转规则（损益/成本/自定义）逐条执行，生成结转凭证
5. **余额快照生成**：将当日末余额写入 `t_account_balance_snapshot`（snapshot_type=DAY）
6. **日切总调度 Job**：编排上述流程的 XXL-JOB Handler

> **核心原则**：日切是**只读 + 汇总计算**流程，不影响已有余额数据（仅新增余额记录和快照）。试算平衡失败则阻断后续结转和快照流程，等待人工介入。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 余额方向约束、严禁负数运算、借贷平衡 |
| 3 | `docs/sql/all-tables.sql` | 全部 DDL（重点看 t_account_balance / t_account_balance_snapshot / t_period_end_transfer_*） |
| 4 | `docs/prompt/step-12-posting.md` | Step 12 过账引擎（凭证状态/分录状态参考） |
| 5 | `docs/prompt/step-16-buffer-posting.md` | Step 16 缓冲记账（Running Balance 校验 + BufferPostingEodJobHandler） |
| 6 | `accounting-core/.../domain/service/PostingEngineDomainService.java` | 批量过账模式参考 |
| 7 | `accounting-core/.../domain/service/BufferPostingEngineDomainService.java` | 缓冲记账引擎（日终批量参考） |
| 8 | `accounting-core/.../domain/service/BalanceQueryDomainService.java` | 余额查询（余额计算参考） |
| 9 | `accounting-job/.../job/BatchPostingJobHandler.java` | XXL-JOB Handler 编写模式参考 |
| 10 | `accounting-core/.../domain/service/VoucheringDomainService.java` | 凭证生成（期末结转凭证生成参考） |

---

## 3. 任务分配与子 Agent 编排

```
Phase 1（串行）：P0 前置补充任务
  └─ Agent-A：完成 P0-1~P0-8（Mapper/XML/Repository 补充 + DDL 确认）

Phase 2（可并行）：核心业务逻辑
  ├─ Agent-B：EodCheckDomainService（日切前置检查）+ TrialBalanceDomainService（试算平衡）
  ├─ Agent-C：EodDomainService（日余额计算 + 快照生成 + 期末结转执行）
  └─ Agent-D：PeriodEndTransferDomainService（期末结转规则执行 + 结转凭证生成）

Phase 3（依赖 Phase 2）：Job + Controller
  └─ Agent-E：EodJobHandler（日切总调度 Job）+ EodApplicationService + EodController + DTO
```

> **执行顺序**：Agent-A → (Agent-B + Agent-C + Agent-D 并行) → Agent-E

### Agent-A：P0 前置补充

**负责**：P0-1~P0-8 全部 Mapper/XML 补充 + DDL 确认
**输出**：5 段 XML SQL + 7 个 Mapper default 方法

### Agent-B：日切检查 + 试算平衡

**负责**：
- `EodCheckDomainService`
  - `checkEodPreconditions(LocalDate accountingDate)` — 日切前置检查
    - 缓冲明细：当日无 status=1(待入账)/2(处理中) 的缓冲明细
    - 事务：当日无 status=1(处理中) 的事务
    - 凭证：当日无 status=1(未过账)/2(过账中) 的凭证
    - 流水：当日无 status=1(处理中) 的业务流水
- `TrialBalanceDomainService`
  - `executeTrialBalance(LocalDate accountingDate)` — 按科目汇总借贷方发生额，验证平衡
  - 不平衡时返回差异明细（科目编码、借方合计、贷方合计、差额）

### Agent-C：日余额计算 + 快照生成

**负责**：`EodDomainService`
- `calculateDailyBalances(LocalDate accountingDate)` — 计算每个账户的日余额
  - begin_balance = 前一日 end_balance（或账户 opening_balance）
  - debit_amount = 当日借方发生额合计
  - credit_amount = 当日贷方发生额合计
  - end_balance = begin_balance ± debit_amount ± credit_amount（根据余额方向计算）
- `upsertDailyBalances(...)` — 批量 Upsert 到 t_account_balance
- `generateDailySnapshot(LocalDate accountingDate)` — 生成日快照写入 t_account_balance_snapshot

### Agent-D：期末结转执行

**负责**：`PeriodEndTransferDomainService`
- `executeTransferRules(LocalDate accountingDate)` — 按 execute_order 升序逐条执行结转规则
- 对每条规则：
  1. 解析 source_subject_code（支持通配符，如 6*）
  2. 查询符合条件的科目当日余额
  3. 按 transfer_direction 计算结转金额
  4. 调用 VoucheringDomainService 生成结转凭证（source 科目 ↔ target 科目）
  5. 记录到 t_period_end_transfer_record
- 单条规则失败不中断后续规则

### Agent-E：日切总调度 + 接口

**负责**：`EodJobHandler` + `EodApplicationService` + `EodController` + DTO
- `@XxlJob("eodJob")` — 日切总调度 Job（每日 23:55 执行，在 BufferPostingEodJob 之后 5 分钟）
- `POST /accounting/eod/execute` — 手动触发日切
- `GET /accounting/eod/precheck` — 查询日切前置检查结果
- `GET /accounting/eod/trial-balance` — 查询试算平衡结果

---

## 4. 核心业务规则

### 4.1 日切前置检查

```
触发时机：日切总调度 Job 执行时第一步

检查项（全部通过才可继续日切）：
  1. 缓冲明细检查：
     SELECT COUNT(*) FROM t_buffer_posting_detail
       WHERE accounting_date = #{date}
         AND status IN (1, 2)  -- 待入账/处理中
         AND is_delete = 0
     结果必须为 0

  2. 事务状态检查：
     SELECT COUNT(*) FROM t_transaction
       WHERE accounting_date = #{date}
         AND status = 1  -- 处理中
         AND is_delete = 0
     结果必须为 0

  3. 凭证状态检查：
     SELECT COUNT(*) FROM t_accounting_voucher
       WHERE accounting_date = #{date}
         AND status IN (1, 2)  -- 未过账/过账中
         AND is_delete = 0
     结果必须为 0

  4. 业务流水检查：
     SELECT COUNT(*) FROM t_business_record
       WHERE accounting_date = #{date}
         AND status = 1  -- 处理中
         AND is_delete = 0
     结果必须为 0

  5. 冻结记录检查（可选）：
     SELECT COUNT(*) FROM t_account_freeze_detail
       WHERE expire_time <= NOW()
         AND status = 1  -- 冻结中
         AND is_delete = 0
     结果必须为 0（即无过期未解冻记录）

检查结果：
  - 全部通过 → 继续日切流程
  - 存在未处理项 → 记录日志 + 阻断日切 + 返回 EOD_BLOCKED 错误码
    日志格式：[EOD-PRECHECK-FAILED] date=X, bufferPending=N, processingTxn=M, unpostedVoucher=P
```

### 4.2 日余额计算

```
计算范围：当日有过账记录的账户（通过 t_accounting_voucher_entry + t_sub_account_detail 关联）

计算逻辑（按账户独立计算，无并发依赖）：
  对每个有当日过账的 account_no：

  1. 查询前一日末余额（begin_balance）：
     SELECT end_balance FROM t_account_balance
       WHERE accounting_date = #{date} - INTERVAL 1 DAY
         AND account_no = #{accountNo}
         AND is_delete = 0
     如果无记录（新开户/首次记账）→ begin_balance = 账户 opening_balance

  2. 计算当日借方发生额（debit_amount）：
     SELECT SUM(amount) FROM t_accounting_voucher_entry
       WHERE accounting_date = #{date}
         AND account_no = #{accountNo}
         AND debit_credit = 1  -- 借方
         AND status = 2  -- 已过账
         AND is_delete = 0

  3. 计算当日贷方发生额（credit_amount）：
     SELECT SUM(amount) FROM t_accounting_voucher_entry
       WHERE accounting_date = #{date}
         AND account_no = #{accountNo}
         AND debit_credit = 2  -- 贷方
         AND status = 2
         AND is_delete = 0

  4. 计算期末余额（end_balance）：
     根据账户余额方向（balance_direction）计算：
     - 借方余额账户（balance_direction=1）：
       end_balance = begin_balance + debit_amount - credit_amount
     - 贷方余额账户（balance_direction=2）：
       end_balance = begin_balance + credit_amount - debit_amount

  5. 余额充足性校验：
     如果计算出的 end_balance < 0 → 告警（说明余额计算有误）
     记录：[EOD-BALANCE-NEGATIVE] accountNo=X, endBalance=Y

  6. 写入 t_account_balance（upsert）：
     每天每个账户只写入一次，重复执行时覆盖更新

性能优化：
  - 批量查询：一次性查询当日全部有过账的账户
  - 批量 upsert：一次 INSERT ... ON DUPLICATE KEY UPDATE 写入全部记录
  - 按 account_no 升序处理（避免潜在的并发锁问题）
```

### 4.3 试算平衡

```
触发时机：日余额计算完成后

试算平衡规则：
  有借必有贷，借贷必相等

计算逻辑：
  1. 按科目汇总当日已过账分录的借贷方发生额：
     SELECT subject_code,
            SUM(CASE WHEN debit_credit = 1 THEN amount ELSE 0 END) AS total_debit,
            SUM(CASE WHEN debit_credit = 2 THEN amount ELSE 0 END) AS total_credit
       FROM t_accounting_voucher_entry
       WHERE accounting_date = #{date}
         AND status = 2  -- 已过账
         AND is_delete = 0
       GROUP BY subject_code

  2. 全局汇总：
     total_debit_all = SUM(total_debit) across all subjects
     total_credit_all = SUM(total_credit) across all subjects

  3. 差额计算：
     diff = |total_debit_all - total_credit_all|

  4. 判定：
     - diff <= 0.000001 → 试算平衡通过
     - diff > 0.000001 → 试算平衡失败

  5. 失败处理：
     - 记录每个不平衡科目的差额明细
     - 阻断后续结转和快照流程
     - 返回 TRIAL_BALANCE_FAILED 错误码
     - 告警日志格式：
       [TRIAL-BALANCE-FAILED] date=X, totalDebit=Y, totalCredit=Z, diff=W
       [TRIAL-BALANCE-IMBALANCED] subject=601001, debit=1000.00, credit=999.95, diff=0.05

注意：
  - 试算平衡仅验证已过账（status=2）的分录
  - 未过账/过账失败的分录不参与试算平衡（应在前置检查阶段被拦截）
  - 差额阈值 0.000001（DECIMAL(18,6) 精度）
```

### 4.4 期末结转执行

```
触发时机：试算平衡通过后

执行逻辑（按规则逐条执行，单条失败不中断）：
  1. 查询启用中的结转规则（按 execute_order 升序）：
     SELECT * FROM t_period_end_transfer_rule
       WHERE status = 1  -- 启用
         AND is_delete = 0
       ORDER BY execute_order ASC

  2. 对每条规则：
     a. 解析 source_subject_code 通配符：
        - 如 "6*" → 匹配所有 6 开头的科目（损益类科目）
        - 使用 LIKE 查询：subject_code LIKE '6%'

     b. 查询符合条件的科目当日余额：
        SELECT account_no, subject_code, end_balance, balance_direction
          FROM t_account_balance
          WHERE accounting_date = #{date}
            AND subject_code LIKE #{pattern}
            AND is_delete = 0

     c. 对每个有余额的账户：
        - 根据 transfer_direction 确定结转方向：
          - DEBIT_TO_CREDIT(1)：借方余额 → 结转到贷方
            生成凭证：借方科目（红字/负数金额） → 贷方 target_subject
          - CREDIT_TO_DEBIT(2)：贷方余额 → 结转到借方
            生成凭证：借方 target_subject → 贷方科目（红字/负数金额）
        - 结转金额 = 该账户当日末余额（全额结转）

     d. 生成结转凭证：
        调用 VoucheringDomainService 或直接在事务中生成：
        - voucher_type = "结账凭证"
        - posting_type = 2（机制凭证）
        - trade_type = 4（蓝，正常结转）
        - 摘要：按 summary_template 渲染（如 "2026年06月损益结转"）

     e. 更新余额表（结转后余额应为零）：
        更新 t_account_balance.end_balance = 0
        对于已结转的账户

     f. 记录结转结果：
        插入 t_period_end_transfer_record：
        - transfer_no = 唯一流水号（格式：EODTR{yyyyMMdd}{seq}）
        - accounting_date = 当日日期
        - transfer_type = 规则类型
        - rule_code = 规则编码
        - voucher_no = 生成的凭证号
        - total_amount = 结转总金额
        - status = 2（成功）

     g. 失败处理：
        - 记录到 t_period_end_transfer_record（status=3 失败）
        - 日志格式：[EOD-TRANSFER-FAILED] rule=X, reason=Y
        - 不中断后续规则

  3. 全部规则执行完毕后：
     - 日志汇总：[EOD-TRANSFER-SUMMARY] date=X, rules=Y, success=Z, failed=W
```

### 4.5 余额快照生成

```
触发时机：日切全部流程完成后（含期末结转）

快照逻辑：
  1. 查询当日全部日余额记录：
     SELECT * FROM t_account_balance
       WHERE accounting_date = #{date}
         AND is_delete = 0

  2. 对每条日余额记录生成快照：
     snapshot_date = accounting_date
     snapshot_type = DAY(1)
     snapshot_time = NOW()
     subject_code = 日余额记录的 subject_code
     account_no = 日余额记录的 account_no
     currency = 日余额记录的 currency
     balance_direction = 日余额记录的 balance_direction
     balance = 日余额记录的 end_balance
     ext_json = JSON 格式的扩展信息（如当日借贷方发生额）
       {"debitAmount": 1000.00, "creditAmount": 800.00}

  3. 批量插入 t_account_balance_snapshot

  4. 月末日额外生成月快照（snapshot_type=MONTH）：
     如果 date 是月末最后一天（last day of month）：
       额外插入一条 snapshot_type=MONTH 的记录
```

### 4.6 日切总调度 Job 执行流程

```
XXL-JOB 定时触发（每日 23:55 执行，在 BufferPostingEodJob 23:50 之后 5 分钟）
  ↓
Step 1: 日切前置检查（EodCheckDomainService.checkEodPreconditions）
  ↓ 全部通过
Step 2: 日余额计算（EodDomainService.calculateDailyBalances）
  ↓
Step 3: 试算平衡（TrialBalanceDomainService.executeTrialBalance）
  ↓ 平衡通过
Step 4: 期末结转执行（PeriodEndTransferDomainService.executeTransferRules）
  ↓
Step 5: 余额快照生成（EodDomainService.generateDailySnapshot）
  ↓
Step 6: 完成日志
  [EOD-JOB] 执行完成: preCheck=PASS, balanceCalc=N accounts, trialBalance=PASS,
             transferRules=Y, transferSuccess=Z, snapshotGenerated=W

异常处理：
  - Step 1 失败 → 阻断 + 返回 EOD_BLOCKED
  - Step 3 失败 → 阻断 + 返回 TRIAL_BALANCE_FAILED
  - Step 4 单条规则失败 → 记录失败 + 继续后续规则
  - Step 5 失败 → 记录告警 + 不阻断（快照可事后补生成）

幂等性：
  - 日余额：upsert 语义，重复执行覆盖更新
  - 快照：唯一键 (snapshot_date, snapshot_type, account_no)，重复执行用 INSERT IGNORE
  - 结转：检查当日是否已有结转记录，已有则跳过
```

---

## 5. 接口契约

所有接口路径前缀：`/accounting/eod`

### 5.1 POST `/accounting/eod/execute` — 手动触发日切

**请求体** (`EodExecuteRequest`):
```json
{
  "accountingDate": "2026-06-12",
  "skipPreCheck": false,
  "executeTransfer": true
}
```

**校验规则**：
- `accountingDate`：必填，yyyy-MM-dd 格式
- `skipPreCheck`：选填，默认 false（true 则跳过前置检查，用于人工强制日切）
- `executeTransfer`：选填，默认 true（是否执行期末结转）

**响应** (`EodExecuteResponse`):
```json
{
  "accountingDate": "2026-06-12",
  "preCheckPassed": true,
  "preCheckDetails": {
    "bufferPending": 0,
    "processingTxn": 0,
    "unpostedVoucher": 0,
    "processingJournal": 0,
    "expiredFreeze": 0
  },
  "balanceCalculated": true,
  "balanceCount": 120,
  "trialBalancePassed": true,
  "trialBalanceDetails": {
    "totalDebit": 500000.00,
    "totalCredit": 500000.00,
    "diff": 0.00
  },
  "transferResults": [
    {
      "ruleCode": "PL_TRANSFER",
      "ruleName": "损益结转",
      "transferNo": "EODTR20260612001",
      "voucherNo": "V20260612000001",
      "totalAmount": 50000.00,
      "status": 2
    }
  ],
  "snapshotGenerated": true,
  "snapshotCount": 120,
  "totalDurationMs": 5200
}
```

### 5.2 GET `/accounting/eod/precheck` — 查询日切前置检查结果

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountingDate | String | 是 | 会计日期 yyyy-MM-dd |

**响应** (`EodPreCheckResponse`):
```json
{
  "accountingDate": "2026-06-12",
  "allPassed": true,
  "checks": [
    { "name": "bufferPending", "count": 0, "passed": true },
    { "name": "processingTxn", "count": 0, "passed": true },
    { "name": "unpostedVoucher", "count": 0, "passed": true },
    { "name": "processingJournal", "count": 0, "passed": true },
    { "name": "expiredFreeze", "count": 0, "passed": true }
  ]
}
```

### 5.3 GET `/accounting/eod/trial-balance` — 查询试算平衡结果

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountingDate | String | 是 | 会计日期 yyyy-MM-dd |

**响应** (`TrialBalanceResponse`):
```json
{
  "accountingDate": "2026-06-12",
  "passed": true,
  "totalDebit": 500000.00,
  "totalCredit": 500000.00,
  "diff": 0.00,
  "subjectDetails": [
    {
      "subjectCode": "1001",
      "subjectName": "库存现金",
      "totalDebit": 10000.00,
      "totalCredit": 8000.00,
      "netDiff": 2000.00
    }
  ],
  "imbalancedSubjects": []
}
```

---

## 6. 需要创建/修改的文件

### 新建

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── EodCheckDomainService.java              # 日切前置检查
    │       ├── TrialBalanceDomainService.java          # 试算平衡
    │       ├── EodDomainService.java                   # 日余额计算 + 快照生成（编排层）
    │       └── PeriodEndTransferDomainService.java     # 期末结转执行
    └── infrastructure/persistence/
        └── repository/
            ├── AccountBalanceRepository.java           # 日余额仓储
            ├── AccountBalanceSnapshotRepository.java   # 快照仓储
            ├── PeriodEndTransferRuleRepository.java    # 结转规则仓储
            └── PeriodEndTransferRecordRepository.java  # 结转记录仓储

accounting-job/
└── src/main/java/com/kltb/accounting/job/job/
    └── EodJobHandler.java                              # 日切总调度 Job

accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── EodExecuteRequest.java                      # 手动触发日切请求
    └── response/
        ├── EodExecuteResponse.java                     # 日切执行响应
        ├── EodPreCheckResponse.java                    # 前置检查响应
        └── TrialBalanceResponse.java                   # 试算平衡响应

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── service/
    │       └── EodApplicationService.java              # 应用层编排
    │   └── assembler/
    │       └── EodAssembler.java                       # DTO 转换器
    └── interfaces/
        └── EodController.java                          # Controller
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `AccountBalanceMapper.java` | 新增 batchUpsertBalance 方法 + XML |
| `AccountBalanceSnapshotMapper.java` | 新增 batchInsertSnapshot 方法 + XML |
| `PeriodEndTransferRuleMapper.java` | 新增 selectEnabledRules default 方法 |
| `PeriodEndTransferRecordMapper.java` | 新增 selectByAccountingDate default 方法 |
| `TransactionMapper.java` | 新增 countByAccountingDateAndStatus 方法 + XML |
| `AccountingVoucherEntryMapper.java` | 新增 sumEntriesBySubject 方法 + XML |
| `AccountingVoucherMapper.java` | 新增 countByAccountingDateAndStatus default 方法 |
| `ResultCode.java` | 新增错误码（见下方） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"2026"` | `EOD_PRECHECK_FAILED` | 日切前置检查失败（存在未处理项） |
| `"2027"` | `EOD_TRANSFER_FAILED` | 期末结转执行失败 |
| `"2028"` | `DAILY_BALANCE_NEGATIVE` | 日余额计算出现负值 |
| `"2029"` | `EOD_ALREADY_EXECUTED` | 日切已执行，不可重复执行 |

> 注：`EOD_BLOCKED(2011)` 和 `TRIAL_BALANCE_FAILED(2012)` 已在 ResultCode 中定义，可直接复用。

---

## 7. EodDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class EodDomainService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 计算日余额
     * <p>
     * 基于当日全部已过账分录，计算每个账户的期初/发生额/期末余额。
     *
     * @param accountingDate 会计日期
     * @return 日余额列表
     */
    public List<AccountBalancePO> calculateDailyBalances(LocalDate accountingDate);

    /**
     * 批量 Upsert 日余额
     */
    public void upsertDailyBalances(LocalDate accountingDate, List<AccountBalancePO> balances);

    /**
     * 生成日快照
     * <p>
     * 将当日末余额写入 t_account_balance_snapshot（snapshot_type=DAY）。
     * 如果是月末最后一天，额外生成月快照（snapshot_type=MONTH）。
     */
    public int generateDailySnapshot(LocalDate accountingDate);
}
```

## 8. EodCheckDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class EodCheckDomainService {

    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final TransactionRepository transactionRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final FreezeDetailRepository freezeDetailRepository;

    /**
     * 日切前置检查
     *
     * @param accountingDate 会计日期
     * @return 检查结果（包含各检查项的计数和是否通过）
     */
    public EodPreCheckResult checkEodPreconditions(LocalDate accountingDate);
}
```

## 9. TrialBalanceDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class TrialBalanceDomainService {

    private final AccountingVoucherRepository voucherRepository;
    private final SubjectRepository subjectRepository;

    private static final BigDecimal BALANCE_THRESHOLD = new BigDecimal("0.000001");

    /**
     * 执行试算平衡
     * <p>
     * 按科目汇总当日已过账分录的借贷方发生额，验证全局借贷平衡。
     *
     * @param accountingDate 会计日期
     * @return 试算平衡结果
     */
    public TrialBalanceResult executeTrialBalance(LocalDate accountingDate);
}
```

## 10. PeriodEndTransferDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class PeriodEndTransferDomainService {

    private final PeriodEndTransferRuleRepository ruleRepository;
    private final PeriodEndTransferRecordRepository recordRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行期末结转规则
     * <p>
     * 按 execute_order 升序逐条执行，单条失败不中断。
     *
     * @param accountingDate 会计日期
     * @return 每条规则的结转结果
     */
    public List<TransferRuleResult> executeTransferRules(LocalDate accountingDate);
}
```

---

## 11. 编码要点

### 11.1 事务边界

```
- 日余额计算：非事务性查询 + 批量 upsert（独立 TransactionTemplate 事务）
- 快照生成：非事务性查询 + 批量 insert（独立 TransactionTemplate 事务）
- 期末结转：每条规则独立 TransactionTemplate 事务（失败不中断）
- 日切总调度 Job：非事务性，各步骤独立事务
```

### 11.2 严禁负数运算

```java
// 日余额计算时校验
if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
    log.error("[EOD-BALANCE-NEGATIVE] accountNo={}, endBalance={}, " +
              "beginBalance={}, debitAmount={}, creditAmount={}",
        accountNo, endBalance, beginBalance, debitAmount, creditAmount);
    throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
        "日余额计算出现负值: accountNo=" + accountNo);
}
```

### 11.3 通配符解析

```java
// source_subject_code 通配符 → SQL LIKE 模式
private String wildcardToLikePattern(String wildcard) {
    return wildcard.replace("*", "%");
}
// 例："6*" → "6%"，"601*" → "601%"
```

### 11.4 结转流水号生成

```java
// 格式：EODTR{yyyyMMdd}{4位序号}
public String generateTransferNo(LocalDate date, int seq) {
    String dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE);
    return String.format("EODTR%s%04d", dateStr, seq);
}
```

### 11.5 幂等性控制

```
- 日切前置检查：纯查询，天然幂等
- 日余额计算：upsert 语义，重复执行覆盖更新
- 试算平衡：纯查询，天然幂等
- 期末结转：检查当日是否已有该规则的结转记录
    SELECT COUNT(*) FROM t_period_end_transfer_record
      WHERE accounting_date = #{date}
        AND rule_code = #{ruleCode}
        AND status = 2
    > 0 → 跳过该规则
- 快照生成：INSERT IGNORE（唯一键冲突时跳过）
```

### 11.6 月末判断

```java
private boolean isMonthEnd(LocalDate date) {
    return date.getDayOfMonth() == date.lengthOfMonth();
}
```

---

## 12. 完成标准（Checklist）

> **对齐日期**：2026-06-24，代码与文档逐项核对后打勾。

### P0 前置任务
- [x] P0-1: AccountBalanceMapper.batchUpsertBalance + XML（INSERT ON DUPLICATE KEY UPDATE）
- [x] P0-2: AccountBalanceSnapshotMapper.batchInsertSnapshot + XML
- [x] P0-3: PeriodEndTransferRuleMapper.selectEnabledRules（LambdaQueryWrapper）
- [x] P0-4: PeriodEndTransferRecordMapper.selectByAccountingDate（LambdaQueryWrapper）
- [x] P0-5: TransactionMapper.countByAccountingDateAndStatus + XML
- [x] P0-6: AccountingVoucherEntryMapper.sumEntriesBySubject + XML
- [x] P0-7: AccountingVoucherMapper.countByAccountingDateAndStatus（LambdaQueryWrapper）
- [x] P0-8: DDL 确认（t_account_balance / t_account_balance_snapshot / transfer_rule / transfer_record）

### Agent-B：日切检查 + 试算平衡
- [x] EodCheckDomainService.checkEodPreconditions 实现 5 项检查
- [x] 检查项全部通过才返回 allPassed=true
- [x] TrialBalanceDomainService.executeTrialBalance 按科目汇总借贷
- [x] 差额阈值 0.000001 判定
- [x] 不平衡时返回差异明细列表

### Agent-C：日余额计算 + 快照生成
- [x] EodDomainService.calculateDailyBalances 计算逻辑正确
- [x] 期初余额取前一日 end_balance（或 opening_balance 兜底）
- [x] 借贷发生额按已过账分录汇总
- [x] 期末余额按余额方向正确计算
- [x] 负数余额检测并告警
- [x] batchUpsertBalance 批量写入 t_account_balance
- [x] generateDailySnapshot 生成日快照
- [x] 月末日额外生成月快照

### Agent-D：期末结转执行
- [x] PeriodEndTransferDomainService.executeTransferRules 按 execute_order 升序执行
- [x] 通配符解析正确（6* → 6%）
- [x] 结转方向正确（DEBIT_TO_CREDIT / CREDIT_TO_DEBIT）
- [x] 结转凭证生成（结账凭证类型、机制凭证、摘要模板渲染）
- [x] 结转记录写入 t_period_end_transfer_record
- [x] 单条规则失败不中断后续规则
- [x] 幂等控制（当日已有结转记录则跳过）

### Agent-E：日切总调度 + 接口
- [x] EodJobHandler 编排 6 步流程（23:55 执行）
- [x] EodApplicationService 编排手动触发用例
- [x] EodController 实现 3 个接口
- [x] DTO 完整（Request + Response + Assembler）
- [x] 幂等性控制（日切不可重复执行，除非 skipPreCheck=true）

### TL Review
- [x] 日余额计算正确（期初 + 借方 - 贷方 = 期末，方向判断正确）
- [x] 试算平衡准确（已过账分录借贷合计相等）
- [x] 期末结转凭证生成正确（借贷方向、金额、摘要）
- [x] 事务边界正确（TransactionTemplate，非 @Transactional）
- [x] 幂等性控制有效（重复执行不产生重复数据）
- [x] Job 编排顺序正确（前置检查 → 余额 → 试算 → 结转 → 快照）
- [x] 异常处理正确（单步失败不阻断后续步骤，除非关键检查）
- [x] 错误码使用正确（无魔法数字）

---

## 13. 与前后 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 16（缓冲记账） | BufferPostingEodJobHandler 在本 Step 的 EodJobHandler 之前 5 分钟执行；日切前置检查需确认缓冲明细全部入账 |
| Step 18（冲账与红冲） | 红冲凭证参与试算平衡；日切后不允许当日新增红冲凭证 |
| Step 23（前端日切页面） | 依赖本 Step 的日切执行、前置检查、试算平衡接口 |

---

## 14. 下一步行动

进入 **Step 18 · Reversal & Red Offset（冲账与红冲）**，详见 `docs/prompt/step-18-reversal.md`。
