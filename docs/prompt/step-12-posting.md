# step-12-posting · 过账引擎

> **Phase 4 第五步（⚠️ 最高风险模块）** | 归属：`@Java` 工程师
> 前置依赖：Step 11（事务管理已完成 — 实时/异步过账、回滚、MQ消费端逻辑已就绪）、Step 4（XXL-JOB + LocalMessageService 已就绪）

---

## 0. 前置补充任务（Java-A 开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `AccountingVoucherMapper` 补充批量查询方法 | `AccountingVoucherMapper.java`（已有，需补充） | 补充 `selectByStatusAndDateRange`（按凭证状态+会计日期范围查询） |
| P0-2 | `AccountingVoucherRepository` 补充批量查询方法 | `AccountingVoucherRepository.java`（已有，需补充） | 封装 P0-1 的 Repository 层方法 |
| P0-3 | `TransactionMapper` 补充统计方法 | `TransactionMapper.java`（已有，需补充） | 补充 `countByStatusAndDateRange`（按事务状态+日期范围统计） |
| P0-4 | `TransactionRepository` 补充统计方法 | `TransactionRepository.java`（已有，需补充） | 封装 P0-3 的 Repository 层方法 |
| P0-5 | 确认 XXL-JOB Handler 注册可用 | 读取 `accounting-job` 模块已有 Handler 模式 | 用于本地消息扫描 Job、批量过账 Job |
| P0-6 | `LocalMessageService` 补充查询方法 | `LocalMessageService.java`（已有，需补充） | 补充 `selectPendingMessages`（按状态+重试次数查询待发送消息） |
| P0-7 | `LocalMessageMapper` 补充查询方法 | `LocalMessageMapper.java`（已有，需补充） | 补充 `selectByStatusAndRetryLimit`（status=1 且 retry_count < max_retry 且 next_retry_time <= now） |
| P0-8 | DDL 列补充 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` 新增 `fail_reason` VARCHAR(500)、`retry_count` INT DEFAULT 0、`skip_flag` TINYINT DEFAULT 0 |

> 上述补充任务由 Java-A 在开始编码前一并完成，不单独拆分子任务文件。

**P0-8 DDL 变更说明**：
```sql
-- t_accounting_voucher 新增列
ALTER TABLE t_accounting_voucher ADD COLUMN fail_reason VARCHAR(500) DEFAULT NULL COMMENT '过账失败原因';
ALTER TABLE t_accounting_voucher ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '手动重试次数';
ALTER TABLE t_accounting_voucher ADD COLUMN skip_flag TINYINT NOT NULL DEFAULT 0 COMMENT '跳过标记：0-未跳过,1-人工跳过';
-- 同时更新对应的 AccountingVoucherPO.java 实体类属性
```

> 凭证状态复用现有 status=4(过账失败)，通过 skip_flag 区分"系统判定失败"和"人工跳过"。

---

## 1. 任务目标（Mission）

实现过账引擎调度与运营能力，作为记账核心引擎的**第四阶段**，承接 Step 11 的单笔过账能力，提供批量过账、定时调度、进度监控与异常治理。

核心职责：
1. **本地消息扫描 Job**：定时扫描 `t_local_message`（status=1 待发送）→ 投递 RocketMQ → 更新状态
2. **批量过账执行**：按会计日期/凭证状态批量查询待过账凭证 → 逐笔调用 PostingApplicationService → 统计结果
3. **过账进度监控**：提供凭证/事务过账进度查询接口（已处理/总数/成功率/失败明细）
4. **异常凭证治理**：查询过账失败/处理中的凭证 → 支持手动重试/标记跳过
5. **过账统计报表**：按日期/业务线/交易码维度统计过账量、成功率、平均耗时
6. **重试退避调度**：本地消息失败重试的指数退避调度（10s → 30s → 60s → 告警）

**过账引擎是 Step 11 单笔过账能力的上层封装，不重复实现余额计算/加锁/回滚等核心逻辑，而是调度、编排、监控这些已有能力。**

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、借贷方向约束、余额计算规则 |
| 3 | `docs/design/domain-model.md` | 账户域、凭证域、流水域模型 |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` / `t_business_record` DDL |
| 6 | `docs/sql/6-infra.sql` | `t_local_message` / `t_message_receipt` DDL |
| 7 | `docs/design/flowchart/standard_posting_flow_detailed.mmd` | 标准入账全流程（四阶段） |
| 8 | `docs/design/flowchart/transaction_rollback_flow.mmd` | 事务回滚流程图 |
| 9 | `accounting-core/.../domain/service/PostingDomainService.java` | Step 11 实时过账领域服务 |
| 10 | `accounting-core/.../application/service/PostingApplicationService.java` | Step 11 过账编排应用服务 |
| 11 | `accounting-core/.../infrastructure/messaging/LocalMessageService.java` | 本地消息服务 |
| 12 | `accounting-core/.../infrastructure/messaging/PostingMessageConsumer.java` | MQ 消费端 |

---

## 3. 任务分配

| 工程师 | 详细文件 | 负责内容 |
|--------|---------|---------|
| Java-A | `docs/prompt/tasks/step-12-java-a.md` | P0 补充 + LocalMessageScanJob（XXL-JOB Handler）+ 消息投递 + 重试退避 + PostingEngineDomainService（批量过账核心逻辑） |
| Java-B | `docs/prompt/tasks/step-12-java-b.md` | PostingMonitorDomainService（过账进度监控 + 异常凭证治理 + 统计报表）+ 异常重试/跳过逻辑 |
| Java-C | `docs/prompt/tasks/step-12-java-c.md` | PostingEngineApplicationService（批量过账编排 + 进度查询编排）+ PostingEngineController（7 个接口）+ DTO + Assembler |

> **依赖关系**：Java-A 最先完成（本地消息扫描 Job 和批量过账核心逻辑是基础设施）。Java-B 依赖 Java-A 的批量过账结果，做监控和统计。Java-C 依赖 Java-A/B 的领域服务，做上层编排和对外接口。建议串行执行：A → B → C。

---

## 4. 核心业务规则

### 4.1 本地消息扫描 Job 流程

```
XXL-JOB 定时触发（默认每 30 秒执行一次）
  ↓
【查询层】SELECT * FROM t_local_message
           WHERE status = 1(待发送)
             AND retry_count < max_retry
             AND next_retry_time <= NOW()
           ORDER BY create_time ASC
           LIMIT 100（每批次最大处理量，可配置）
  ↓
【投递层】逐条消息调用 RocketMQ Producer 发送
  ↓ 发送成功
     → 更新 t_local_message.status = 2(已发送)
     → 更新 send_time = NOW()
     → 写入 t_message_receipt（消费成功，等待 MQ 消费端回写实际结果）
  ↓ 发送失败
     → retry_count += 1
     → 计算下次重试时间（指数退避：10s → 30s → 60s）
     → 更新 next_retry_time
     → retry_count >= max_retry → status = 3(发送失败)
     → 触发告警（邮件/短信/监控平台）
  ↓
【统计层】返回处理结果（总数 / 成功 / 失败 / 待重试）
```

### 4.2 批量过账执行流程

```
触发方式（两种）：
  - 手动触发：前端页面 / API 调用，传入会计日期范围
  - 定时触发：XXL-JOB 定时任务，默认每 5 分钟执行一次

【查询层】按条件查询待过账凭证：
           SELECT * FROM t_accounting_voucher
           WHERE status = 1(未过账)
             AND accounting_date BETWEEN ? AND ?
             AND posting_type IN ('REALTIME', 'ASYNC')  -- 排除缓冲记账
           ORDER BY create_time ASC
           LIMIT 50（每批次最大处理量，可配置）
  ↓
【编排层】逐笔凭证调用 PostingApplicationService.executePosting()
  ↓ 单笔成功 → 成功计数 +1
  ↓ 单笔失败 → 失败计数 +1，记录失败原因（不中断其他凭证）
  ↓
【统计层】返回批量过账结果：
           - 总数 / 成功 / 失败
           - 失败凭证列表（voucherNo + failReason）
           - 总耗时

> P2-11 说明：当前阶段依赖凭证状态从 1(未过账) → 3(已过账) 来天然过滤已处理凭证，
  无需游标。若过账中途失败（status=2 过账中），下一批查询会跳过该凭证（因 status!=1）。
  数据量增长后（>1000 条/批次）可引入游标分页。
```

### 4.3 过账进度监控规则

```
监控维度（三种）：
  1. 凭证维度：按 voucherNo 查询
     - 凭证基本信息 + 分录过账状态列表
     - 实时分录已处理数 / 总数
     - 异步分录已处理数 / 总数
     - 缓冲分录已处理数 / 总数（Step 16 更新）

  2. 事务维度：按 txnNo 查询
     - 事务基本信息 + 关联凭证过账进度
     - 关联凭证数 / 已过账凭证数
     - 事务状态（PROCESSING / SUCCESS / FAILED）

  3. 批次维度：按会计日期范围查询
     - 该日期范围内所有凭证的过账汇总
     - 总凭证数 / 已过账 / 过账中 / 过账失败
     - 过账成功率 = 已过账 / 总凭证数
     - 平均耗时（仅统计已过账凭证）

进度计算公式：
  进度 = (实时已过账 + 异步已过账) / (实时总数 + 异步总数) × 100%
  注：缓冲分录不计入进度（由 Step 16 单独处理）
```

### 4.4 异常凭证治理规则

```
异常凭证定义：
  - status = 4(过账失败) 的凭证
  - status = 2(过账中) 且超过 1 小时未完成的凭证（僵尸凭证）

治理操作（两种）：
  1. 手动重试：
     - 按 voucherNo 调用 PostingApplicationService.executePosting()
     - 限制：最多重试 5 次（含系统自动重试）
     - 每次重试记录操作人和原因

  2. 标记跳过：
     - 将凭证 status 保持为 4(过账失败)，设置 skip_flag = 1
     - 记录跳过原因和操作人
     - 触发告警（跳过意味着数据不一致，需要人工介入）
     - 注意：跳过操作需二级确认（由前端页面弹窗确认，后端 skip_reason 非空即视为已确认）

⚠️ 僵尸凭证自动检测：
   - Job 每 10 分钟扫描一次 status=2 且 update_time < NOW() - 1h 的凭证
   - 自动标记为 4(过账失败)，记录原因"过账超时"
   - 触发告警
```

### 4.5 过账统计报表规则

```
统计维度（四种）：
  1. 按日期：查询某一天的过账总量、成功率、平均耗时
  2. 按业务线：按 businessCode 分组统计
  3. 按交易码：按 tradingCode 分组统计
  4. 按支付渠道：按 payChannel 分组统计

统计指标：
  - total_count: 总凭证数
  - success_count: 成功过账数（status=3）
  - failed_count: 过账失败数（status=4）
  - processing_count: 过账中数（status=2）
  - success_rate: 成功率 = success_count / total_count
  - avg_duration_ms: 平均耗时（毫秒，仅统计已过账凭证）
  - max_duration_ms: 最大耗时
  - min_duration_ms: 最小耗时

统计数据来源：
  - 凭证表 t_accounting_voucher（按 status 分组计数）
  - 事务表 t_transaction（按 finish_time - create_time 计算耗时，仅统计 status=2 的事务）
  - 消息回执表 t_message_receipt（MQ 消费成功/失败计数）

⚠️ 耗时统计说明（P1-8 修复）：当前阶段统计的是**事务维度耗时**（t_transaction.finish_time - create_time），
  而非凭证维度耗时。实践中事务与凭证通常一对一，用事务耗时近似凭证耗时可接受。
  凭证维度耗时（post_time - create_time）可作为后续优化方向。

⚠️ 当前阶段统计为实时查询，不引入预聚合表。数据量增长后可引入定时聚合表。
```

### 4.6 过账引擎与 Step 11 的职责边界

```
Step 11（事务管理）职责：
  - 单笔凭证过账核心逻辑（加锁、余额计算、账户更新）
  - MQ 消费端过账逻辑（幂等、余额计算、状态联动）
  - 回滚逻辑（实时回滚、单边回滚）
  - 单笔过账入口（PostingApplicationService.executePosting）

Step 12（过账引擎）职责：
  - 批量过账调度（查询待过账凭证 → 逐笔调用 Step 11）
  - 本地消息扫描 Job（扫描 t_local_message → 投递 MQ）
  - 过账进度监控（查询凭证/事务/批次进度）
  - 异常凭证治理（重试、跳过、僵尸凭证检测）
  - 过账统计报表（多维度统计数据）

Step 12 不重复实现：
  - ❌ 余额计算（使用 Step 11 的 AccountBalanceCalculator）
  - ❌ 加锁逻辑（使用 Step 11 的 PostingDomainService）
  - ❌ 回滚逻辑（使用 Step 11 的 RollbackDomainService）
  - ❌ MQ 消费逻辑（使用 Step 11 的 PostingMessageConsumer）

Step 12 的核心设计原则：
  ✅ 编排而非实现：调用 Step 11 的已有能力
  ✅ 非侵入式：不在 Step 11 的核心路径上增加逻辑
  ✅ 可独立运行：批量过账和监控不阻塞单笔过账
```

---

## 5. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── BatchPostingRequest.java          # 批量过账请求 DTO
    │   ├── PostingRetryRequest.java          # 凭证重试请求 DTO
    │   └── PostingSkipRequest.java           # 凭证跳过请求 DTO
    └── response/
        ├── BatchPostingResponse.java         # 批量过账结果响应 DTO
        ├── PostingMonitorResponse.java       # 过账监控响应 DTO
        ├── PostingStatsResponse.java         # 过账统计报表响应 DTO
        ├── PostingRetryResponse.java         # 凭证重试结果响应 DTO
        └── AbnormalVoucherResponse.java      # 异常凭证响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── PostingEngineDomainService.java    # 批量过账领域服务（Java-A）
    │       └── PostingMonitorDomainService.java   # 过账监控领域服务（Java-B）
    ├── application/
    │   └── service/
    │       └── PostingEngineApplicationService.java  # 过账引擎应用服务（Java-C）
    │   └── assembler/
    │       └── PostingEngineAssembler.java        # PO ↔ DTO 转换（Java-C）
    └── interfaces/
        └── PostingEngineController.java           # 过账引擎 Controller（Java-C）

accounting-job/
└── src/main/java/com/kltb/accounting/job/
    ├── LocalMessageScanJobHandler.java        # 本地消息扫描 Job（Java-A）
    └── BatchPostingJobHandler.java            # 批量过账 Job（Java-A）
```

**需要修改/补充的 Mapper**：

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountingVoucherMapper.java` | 补充方法 | 追加 `selectByStatusAndDateRange`、`countByStatusGroup` |
| `TransactionMapper.java` | 补充方法 | 追加 `countByStatusAndDateRange`、`selectDurationStats` |
| `LocalMessageMapper.java` | 补充方法 | 追加 `selectPendingMessages`、`updateRetryInfo` |

**已有 Repository（直接复用）**：

| 文件 | 已有方法 |
|------|----------|
| `AccountingVoucherRepository` | `selectByVoucherNo`、`selectEntriesByVoucherNo`、`updateById`、`updateStatusByVoucherNo` |
| `TransactionRepository` | `selectByTxnNo`、`updateStatusByTxnNo` |
| `LocalMessageService` | `save`、`markSent` |

---

## 6. 接口契约

所有接口路径前缀：`/accounting/posting-engine`

### 6.1 POST `/accounting/posting-engine/batch` — 批量过账

**请求体** (`BatchPostingRequest`):
```json
{
  "startDate": "2026-05-12",
  "endDate": "2026-05-12",
  "businessCode": "LOAN",
  "maxBatchSize": 50
}
```

**校验规则**：
- `startDate`：必填
- `endDate`：非必填，默认等于 startDate
- `businessCode`：非必填，不传则查询所有业务线
- `maxBatchSize`：非必填，默认 50，最大 200

**业务逻辑**：
1. 按条件查询待过账凭证（status=1 未过账）
2. 逐笔调用 PostingApplicationService.executePosting()
3. 单笔失败不中断，继续处理下一笔
4. 返回批量过账结果（总数/成功/失败/失败列表）

### 6.2 GET `/accounting/posting-engine/monitor/voucher/{voucherNo}` — 凭证过账进度

**响应**：凭证基本信息 + 分录过账状态 + 进度百分比 + 各类型分录处理情况

### 6.3 GET `/accounting/posting-engine/monitor/transaction/{txnNo}` — 事务过账进度

**响应**：事务基本信息 + 关联凭证过账进度 + 整体进度百分比

### 6.4 GET `/accounting/posting-engine/monitor/stats` — 过账统计报表

**请求参数**：
- `startDate`：必填，统计起始日期
- `endDate`：必填，统计结束日期
- `dimension`：非必填，默认 `date`，可选 `businessCode` / `tradingCode` / `payChannel`

**响应**：按维度分组的统计数据列表（total_count / success_count / failed_count / success_rate / avg_duration_ms）

### 6.5 POST `/accounting/posting-engine/abnormal/retry` — 异常凭证重试

**请求体** (`PostingRetryRequest`):
```json
{
  "voucherNo": "VOU20260512000001",
  "operatorName": "admin",
  "retryReason": "系统异常后手动重试"
}
```

**业务逻辑**：
1. 校验凭证状态为 4(过账失败)（status=2 的僵尸凭证由 Job 自动检测处理，不开放手动重试）
2. 校验 retry_count < 5（最多 5 次手动重试）
3. 调用 PostingEngineDomainService.postSingleVoucher(voucherNo)（间接委托 Step 11）
4. 重试成功 → retry_count 不变；重试失败 → retry_count += 1

### 6.6 POST `/accounting/posting-engine/abnormal/skip` — 异常凭证跳过

**请求体** (`PostingSkipRequest`):
```json
{
  "voucherNo": "VOU20260512000001",
  "operatorName": "admin",
  "skipReason": "业务确认无需过账"
}
```

**业务逻辑**：
1. 校验凭证状态为 4(过账失败)
2. 更新凭证 skip_flag = 1（人工跳过），status 保持 4
3. 记录跳过原因和操作人到 fail_reason 列
4. 触发告警（跳过意味着数据不一致，需要人工介入）

> P2-13 修复：二级确认由前端页面负责（弹窗二次确认），后端通过 skip_reason 非空即视为已确认。

### 6.7 GET `/accounting/posting-engine/abnormal/list` — 异常凭证列表

**请求参数**：
- `status`：非必填，4=过账失败，2=过账中（僵尸凭证）
- `startDate` / `endDate`：非必填，按会计日期过滤

**响应**：异常凭证列表（voucherNo / status / accountingDate / failReason / retryCount）

---

## 7. 领域服务设计

### 7.1 PostingEngineDomainService（Java-A 负责）

> **职责**：批量过账核心逻辑，不含事务，由 Application Service 统一控制事务边界。

```java
@Service
@RequiredArgsConstructor
public class PostingEngineDomainService {

    private final PostingApplicationService postingApplicationService;
    private final AccountingVoucherRepository accountingVoucherRepository;

    /**
     * 批量过账：按条件查询待过账凭证 → 逐笔执行过账
     *
     * @param startDate    起始会计日期
     * @param endDate      结束会计日期
     * @param businessCode 业务线编码（null 表示全部）
     * @param maxBatchSize 最大批次大小
     * @return 批量过账结果
     */
    public BatchPostingResult executeBatchPosting(
        LocalDate startDate,
        LocalDate endDate,
        String businessCode,
        int maxBatchSize);

    /**
     * 查询待过账凭证列表
     */
    public List<AccountingVoucherPO> selectPendingVouchers(
        LocalDate startDate,
        LocalDate endDate,
        String businessCode,
        int limit);

    /**
     * 单笔凭证过账（委托给 Step 11）
     * 返回类型 PostingExecuteResult 来自 Step 11 的 PostingApplicationService.executePosting()，直接透传。
     */
    public PostingExecuteResult postSingleVoucher(String voucherNo);
}
```

### 7.2 PostingMonitorDomainService（Java-B 负责）

```java
@Service
@RequiredArgsConstructor
public class PostingMonitorDomainService {

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final PostingEngineDomainService postingEngineDomainService;
    // P1-6 修复：不直接依赖 PostingApplicationService，通过 PostingEngineDomainService.postSingleVoucher 间接调用

    /**
     * 查询凭证过账进度
     */
    public VoucherProgressData getVoucherProgress(String voucherNo);

    /**
     * 查询事务过账进度
     */
    public TransactionProgressData getTransactionProgress(String txnNo);

    /**
     * 查询过账统计报表（按维度分组）
     */
    public List<PostingStatsData> getPostingStats(
        LocalDate startDate,
        LocalDate endDate,
        String dimension);

    /**
     * 查询异常凭证列表
     */
    public List<AbnormalVoucherData> getAbnormalVouchers(
        Integer status,
        LocalDate startDate,
        LocalDate endDate);

    /**
     * 僵尸凭证检测：status=2 且超过 1 小时未完成的凭证
     */
    public List<AbnormalVoucherData> detectZombieVouchers();

    /**
     * 凭证重试（限次校验 + 委托 Step 11）
     */
    public PostingExecuteResult retryAbnormalVoucher(
        String voucherNo, String operatorName, String retryReason);

    /**
     * 凭证跳过
     */
    public void skipAbnormalVoucher(
        String voucherNo, String operatorName, String skipReason);
}
```

### 7.3 PostingEngineApplicationService（Java-C 负责）

```java
@Service
@RequiredArgsConstructor
public class PostingEngineApplicationService {

    private final PostingEngineDomainService postingEngineDomainService;
    private final PostingMonitorDomainService postingMonitorDomainService;
    private final PostingEngineAssembler assembler;
}
```

---

## 8. 领域数据对象

### 8.1 BatchPostingResult（批量过账结果）

```java
@Data
@AllArgsConstructor
public class BatchPostingResult {
    private int totalCount;
    private int successCount;
    private int failedCount;
    private long totalDurationMs;
    private List<FailedVoucherInfo> failedList;

    @Data
    @AllArgsConstructor
    public static class FailedVoucherInfo {
        private String voucherNo;
        private String failReason;
    }
}
```

### 8.2 VoucherProgressData（凭证过账进度）

```java
@Data
@AllArgsConstructor
public class VoucherProgressData {
    private String voucherNo;
    private Integer status;              // 状态码，与 DDL TINYINT 一致
    private String statusDesc;           // 状态描述
    private LocalDate accountingDate;
    private int totalEntries;
    private int realTimePosted;
    private int realTimeTotal;
    private int asyncPosted;
    private int asyncTotal;
    private int bufferPosted;     // Step 16 更新
    private int bufferTotal;
    private BigDecimal progressPercent; // (realTimePosted + asyncPosted) / (realTimeTotal + asyncTotal)
}
```

### 8.3 PostingStatsData（过账统计数据）

```java
@Data
@AllArgsConstructor
public class PostingStatsData {
    private String dimensionValue;  // 日期/业务线/交易码/渠道
    private int totalCount;
    private int successCount;
    private int failedCount;
    private int processingCount;
    private BigDecimal successRate;
    private Long avgDurationMs;
    private Long maxDurationMs;
    private Long minDurationMs;
}
```

### 8.4 AbnormalVoucherData（异常凭证数据）

```java
@Data
@AllArgsConstructor
public class AbnormalVoucherData {
    private String voucherNo;
    private Integer status;              // 状态码，与 DDL TINYINT 一致
    private String statusDesc;           // 状态描述
    private LocalDate accountingDate;
    private String failReason;
    private Integer retryCount;
    private Integer skipFlag;          // 0-未跳过, 1-人工跳过（P0-3/P0-8 修复）
    private LocalDateTime updateTime;  // P1-9 修复：用 update_time 而非 post_time
}
```

---

## 9. Job 设计

### 9.1 LocalMessageScanJobHandler（accounting-job 模块）

```java
@Component
public class LocalMessageScanJobHandler {

    // P2-10 修复确认项：rocketMQProducer 的类型和注入方式需与 Step 4/Step 11 已有 MQ 发送方式保持一致。
    // 若项目使用 Aliyun ONS 封装的 Producer，应使用对应的 Bean 而非原生 RocketMQTemplate。
    private final LocalMessageService localMessageService;
    private final RocketMQProducer rocketMQProducer; // 或等价 Producer（需确认与项目已有集成一致）
    private final MessageReceiptMapper messageReceiptMapper;

    /**
     * XXL-JOB Handler
     * 默认每 30 秒执行一次
     * 扫描 t_local_message（status=1 待发送）→ 投递 MQ → 更新状态
     */
    @XxlJob("localMessageScanJob")
    public ReturnT<String> execute() {
        // 1. 查询待发送消息（LIMIT 100）
        List<LocalMessagePO> messages = localMessageService.selectPendingMessages(100);

        int success = 0;
        int failed = 0;

        for (LocalMessagePO message : messages) {
            try {
                // 2. 投递 MQ
                rocketMQProducer.send(message.getTopic(), message.getTag(),
                    message.getBusinessKey(), message.getPayload());

                // 3. 更新状态
                localMessageService.markSent(message.getBusinessKey());
                success++;
            } catch (Exception e) {
                // 4. 重试计数 +1，计算下次重试时间
                localMessageService.updateRetryInfo(message.getMessageId(), e.getMessage());
                failed++;
            }
        }

        return ReturnT.SUCCESS;
    }
}
```

### 9.2 BatchPostingJobHandler（accounting-job 模块）

```java
@Component
public class BatchPostingJobHandler {

    private final PostingEngineDomainService postingEngineDomainService;

    /**
     * XXL-JOB Handler
     * 调度配置：每 5 分钟执行一次
     * 路由策略：FIRST（单实例执行）
     * 参数传递：通过 XXL-JOB 任务参数传入会计日期（格式 yyyy-MM-dd），
     *           不传则默认使用 LocalDate.now()。
     *
     * 查询待过账凭证 → 逐笔执行过账
     */
    @XxlJob("batchPostingJob")
    public ReturnT<String> execute(String param) {
        // P1-5 修复：支持通过 XXL-JOB 参数指定会计日期
        LocalDate targetDate;
        if (param != null && !param.trim().isEmpty()) {
            try {
                targetDate = LocalDate.parse(param.trim());
            } catch (Exception e) {
                log.error("[BATCH-POSTING-JOB] 参数解析失败: param={}, 使用当前日期", param);
                targetDate = LocalDate.now();
            }
        } else {
            targetDate = LocalDate.now();
        }

        log.info("[BATCH-POSTING-JOB] 开始执行: date={}", targetDate);

        long startTime = System.currentTimeMillis();
        BatchPostingResult result = postingEngineDomainService.executeBatchPosting(
            targetDate, targetDate, null, 50);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[BATCH-POSTING-JOB] 执行完成: total={}, success={}, failed={}, duration={}ms",
            result.getTotalCount(), result.getSuccessCount(), result.getFailedCount(), duration);

        if (result.getFailedCount() > 0) {
            log.warn("[BATCH-POSTING-JOB] 部分失败: total={}, success={}, failed={}",
                result.getTotalCount(), result.getSuccessCount(), result.getFailedCount());
        }

        return ReturnT.SUCCESS;
    }
}
```

---

## 10. 编码要点

### 10.1 事务边界

- 批量过账中**每笔凭证使用独立事务**（调用 Step 11 的 PostingApplicationService）
- 批量过账整体**不包裹大事务**（一笔失败不影响其他笔）
- 本地消息 Job 中每条消息的状态更新使用**独立 TransactionTemplate**
- 凭证重试/跳过使用**独立 TransactionTemplate**
- 严禁 `@Transactional`，全部使用 `TransactionTemplate`

### 10.2 并发安全

- 批量过账 Job 与手动批量过账**互斥执行**（使用 XXL-JOB 的路由策略或分布式锁）
- 同一凭证**不可同时被两路过账线程处理**（Step 11 已有分布式锁防护）
- 本地消息扫描 Job **单实例执行**（XXL-JOB 路由策略 = FIRST / SHARDING）

### 10.3 异常处理

- 批量过账中单笔失败**不中断整体流程**，继续处理下一笔
- 失败原因记录到 `BatchPostingResult.failedList`
- 失败凭证自动进入异常凭证列表，可通过手动重试或跳过治理
- 本地消息投递失败**不抛异常**，仅更新重试信息后 continue

### 10.4 异常体系

| 场景 | 异常类型 | ResultCode | 说明 |
|------|---------|-----------|------|
| 凭证不存在 | `ServiceException` | VOUCHER_NOT_FOUND | 通用业务异常 |
| 凭证状态非法（重试/跳过） | `ServiceException` | VOUCHER_STATUS_ILLEGAL | 不允许重试/跳过 |
| 重试次数超限 | `ServiceException` | POSTING_RETRY_EXHAUSTED | 已达最大重试次数 |
| 批量过账部分失败 | 不抛异常 | — | 通过 BatchPostingResult 返回失败信息 |
| 本地消息投递失败 | 不抛异常 | — | 通过重试机制处理 |

### 10.5 与 Step 11 的衔接

```
Step 11 已实现能力：
  - PostingApplicationService.executePosting(voucherNo)  ← 单笔过账入口
  - PostingDomainService.executeRealTimePosting()         ← 实时过账
  - AsyncPostingDomainService.consumeAsyncPostingMessage() ← 异步过账
  - RollbackDomainService.markTransactionFailed()          ← 失败回滚

Step 12 调用方式：
  PostingEngineDomainService.postSingleVoucher(voucherNo)
    → 委托 → PostingApplicationService.executePosting(voucherNo)

Step 12 不重复实现任何余额计算/加锁/回滚逻辑。
```

### 10.6 与 Step 16 的衔接

```
Step 12 进度监控中：
  - bufferPosted / bufferTotal 当前阶段始终为 0
  - Step 16 缓冲记账完成后，更新缓冲分录状态，进度自动反映

Step 12 统计报表中：
  - 缓冲凭证（posting_type=BUFFER）当前阶段不计入统计
  - Step 16 完成后，统计口径扩展为包含缓冲凭证

Step 12 批量过账中：
  - 当前阶段仅查询 posting_type IN ('REALTIME', 'ASYNC') 的凭证
  - 排除 posting_type = 'BUFFER' 的凭证
```

### 10.7 与 Step 17 的衔接

```
Step 17（EOD 日切）需要 Step 12 提供的能力：
  - 日切前检查：查询当日所有凭证过账进度，确认 100% 完成
  - 如果存在未过账凭证，阻断日切流程
  - 统计报表数据为试算平衡提供基准

Step 17 调用方式：
  PostingMonitorDomainService.getVoucherProgress()  → 检查凭证进度
  PostingMonitorDomainService.getPostingStats()     → 获取统计数据
```

---

## 11. 完成标准（Checklist）

> **对齐日期**：2026-06-24，代码与文档逐项核对后打勾。
> **P0-5 说明**：`LocalMessageScanJobHandler` 已实现为 `LocalMessageRetryJob`，功能更完整（支持分片、claim 防重、指数退避、AbstractXxlJobHandler 父类）。

### Java-A
- [x] P0-1: `AccountingVoucherMapper` 补充 `selectByStatusAndDateRange`、`countByStatusGroup`
- [x] P0-2: `AccountingVoucherRepository` 补充批量查询方法
- [x] P0-3: `TransactionMapper` 补充 `countByStatusAndDateRange`、`selectDurationStats`
- [x] P0-4: `TransactionRepository` 补充统计方法
- [x] P0-6: `LocalMessageService` 补充 `selectPendingMessages`
- [x] P0-7: `LocalMessageMapper` 补充 `selectByStatusAndRetryLimit`
- [x] `PostingEngineDomainService` 批量过账领域服务
- [x] `executeBatchPosting` 按条件查询 → 逐笔调用 Step 11 → 统计结果
- [x] `selectPendingVouchers` 按状态+日期范围查询
- [x] `postSingleVoucher` 委托 Step 11
- [x] `LocalMessageRetryJob` XXL-JOB Handler（原命名 LocalMessageScanJobHandler，每 30 秒执行）
- [x] 本地消息扫描：查询 → 投递 MQ → 更新状态 → 失败重试（分片 + claim 防重 + 指数退避）
- [x] `BatchPostingJobHandler` XXL-JOB Handler（每 5 分钟执行）
- [x] 批量过账 Job：查询当日待过账凭证 → 逐笔执行
- [x] 单测：PostingEngineDomainServiceTest（批量过账/单笔失败不中断）+ LocalMessageRetryJob（已在 job 模块）

### Java-B
- [x] `PostingMonitorDomainService` 过账监控领域服务
- [x] `getVoucherProgress` 凭证过账进度查询
- [x] `getTransactionProgress` 事务过账进度查询
- [x] `getPostingStats` 过账统计报表（按日期/业务线/交易码/渠道分组）
- [x] `getAbnormalVouchers` 异常凭证列表查询
- [x] `detectZombieVouchers` 僵尸凭证检测（status=2 且 update_time < NOW() - 1h，P1-9 修复）
- [x] `retryAbnormalVoucher` 凭证重试（限次校验 + 委托 PostingEngineDomainService.postSingleVoucher，P1-6 修复）
- [x] `skipAbnormalVoucher` 凭证跳过（skip_flag=1 + 告警，P0-3/P0-8 修复）
- [x] 单测：PostingMonitorDomainServiceTest（进度计算/统计分组/异常凭证/重试/跳过）

### Java-C
- [x] `PostingEngineApplicationService` 过账引擎应用服务（编排层）
- [x] `PostingEngineController` 实现 7 个接口
- [x] `BatchPostingRequest` / `PostingRetryRequest` / `PostingSkipRequest` DTO
- [x] `BatchPostingResponse` / `PostingMonitorResponse` / `PostingStatsResponse` / `PostingRetryResponse` / `AbnormalVoucherResponse` DTO
- [x] DTO 使用 `jakarta.validation` 包
- [x] `PostingEngineAssembler` 完成 PO/Result ↔ DTO 转换
- [x] 参数校验（startDate 必填、voucherNo 必填等）
- [x] 凭证不存在 / 状态非法返回明确错误
- [x] 重试次数超限返回明确错误
- [x] 批量过账部分失败不抛异常，通过响应体返回失败信息
- [x] 单测：PostingEngineDomainServiceTest 覆盖批量过账/进度查询接口

### TL Review
- [x] 批量过账中每笔凭证使用独立事务（不包裹大事务）
- [x] 单笔失败不中断整体流程（try-catch continue 模式）
- [x] 本地消息 Job 单实例执行（XXL-JOB 路由策略 = FIRST）
- [x] 同一凭证不可同时被两路过账线程处理（Step 11 分布式锁防护）
- [x] 严禁 `@Transactional`，全部使用 `TransactionTemplate`
- [x] Step 12 不重复实现 Step 11 的余额计算/加锁/回滚逻辑
- [x] 批量过账仅查询 posting_type IN ('REALTIME', 'ASYNC') 的凭证（排除 BUFFER）
- [x] 凭证重试限次校验（最多 5 次）
- [x] 凭证跳过触发告警（数据不一致风险），使用 skip_flag 而非新状态值（P0-3/P0-8）
- [x] 僵尸凭证检测逻辑正确（status=2 且 update_time < NOW() - 1h，P1-9 修复）
- [x] 进度计算排除缓冲分录（Step 16 处理）
- [x] 异常体系使用正确（ServiceException vs AccountException）
- [x] DTO 使用 jakarta.validation 包
- [x] 与 Step 11 衔接正确（委托 PostingApplicationService.executePosting）
- [x] 与 Step 16 衔接正确（缓冲凭证不计入进度/统计）
- [x] 与 Step 17 衔接正确（日切前检查过账进度）

---

## 12. 下一步行动

进入 **Step 13 · Account Status Control（账户状态管理）**，详见 `docs/prompt/step-13-account-status.md`。
