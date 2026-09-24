# step-17-supplement · 日切与试算平衡（补充任务）

> **Phase 6 补充** | 归属：`@Java` 工程师
>
> 前置依赖：Step 17（日切与试算平衡 · 主体）已完成
>
> **背景**：原始设计文档 `docs/design/flowchart/eod_five_phases.mmd` 和 `docs/ai-rules/accounting.md` 定义了日终五阶段流程，但 Step 17 只实现了阶段 2 ~ 4.5。本 Step 补齐遗漏的 **阶段 1（瞬间切日）、阶段 5（归档）、总分核对、余额核对** 以及 **日切状态追踪**。

---

## 0. Gap 对照表

| 规范来源 | 阶段 1 瞬间切日 | 阶段 2 存量清理 | 阶段 3 余额快照 | 阶段 4 试算平衡 | 阶段 4.5 期末结转 | 阶段 5 归档 |
|---------|---------------|---------------|---------------|---------------|----------------|-----------|
| `accounting.md` | ✅ 已定义 | ✅ | ✅ | ✅ | ✅ | ✅ |
| `eod_five_phases.mmd` | ✅ 有流程图 | ✅ | ✅ | ✅ | - | ✅ |
| Step 17 任务文档 | ❌ 未纳入 | ✅ | ✅ | ⚠️ 只有借贷平衡 | ✅ | ❌ 未纳入 |
| 实际代码 | ❌ | ✅ | ✅ | ⚠️ 缺总分核对/余额核对 | ✅ | ❌ |

### 本 Step 需要补齐

| # | 缺失功能 | 规范来源 | 说明 |
|---|---------|---------|------|
| SUP-1 | 全局会计日期缓存 | `accounting.md` 阶段1 + `eod_five_phases.mmd` P1_UPDATE/P1_CONFIG | Redis 缓存 + DB 持久化 |
| SUP-2 | 瞬间切日逻辑 | `accounting.md` 阶段1 + `eod_five_phases.mmd` P1_EFFECT | T → T+1 切换 |
| SUP-3 | 日切状态表 DDL | 本 Step 新增 | 追踪日切各阶段状态 |
| SUP-4 | 日切状态持久化 | `eod_five_phases.mmd` P5_MARK | PO/Mapper/XML/Repository |
| SUP-5 | 日切状态查询 | 本 Step 新增 | API 查询日切进度 |
| SUP-6 | 总分核对 | `accounting.md` 阶段4-② | 科目总账 vs 分户余额合计 |
| SUP-7 | 余额核对 | `accounting.md` 阶段4-③ | 账户余额 vs 明细最后一条 post_balance |
| SUP-8 | 归档逻辑 | `accounting.md` 阶段5 + `eod_five_phases.mmd` P5_MARK/P5_TIME | 标记 T 日账务关闭 |
| SUP-9 | 日切总调度流程更新 | 本 Step | 将阶段 1 和阶段 5 编入 EodJobHandler |
| SUP-10 | JournalingDomainService 集成 | `accounting.md` 第四条 | 从缓存读取会计日期而非 tradeTime.toLocalDate() |

---

## 1. 前置补充任务（开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | 新增 `t_eod_status` 表 DDL | `docs/sql/6-infra.sql` | 日切状态追踪表（需 DBA 评审后加入） |
| P0-2 | `EodStatusMapper` | 新建 Mapper + XML | 日切状态 CRUD |
| P0-3 | `EodStatusRepository` | 新建 Repository | 仓储层封装 |
| P0-4 | `AccountingDateCache` | 新建 Redis 缓存组件 | 全局会计日期 Redis 缓存 |
| P0-5 | 新增错误码 | `ResultCode.java` | 新增 2030~2034 错误码 |
| P0-6 | `AccountBalanceMapper` 补充按科目汇总方法 | Mapper + XML | 总分核对用 |

### P0-1: `t_eod_status` 表 DDL

```sql
-- 日切状态表（追踪每日日切执行状态）
CREATE TABLE t_eod_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    accounting_date DATE NOT NULL COMMENT '会计日期（T 日）',
    eod_status TINYINT NOT NULL DEFAULT 1 COMMENT '日切状态：1-未开始,2-切日中,3-清理中,4-快照中,5-试算中,6-结转中,7-归档中,8-完成,9-失败',
    switch_date_time DATETIME COMMENT '切日完成时间',
    archive_date_time DATETIME COMMENT '归档完成时间',
    failed_stage VARCHAR(32) NOT NULL DEFAULT '' COMMENT '失败阶段',
    fail_reason VARCHAR(255) NOT NULL DEFAULT '' COMMENT '失败原因',
    total_duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_delete BIGINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标识',
    tenant_id INT NOT NULL DEFAULT -1 COMMENT '租户ID',
    UNIQUE KEY uk_eod_date (accounting_date, is_delete),
    KEY idx_eod_status (eod_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日切状态表';
```

### P0-5: 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"2030"` | `DATE_SWITCH_FAILED` | 全局会计日期切换失败 |
| `"2031"` | `EOD_ARCHIVE_FAILED` | 日切归档失败 |
| `"2032"` | `GL_SUB_ACCOUNT_MISMATCH` | 总分核对失败（总账 != 分户合计） |
| `"2033"` | `ACCOUNT_DETAIL_MISMATCH` | 余额核对失败（账户余额 != 明细 post_balance） |
| `"2034"` | `EOD_STATUS_NOT_FOUND` | 日切状态记录不存在 |

### P0-6: AccountBalanceMapper 按科目汇总

```java
/**
 * 按科目汇总日余额（总分核对用）
 */
List<Map<String, Object>> sumBalancesBySubject(
    @Param("accountingDate") LocalDate accountingDate);
```

XML：

```xml
<select id="sumBalancesBySubject" resultType="java.util.HashMap">
    SELECT subject_code,
           SUM(end_balance) AS total_balance,
           SUM(debit_amount) AS total_debit,
           SUM(credit_amount) AS total_credit,
           COUNT(*) AS account_count
    FROM t_account_balance
    WHERE accounting_date = #{accountingDate}
      AND is_delete = 0
    GROUP BY subject_code
</select>
```

---

## 2. 任务分配与子 Agent 编排

```
Phase 1（串行）：P0 前置补充任务
  └─ Agent-A：完成 P0-1~P0-6（DDL/Mapper/XML/Repository/缓存组件）

Phase 2（可并行）：核心业务逻辑
  ├─ Agent-B：AccountingDateSwitchDomainService（瞬间切日）+ AccountingDateCache（Redis 缓存）
  ├─ Agent-C：TrialBalanceDomainService 增强（总分核对 + 余额核对）
  └─ Agent-D：EodArchiveDomainService（归档）+ EodStatus 持久化

Phase 3（依赖 Phase 2）：集成与 Job 编排
  └─ Agent-E：JournalingDomainService 集成 + EodJobHandler 流程更新 + 新 API
```

> **执行顺序**：Agent-A → (Agent-B + Agent-C + Agent-D 并行) → Agent-E

---

## 3. 核心业务规则

### 3.1 全局会计日期缓存（SUP-1 + SUP-2）

```
存储策略：
  - 首次启动：从 t_eod_status 中最近一条 eod_status=8（完成）记录的 accounting_date + 1 天
              若无记录，使用当前系统日期
  - 缓存：Redis key = "accounting:date:current"
  - 每次日切（阶段 1）：更新 Redis + 写入 t_eod_status 新记录

读取策略：
  - 所有新交易请求：先读 Redis，无则查 DB 兜底
  - 缓存 TTL：24 小时（每次切日刷新 TTL）
  - 多实例一致性：切日时发布 Redis Pub/Sub 通知所有实例刷新

切日流程：
  1. 计算新日期 = 当前会计日期 + 1 天
  2. 更新 Redis: SET "accounting:date:current" = newDate（带版本号 CAS）
  3. 写入 t_eod_status 新记录（eod_status=2 切日中 → 8 完成）
  4. 发布 Pub/Sub 通知: CHANNEL = "accounting:date:notify", MESSAGE = newDate
  5. 所有实例收到通知后刷新本地 Caffeine 缓存
```

### 3.2 日切状态追踪（SUP-4 + SUP-5）

```
状态机：
  1(未开始) → 2(切日中) → 3(清理中) → 4(快照中) → 5(试算中) → 6(结转中) → 7(归档中) → 8(完成)
   ↓ 任一阶段失败
  9(失败)

状态更新：
  - 每个阶段开始前更新为对应"进行中"状态
  - 阶段成功完成后不更新状态（状态只记录阶段进度）
  - 全部完成时标记 8(完成)
  - 任一阶段失败时标记 9(失败) + 记录失败阶段和原因

查询接口：
  GET /accounting/eod/status?accountingDate=YYYY-MM-DD
  返回当日日切状态 + 各阶段完成时间
```

### 3.3 总分核对（SUP-6）

```
触发时机：借贷平衡通过后

核对逻辑：
  1. 按科目汇总 t_account_balance 中的余额：
     SELECT subject_code, SUM(end_balance) AS total_balance
       FROM t_account_balance
       WHERE accounting_date = #{date} AND is_delete = 0
       GROUP BY subject_code

  2. 按科目汇总 t_account 中的余额（分户）：
     SELECT a.subject_code, SUM(a.balance) AS total_balance
       FROM t_account a
       WHERE a.status = 1 AND a.is_delete = 0
       GROUP BY a.subject_code

  3. 逐科目对比：
     |balance_from_balance - balance_from_account| <= 0.000001 → 通过
     否则 → 记录差异明细

  4. 全局汇总差异：
     total_diff = SUM(|diff|) across all subjects
     total_diff <= 0.000001 → 总分核对通过

  失败处理：
    - 记录每个差异科目的明细
    - 阻断后续归档流程
    - 返回 GL_SUB_ACCOUNT_MISMATCH 错误码
```

### 3.4 余额核对（SUP-7）

```
触发时机：总分核对通过后

核对逻辑：
  1. 查询当日全部 t_account_balance 记录
  2. 对每条记录，查询 t_account 中的实际余额：
     SELECT account_no, balance FROM t_account WHERE account_no = #{accountNo}

  3. 对比：
     |account_balance.end_balance - account.balance| <= 0.000001 → 通过
     否则 → 记录差异

  4. 对 t_account_balance 中的每条记录，验证余额计算：
     取 t_account_detail 中该账户最后一条明细的 post_balance：
     SELECT post_balance FROM t_account_detail
       WHERE account_no = #{accountNo}
       AND accounting_date = #{date}
       ORDER BY id DESC LIMIT 1

     验证：post_balance_N == end_balance
     不等 → 触发告警

  失败处理：
    - 记录差异明细
    - 阻断归档
    - 返回 ACCOUNT_DETAIL_MISMATCH 错误码
```

### 3.5 归档逻辑（SUP-8）

```
触发时机：全部核对通过后

归档操作：
  1. 更新 t_eod_status：
     UPDATE t_eod_status SET
       eod_status = 8,
       archive_date_time = NOW(),
       total_duration_ms = #{duration},
       update_time = NOW()
     WHERE accounting_date = #{date} AND is_delete = 0

  2. 日志记录：
     [EOD-ARCHIVE] date=X, status=COMPLETED, duration=Yms

  3. 通知外围系统（预留 MQ 消息，后续 Step 实现）：
     - topic: "eod.completed"
     - payload: { accountingDate, completedAt }

  失败处理：
    - 更新 t_eod_status 为 9(失败)
    - 记录失败原因
    - 返回 EOD_ARCHIVE_FAILED 错误码
```

### 3.6 日切总调度 Job 更新（SUP-9）

```
EodJobHandler (23:55) → EodApplicationService
  ├── Step 0: AccountingDateSwitchDomainService.switchDate     — 瞬间切日（NEW）
  ├── Step 1: EodCheckDomainService.checkEodPreconditions      — 存量清理（5项检查）
  ├── Step 2: EodDomainService.calculateDailyBalances          — 日余额计算
  ├── Step 3: EodDomainService.upsertDailyBalances             — 写入日余额
  ├── Step 4: TrialBalanceDomainService.executeTrialBalance    — 借贷平衡
  ├── Step 4b: TrialBalanceDomainService.executeGlReconciliation — 总分核对（NEW）
  ├── Step 4c: TrialBalanceDomainService.executeBalanceReconciliation — 余额核对（NEW）
  ├── Step 5: PeriodEndTransferDomainService.executeTransferRules — 期末结转
  ├── Step 6: EodDomainService.generateDailySnapshot           — 日/月快照
  └── Step 7: EodArchiveDomainService.archive                  — 归档（NEW）

状态更新：
  每个步骤执行前后更新 t_eod_status 对应阶段状态
```

### 3.7 JournalingDomainService 集成（SUP-10）

```
修改前：
  public LocalDate determineAccountingDate(LocalDateTime tradeTime) {
      return tradeTime.toLocalDate();
  }

修改后：
  public LocalDate determineAccountingDate(LocalDateTime tradeTime) {
      return accountingDateCache.getCurrentDate();
  }

说明：
  - 会计日期不再由交易时间决定，而是从全局缓存读取
  - tradeTime 参数保留（用于其他业务逻辑），但不再作为会计日期来源
  - 符合规范：会计日期在业务流水入库时确定（此时已由缓存提供）
```

---

## 4. 需要创建/修改的文件

### 新建

```
accounting-core/
├── src/main/java/com/kltb/accounting/core/
│   ├── domain/service/
│   │   ├── AccountingDateSwitchDomainService.java      # 瞬间切日领域服务
│   │   ├── EodArchiveDomainService.java                # 归档领域服务
│   │   └── EodStatusDomainService.java                 # 日切状态追踪领域服务
│   ├── infrastructure/
│   │   ├── cache/
│   │   │   └── AccountingDateCache.java                # Redis + Caffeine 二级缓存
│   │   └── persistence/
│   │       ├── entity/
│   │       │   └── EodStatusPO.java                    # 日切状态 PO
│   │       ├── mapper/
│   │       │   └── EodStatusMapper.java                # 日切状态 Mapper
│   │       └── repository/
│   │           └── EodStatusRepository.java            # 日切状态仓储
│   └── interfaces/
│       └── EodStatusController.java                    # 日切状态查询接口

accounting-api/
├── src/main/java/com/kltb/accounting/api/
│   └── response/
│       └── EodStatusResponse.java                      # 日切状态响应 DTO
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `docs/sql/6-infra.sql` | 新增 `t_eod_status` 表 DDL |
| `ResultCode.java` | 新增 2030~2034 错误码 |
| `AccountBalanceMapper.java` | 新增 `sumBalancesBySubject` 方法 |
| `AccountBalanceMapper.xml` | 新增按科目汇总 SQL |
| `TrialBalanceDomainService.java` | 新增总分核对 + 余额核对方法 |
| `AccountingVoucherRepository.java` | 新增按科目查询账户余额方法 |
| `JournalingDomainService.java` | `determineAccountingDate` 改用缓存 |
| `EodApplicationService.java` | 编排新增的步骤（切日、核对、归档） |
| `EodAssembler.java` | 新增 DTO 转换方法 |
| `EodJobHandler.java` | 编排 8 步流程（新增阶段 1 和阶段 5） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"2030"` | `DATE_SWITCH_FAILED` | 全局会计日期切换失败 |
| `"2031"` | `EOD_ARCHIVE_FAILED` | 日切归档失败 |
| `"2032"` | `GL_SUB_ACCOUNT_MISMATCH` | 总分核对失败 |
| `"2033"` | `ACCOUNT_DETAIL_MISMATCH` | 余额核对失败 |
| `"2034"` | `EOD_STATUS_NOT_FOUND` | 日切状态记录不存在 |

---

## 5. 接口契约

### 5.1 GET `/accounting/eod/status` — 查询日切状态

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountingDate | String | 是 | 会计日期 yyyy-MM-dd |

**响应** (`EodStatusResponse`):
```json
{
  "accountingDate": "2026-06-12",
  "status": "COMPLETED",
  "statusDesc": "已完成",
  "switchDateTime": "2026-06-12T23:55:01",
  "archiveDateTime": "2026-06-13T00:03:45",
  "failedStage": "",
  "failReason": "",
  "totalDurationMs": 512000
}
```

### 5.2 POST `/accounting/eod/switch-date` — 手动触发切日

**请求体** (`DateSwitchRequest`):
```json
{
  "targetDate": "2026-06-13"
}
```

**响应** (`DateSwitchResponse`):
```json
{
  "previousDate": "2026-06-12",
  "newDate": "2026-06-13",
  "switchedAt": "2026-06-12T23:55:01"
}
```

---

## 6. 编码要点

### 6.1 AccountingDateCache 二级缓存架构

```java
@Component
public class AccountingDateCache {

    // L1: Caffeine 本地缓存（单实例内共享）
    private final LoadingCache<String, LocalDate> localCache;

    // L2: Redis 分布式缓存（多实例共享）
    private final RedissonClient redissonClient;
    private static final String REDIS_KEY = "accounting:date:current";
    private static final String PUBSUB_CHANNEL = "accounting:date:notify";

    // 读取：L1 → L2 → DB
    // 写入：DB → L2 → Pub/Sub → 刷新所有实例 L1
}
```

### 6.2 事务边界

```
- 瞬间切日：独立 TransactionTemplate 事务（写 DB + 更新 Redis）
- 日切状态追踪：每个阶段更新独立事务
- 归档：独立 TransactionTemplate 事务
- 总分核对/余额核对：纯查询，无事务
```

### 6.3 事务规范

- 严禁使用 `@Transactional`，全部使用 `TransactionTemplate`
- Redis 操作失败时回滚数据库事务

### 6.4 幂等性

```
- 切日：检查是否已对目标日期执行过切日（通过 t_eod_status 判断）
- 归档：检查 eod_status 是否已是 8(完成)，是则跳过
- 状态更新：CAS 乐观锁更新（version 字段）
```

---

## 7. 完成标准（Checklist）

### P0 前置任务
- [ ] P0-1: `t_eod_status` DDL 写入 `docs/sql/6-infra.sql`
- [ ] P0-2: `EodStatusMapper` + XML 完成
- [ ] P0-3: `EodStatusRepository` 完成
- [ ] P0-4: `AccountingDateCache` 二级缓存完成（Redis + Caffeine）
- [ ] P0-5: `ResultCode` 新增 2030~2034 错误码
- [ ] P0-6: `AccountBalanceMapper.sumBalancesBySubject` + XML 完成

### Agent-B：瞬间切日
- [ ] `AccountingDateSwitchDomainService` 完成
- [ ] 切日流程：计算新日期 → 更新 Redis → 写入 DB → Pub/Sub 通知
- [ ] 首次启动兜底：无历史记录时使用系统日期
- [ ] 幂等控制：同目标日期不重复切日
- [ ] `JournalingDomainService.determineAccountingDate` 改用缓存

### Agent-C：总分核对 + 余额核对
- [ ] `TrialBalanceDomainService.executeGlReconciliation` 完成
- [ ] 按科目汇总 t_account_balance vs t_account 余额
- [ ] 差异阈值 0.000001
- [ ] 不平衡时返回差异明细列表
- [ ] `TrialBalanceDomainService.executeBalanceReconciliation` 完成
- [ ] 账户余额 vs 明细最后一条 post_balance 核对
- [ ] 不等时返回差异明细

### Agent-D：归档 + 状态追踪
- [ ] `EodArchiveDomainService` 完成
- [ ] 归档流程：更新 t_eod_status → 记录完成时间 → 日志
- [ ] `EodStatusDomainService` 完成各阶段状态更新
- [ ] 每个阶段开始前/完成后正确更新状态

### Agent-E：集成与 Job 编排
- [ ] `EodApplicationService.executeEod` 编排 8 步流程
- [ ] `EodJobHandler` 更新为 8 步流程
- [ ] `EodStatusController` 实现状态查询接口
- [ ] 新增 DTO（EodStatusResponse）
- [ ] `EodAssembler` 新增转换方法

### TL Review
- [ ] 切日逻辑正确（Redis + DB 一致性）
- [ ] 总分核对准确（科目汇总 vs 账户余额）
- [ ] 余额核对准确（账户余额 vs 明细 post_balance）
- [ ] 事务边界正确（TransactionTemplate，非 @Transactional）
- [ ] 幂等性有效（重复切日/归档不产生重复数据）
- [ ] 状态追踪完整（各阶段状态正确更新）
- [ ] Job 编排顺序正确（切日 → 清理 → 余额 → 借贷 → 总分 → 余额核对 → 结转 → 快照 → 归档）
- [ ] 错误码使用正确（无魔法数字）

---

## 8. 与前后 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 17（主体） | 复用已有的 DomainService（EodCheckDomainService、EodDomainService 等），在此基础上新增步骤 |
| Step 18（冲账与红冲） | 无直接依赖，但红冲凭证也参与总分/余额核对 |
| Step 23（前端日切页面） | 依赖本 Step 的日切状态查询接口 |

---

## 9. 下一步行动

进入 **Step 18 · Reversal & Red Offset（冲账与红冲）**，详见 `docs/prompt/step-18-reversal.md`。
