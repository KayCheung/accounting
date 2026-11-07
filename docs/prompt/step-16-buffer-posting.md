# step-16-buffer-posting · 缓冲记账

> **Phase 6 第一步** | 归属：`@Java` 工程师
> 前置依赖：Step 14（资金冻结与解冻）+ Step 15（余额查询接口）
>
> **与 Step 10/12 的边界**：Step 10 负责**凭证生成时将匹配到缓冲规则的记录写入 `t_buffer_posting_detail`**（状态=待入账），Step 12 负责**实时过账/异步 MQ 过账**。本 Step 负责**将已缓冲的明细按不同模式（逐条/批量）执行真实过账**，更新余额 + 写 Pre/Post 快照 + 更新凭证状态。

---

## 0. 前置补充任务（开始编码前必须完成）

| # | 任务 | 涉及文件 | 说明 |
|---|------|---------|------|
| P0-1 | `BufferPostingDetailMapper` 补充批量查询方法 | `BufferPostingDetailMapper.java` + `BufferPostingDetailMapper.xml` | 按 bufferMode + status + accountingDate 批量查询待入账明细 |
| P0-2 | `BufferPostingDetailMapper` 补充按账户汇总方法 | `BufferPostingDetailMapper.java` + XML | 按 accountNo 汇总金额（用于 bufferMode=2 日间批量） |
| P0-3 | `BufferPostingDetailMapper` 补充 Running Balance 校验方法 | `BufferPostingDetailMapper.java` + XML | 查询某账户某会计日期末条缓冲明细的 postBalance |
| P0-4 | `BufferPostingDetailMapper` 补充分片批量查询 | `BufferPostingDetailMapper.java` + XML | 按 sharding 范围查询（用于 Job 分片扫描） |
| P0-5 | `SubAccountMapper` 补充余额更新（已有，确认 P0-1 方法可用） | `SubAccountMapper.java` | Step 14 已实现 `updateBalance`，确认可复用 |
| P0-6 | `BufferPostingDetailRepository` 封装 | `BufferPostingDetailRepository.java` | 封装所有缓冲明细查询方法 |

### P0-1: BufferPostingDetailMapper 批量查询

```java
// BufferPostingDetailMapper.java
/**
 * 批量查询待入账缓冲明细（按会计日期 + 模式 + 状态过滤）
 * <p>
 * 使用 MyBatis-Plus LambdaQueryWrapper，无需写 XML。
 */
default List<BufferPostingDetailPO> selectPendingByCondition(
        LocalDate accountingDate, Integer bufferMode, Integer status, int limit) {
    LambdaQueryWrapper<BufferPostingDetailPO> wrapper = new LambdaQueryWrapper<BufferPostingDetailPO>()
            .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
            .eq(BufferPostingDetailPO::getStatus, status)
            .eq(BufferPostingDetailPO::getIsDelete, 0)
            .orderByAsc(BufferPostingDetailPO::getId)
            .last("LIMIT " + limit);
    if (bufferMode != null) {
        wrapper.eq(BufferPostingDetailPO::getBufferMode, bufferMode);
    }
    return this.selectList(wrapper);
}
```

### P0-2: BufferPostingDetailMapper 按账户汇总

> **注意**：聚合查询（GROUP BY + SUM）无法用 Wrapper 表达，需要写 XML。

```java
/**
 * 按账户汇总缓冲金额（用于 bufferMode=2 日间批量）
 * <p>
 * 涉及 GROUP BY + SUM 聚合，XML 实现。
 */
List<Map<String, Object>> sumByAccountNo(
    @Param("accountingDate") LocalDate accountingDate,
    @Param("status") Integer status);
```

对应 XML：

```xml
<select id="sumByAccountNo" resultType="java.util.HashMap">
    SELECT account_no,
           SUM(amount) AS total_amount,
           COUNT(*) AS detail_count
    FROM t_buffer_posting_detail
    WHERE accounting_date = #{accountingDate}
      AND status = #{status}
      AND is_delete = 0
    GROUP BY account_no
</select>
```

### P0-3: BufferPostingDetailMapper Running Balance 校验

```java
/**
 * 查询某账户某会计日期最后一条缓冲明细
 * <p>
 * 使用 MyBatis-Plus LambdaQueryWrapper，无需写 XML。
 */
default BufferPostingDetailPO selectLastDetailByAccountAndDate(
        String accountNo, LocalDate accountingDate) {
    return this.selectOne(new LambdaQueryWrapper<BufferPostingDetailPO>()
            .eq(BufferPostingDetailPO::getAccountNo, accountNo)
            .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
            .eq(BufferPostingDetailPO::getIsDelete, 0)
            .orderByDesc(BufferPostingDetailPO::getTradeTime)
            .orderByDesc(BufferPostingDetailPO::getId)
            .last("LIMIT 1"));
}
```

### P0-4: BufferPostingDetailMapper 分片批量查询

```java
/**
 * 按分片值范围查询缓冲明细（用于 Job 分片扫描）
 * <p>
 * 使用 MyBatis-Plus LambdaQueryWrapper，无需写 XML。
 */
default List<BufferPostingDetailPO> selectByShardingRange(
        Long shardingStart, Long shardingEnd, LocalDate accountingDate, Integer status, int limit) {
    LambdaQueryWrapper<BufferPostingDetailPO> wrapper = new LambdaQueryWrapper<BufferPostingDetailPO>()
            .ge(BufferPostingDetailPO::getSharding, shardingStart)
            .le(BufferPostingDetailPO::getSharding, shardingEnd)
            .eq(BufferPostingDetailPO::getAccountingDate, accountingDate)
            .eq(BufferPostingDetailPO::getStatus, status)
            .eq(BufferPostingDetailPO::getIsDelete, 0)
            .orderByAsc(BufferPostingDetailPO::getId)
            .last("LIMIT " + limit);
    return this.selectList(wrapper);
}
```

### P0-6: BufferPostingDetailRepository 封装

```java
// BufferPostingDetailRepository.java
@Repository
@RequiredArgsConstructor
public class BufferPostingDetailRepository {

    private final BufferPostingDetailMapper bufferPostingDetailMapper;

    public List<BufferPostingDetailPO> selectPendingByCondition(
            LocalDate accountingDate, Integer bufferMode, Integer status, int limit) {
        return bufferPostingDetailMapper.selectPendingByCondition(
                accountingDate, bufferMode, status, limit);
    }

    public List<Map<String, Object>> sumByAccountNo(LocalDate accountingDate, Integer status) {
        return bufferPostingDetailMapper.sumByAccountNo(accountingDate, status);
    }

    public BufferPostingDetailPO selectLastDetailByAccountAndDate(String accountNo, LocalDate accountingDate) {
        return bufferPostingDetailMapper.selectLastDetailByAccountAndDate(accountNo, accountingDate);
    }

    public List<BufferPostingDetailPO> selectByShardingRange(
            Long shardingStart, Long shardingEnd, LocalDate accountingDate, Integer status, int limit) {
        return bufferPostingDetailMapper.selectByShardingRange(
                shardingStart, shardingEnd, accountingDate, status, limit);
    }

    public void updateToProcessing(Long id) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.PROCESSING);
        po.setStartTime(LocalDateTime.now());
        bufferPostingDetailMapper.updateById(po);
    }

    public void updateToSuccess(Long id, LocalDateTime completeTime) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.SUCCESS);
        po.setCompleteTime(completeTime);
        int affected = bufferPostingDetailMapper.updateById(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "缓冲明细更新冲突: id=" + id);
        }
    }

    public void updateToFailed(Long id, String failReason) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(id);
        po.setStatus(BufferStatusEnum.FAILED);
        po.setFailReason(failReason);
        po.setCompleteTime(LocalDateTime.now());
        int affected = bufferPostingDetailMapper.updateById(po);
        if (affected == 0) {
            throw new AccountException(ResultCode.OPTIMISTIC_LOCK_FAILED,
                    "缓冲明细更新冲突: id=" + id);
        }
    }
}
```

---

## 1. 任务目标（Mission）

实现缓冲记账引擎，将 Step 10 生成的缓冲明细（`t_buffer_posting_detail`，status=待入账）按不同缓冲模式执行真实过账：

1. **逐条缓冲（buffer_mode=1）**：异步逐条处理每条缓冲明细，执行过账逻辑
2. **日间批量（buffer_mode=2）**：按账户汇总后批量更新余额（同一账户多条明细合并为一条余额变动）
3. **Running Balance 校验**：批处理完成后校验末条 `post_balance` 是否等于账户实际余额
4. **锁升级策略**：乐观锁 → 失败 3 次 → 悲观锁 → 仍失败 → 告警
5. **Job 分片策略**：按 `account_no` 哈希值分片扫描，支持水平扩展

> **核心原则**：缓冲记账是**延迟过账**机制，与 Step 12 实时过账共享同一套余额计算逻辑，但执行时机不同。缓冲记账不生成新凭证（凭证已在 Step 10 生成），仅执行余额更新 + 状态联动。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 余额方向约束、严禁负数运算、借贷平衡 |
| 3 | `docs/sql/3-rule.sql` | `t_buffer_posting_rule` / `t_buffer_posting_detail` DDL |
| 4 | `docs/prompt/step-10-vouchering.md` | Step 10 凭证生成（缓冲明细写入逻辑） |
| 5 | `docs/prompt/step-12-posting.md` | Step 12 过账引擎（余额计算 + 锁控制参考） |
| 6 | `docs/prompt/step-14-freeze.md` | Step 14 冻结（子账户余额更新参考） |
| 7 | `accounting-core/.../domain/service/BufferPostingDomainService.java` | 已有缓冲规则匹配 + 辅助核算分摊逻辑 |
| 8 | `accounting-core/.../domain/service/PostingEngineDomainService.java` | 批量过账模式参考 |
| 9 | `accounting-job/.../job/BatchPostingJobHandler.java` | XXL-JOB Handler 编写模式参考 |
| 10 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |

---

## 3. 任务分配与子 Agent 编排

> **执行指引**：本 Step 任务较大，建议使用 **3 个子 Agent 并行执行独立任务**，按以下顺序编排：

### 子 Agent 编排策略

```
Phase 1（串行）：P0 前置补充任务
  └─ Agent-A：完成 P0-1~P0-6（Mapper + Repository 补充 + DDL 确认）

Phase 2（可并行）：核心业务逻辑
  ├─ Agent-B：BufferPostingEngineDomainService（逐条缓冲 + 日间批量 + 锁升级）
  ├─ Agent-C：BufferPostingJobHandler（XXL-JOB 分片扫描 + 定时任务）
  └─ Agent-D：RunningBalanceValidator（Running Balance 校验 + 告警）

Phase 3（依赖 Phase 2）：Controller 与接口
  └─ Agent-E：BufferPostingApplicationService + BufferPostingController + DTO

> 执行顺序：Agent-A → (Agent-B + Agent-C + Agent-D 并行) → Agent-E
```

### Agent-A：P0 前置补充

**负责**：P0-1~P0-6 全部 Mapper/XML/Repository 补充
**输入**：本文件第 0 节
**输出**：1 个聚合 XML（sumByAccountNo）+ 3 个 LambdaQueryWrapper default 方法 + 1 个 Repository

### Agent-B：缓冲记账引擎核心

**负责**：`BufferPostingEngineDomainService`
- `executeSinglePosting(BufferPostingDetailPO detail)` — 逐条缓冲（buffer_mode=1）
- `executeBatchPosting(LocalDate accountingDate, String accountNo)` — 日间批量（buffer_mode=2）
- `executeBalanceUpdate(...)` — 余额计算 + 子账户更新 + 明细快照
- `executeLockUpgrade(...)` — 锁升级策略（乐观 → 悲观 → 告警）

**技术要点**：
- 复用 Step 12 `PostingDomainService` 的余额计算逻辑
- 复用 Step 14 `SubAccountRepository.updateBalance` 的乐观锁更新
- 分布式锁：`lockKey = "account:buffer:{accountNo}"`
- 事务边界：`TransactionTemplate` 包裹单笔/批量过账

### Agent-C：定时任务 + 分片扫描

**负责**：`BufferPostingJobHandler`（accounting-job 模块）
- `@XxlJob("bufferPostingAsyncJob")` — 异步逐条扫描 Job（每 1 分钟执行）
- `@XxlJob("bufferPostingBatchJob")` — 日间批量扫描 Job（每 30 分钟执行）
- `@XxlJob("bufferPostingEodJob")` — 日终批量扫描 Job（每日 23:50 执行）
- 分片策略：按 `sharding` 字段范围扫描（默认 128 分片，按实例数分配）

**技术要点**：
- 参考 `BatchPostingJobHandler` 编写模式
- 分片计算：`shardingStart = (shardIndex * totalShards / shardTotal)`
- 单笔失败不中断，记录错误日志
- 任务参数：会计日期（可选，默认当日）

### Agent-D：Running Balance 校验

**负责**：`RunningBalanceValidator`
- `validateRunningBalance(String accountNo, LocalDate accountingDate)` — 校验末条 postBalance 与账户余额一致
- 不一致时记录告警日志 + 更新缓冲明细 `fail_reason`
- 告警阈值：差额 > 0.000001（DECIMAL(18,6) 精度）

**技术要点**：
- 查询账户实际余额（`SubAccountRepository.selectByAccountNoAndType`）
- 查询末条缓冲明细的 `postBalance`（需计算：preBalance ± amount）
- 比较差额，超阈值则告警

### Agent-E：应用层 + Controller

**负责**：`BufferPostingApplicationService` + `BufferPostingController` + DTO + Assembler
- `POST /accounting/buffer/execute` — 手动触发缓冲记账
- `GET /accounting/buffer/pending-stats` — 查询待入账统计信息
- `GET /accounting/buffer/monitor` — 缓冲监控（按日期/状态分组）
- DTO：`BufferExecuteRequest` / `BufferPendingStatsResponse` / `BufferMonitorResponse`

---

## 4. 核心业务规则

### 4.1 逐条缓冲（buffer_mode=1）

```
触发方式：异步逐条扫描 Job（每 1 分钟执行）

执行逻辑（每笔独立事务）：
  1. 查询待入账缓冲明细（status=1 待入账，按 ID 升序，LIMIT 50）
  2. 对每条明细：
     a. 加分布式锁：lockKey = "account:buffer:{accountNo}"
     b. 双重检查：查询当前子账户余额
     c. 余额计算：
        - 借方（debit_credit=1）：余额增加 → newBalance = oldBalance + amount
        - 贷方（debit_credit=2）：余额减少 → newBalance = oldBalance - amount
        - 注意：具体增减方向还需结合余额方向（balance_direction）判断
     d. 余额充足性校验（贷方场景）：
        - 如果 oldBalance < amount → 余额不足，标记 FAILED
     e. 更新子账户余额（乐观锁）：
        - 失败 3 次内 → 重试（乐观锁）
        - 失败 >= 3 次 → 锁升级为悲观锁（SELECT FOR UPDATE）
        - 悲观锁仍失败 → 标记 FAILED + 告警
     f. 写入子账户明细快照（t_sub_account_detail）：
        - preBalance = 更新前余额
        - postBalance = 更新后余额
     g. 更新缓冲明细状态：
        - 成功 → status=3，completeTime=NOW()
        - 失败 → status=4，failReason=错误原因
     h. 更新凭证状态：
        - 该凭证所有分录均过账 → 凭证 status=3（已过账）
        - 否则 → 凭证 status=2（过账中）
     i. 释放分布式锁

异常处理：
  - 单笔失败不中断后续记录
  - 失败明细 retry_count++，等待下次扫描
  - retry_count >= 3 → 标记 FAILED + 人工介入
```

### 4.2 日间批量（buffer_mode=2）

```
触发方式：日间批量扫描 Job（每 30 分钟执行）

执行逻辑（按账户独立事务）：
  1. 按账户汇总待入账缓冲明细（GROUP BY accountNo）：
     - 同一账户的多条明细汇总为：totalAmount（借方合计 - 贷方合计）
  2. 对每个账户：
     a. 加分布式锁：lockKey = "account:buffer:{accountNo}"
     b. 双重检查：查询当前子账户余额
     c. 余额计算：
        - 净借方（totalAmount > 0）：余额增加
        - 净贷方（totalAmount < 0）：余额减少
        - 净额为零 → 跳过
     d. 余额充足性校验（净额为贷方场景）
     e. 一次性更新子账户余额（乐观锁）
     f. 写入子账户明细快照（摘要含"日间批量汇总"）
     g. 逐条更新缓冲明细状态为 SUCCESS
     h. 更新凭证状态
     i. 释放分布式锁

与逐条缓冲的区别：
  - 批量模式按账户汇总，减少 DB 写次数
  - 同一账户多条缓冲明细合并为一次余额变动
  - 子账户明细的 preBalance/postBalance 为批量前后的值
  - 单笔缓冲明细的 preBalance/postBalance 不独立记录
```

### 4.3 日终批量（buffer_mode=3）

```
触发方式：日终批量扫描 Job（每日 23:50 执行）

执行逻辑：
  与 4.2 日间批量相同，但：
  - 扫描范围为当日全部 buffer_mode=3 的缓冲明细
  - 执行完毕后触发 Running Balance 校验
  - 校验失败则阻断日切流程（通知 Step 17 EOD）
```

### 4.4 Running Balance 计算与校验

```
校验时机：日终批量（buffer_mode=3）执行完毕后

校验逻辑：
  1. 查询账户实际余额：
     - subAccountRepository.selectByAccountNoAndType(accountNo, balanceType)
     - actualBalance = subAccount.balance

  2. 查询该账户当日全部缓冲明细（按 trade_time 排序）：
     - 计算理论余额 = 期初余额 + Σ(借方金额) - Σ(贷方金额)
     - 或：末条明细的 postBalance（如果每笔都正确记录了）

  3. 比较：
     - 差额 = |actualBalance - calculatedBalance|
     - 如果差额 > 0.000001 → 告警

  4. 告警处理：
     - 记录日志：[RUNNING-BALANCE-ALERT] accountNo=X, actual=Y, calculated=Z, diff=W
     - 标记告警：在 t_account_balance_snapshot 或独立告警表记录
     - 不阻断后续流程（仅告警，由人工介入排查）
```

### 4.5 锁升级策略

```
乐观锁阶段（默认）：
  1. 查询子账户余额（带 version）
  2. 计算新余额
  3. UPDATE ... WHERE version = #{version}
  4. 如果 affected = 0 → 乐观锁冲突，重试
  5. 重试计数器 retryCount++

悲观锁阶段（retryCount >= 3）：
  1. SELECT ... FOR UPDATE（按 account_no 升序加锁）
  2. 再次查询余额
  3. 计算新余额
  4. UPDATE ... WHERE version = #{version}
  5. 如果仍失败 → 标记 FAILED + 告警

释放锁：
  - 无论成功/失败，finally 块中释放分布式锁
```

### 4.6 Job 分片策略

```
分片配置：
  - 总分片数：128（sharding 字段范围 0~2^63-1）
  - 计算方式：sharding = Math.abs(accountNo.hashCode())

扫描策略：
  - 每个 Job 实例分配一段 sharding 范围
  - 例如 4 个实例：[0-31], [32-63], [64-95], [96-127]
  - 每段独立查询 + 处理，互不干扰

分片参数通过 XXL-JOB 任务参数传递：
  格式：shardIndex=0,shardTotal=4
  计算：
    shardIndex = XxlJobHelper.getShardIndex()
    shardTotal = XxlJobHelper.getShardTotal()
    shardingStart = shardIndex * (MAX_SHARD / shardTotal)
    shardingEnd = (shardIndex + 1) * (MAX_SHARD / shardTotal) - 1
```

### 4.7 余额方向计算

```
余额方向（balance_direction）：1-借方，2-贷方
借贷方向（debit_credit）：1-借方，2-贷方

余额增减规则：
  - 借方余额账户（balance_direction=1）：
    - debit_credit=1（借方）→ 余额增加
    - debit_credit=2（贷方）→ 余额减少
  - 贷方余额账户（balance_direction=2）：
    - debit_credit=1（借方）→ 余额减少
    - debit_credit=2（贷方）→ 余额增加

计算公式：
  boolean isSameDirection = (balanceDirection == debitCredit);
  if (isSameDirection) {
      newBalance = oldBalance.add(amount);
  } else {
      if (oldBalance.compareTo(amount) < 0) {
          // 余额不足
          throw new AccountException(ResultCode.INSUFFICIENT_BALANCE, ...);
      }
      newBalance = oldBalance.subtract(amount);
  }
```

---

## 5. 接口契约

所有接口路径前缀：`/accounting/buffer`

### 5.1 POST `/accounting/buffer/execute` — 手动触发缓冲记账

**请求体** (`BufferExecuteRequest`):
```json
{
  "accountingDate": "2026-06-12",
  "bufferMode": 1,
  "accountNo": "00120260301000001",
  "maxBatchSize": 100
}
```

**校验规则**：
- `accountingDate`：必填，yyyy-MM-dd 格式
- `bufferMode`：必填，1/2/3
- `accountNo`：选填，不填则处理全部账户
- `maxBatchSize`：选填，默认 50

**响应** (`BufferExecuteResponse`):
```json
{
  "totalCount": 150,
  "successCount": 148,
  "failedCount": 2,
  "durationMs": 3500,
  "failedList": [
    {
      "detailId": 12345,
      "accountNo": "00120260301000001",
      "failReason": "余额不足"
    }
  ]
}
```

### 5.2 GET `/accounting/buffer/pending-stats` — 查询待入账统计

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountingDate | String | 是 | 会计日期 yyyy-MM-dd |

**响应** (`BufferPendingStatsResponse`):
```json
{
  "accountingDate": "2026-06-12",
  "mode1Count": 50,
  "mode1Amount": 12000.00,
  "mode2Count": 200,
  "mode2Amount": 85000.50,
  "mode3Count": 500,
  "mode3Amount": 250000.00,
  "totalAccounts": 120,
  "oldestPendingTime": "2026-06-10 08:00:00"
}
```

### 5.3 GET `/accounting/buffer/monitor` — 缓冲监控

**请求参数**：
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| accountingDate | String | 否 | 会计日期 |
| status | Integer | 否 | 状态：1-待入账, 2-处理中, 3-成功, 4-失败 |

**响应** (`BufferMonitorResponse`):
```json
{
  "totalRecords": 1000,
  "statusBreakdown": [
    { "status": 1, "count": 50, "amount": 12000.00 },
    { "status": 2, "count": 10, "amount": 3000.00 },
    { "status": 3, "count": 900, "amount": 450000.00 },
    { "status": 4, "count": 40, "amount": 20000.00 }
  ],
  "failedTopAccounts": [
    { "accountNo": "00120260301000001", "failedCount": 5, "totalAmount": 5000.00 }
  ],
  "runningBalanceAlerts": [
    { "accountNo": "00120260301000001", "actualBalance": 1000.00, "calculatedBalance": 1000.05, "diff": 0.05 }
  ]
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
    │       ├── BufferPostingEngineDomainService.java    # 缓冲记账引擎核心
    │       └── RunningBalanceValidator.java              # Running Balance 校验
    └── infrastructure/persistence/repository/
        └── BufferPostingDetailRepository.java            # 缓冲明细仓储

accounting-job/
└── src/main/java/com/kltb/accounting/job/job/
    ├── BufferPostingAsyncJobHandler.java                 # 异步逐条扫描 Job
    ├── BufferPostingBatchJobHandler.java                 # 日间批量扫描 Job
    └── BufferPostingEodJobHandler.java                   # 日终批量扫描 Job

accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── BufferExecuteRequest.java                     # 手动触发缓冲记账请求
    └── response/
        ├── BufferExecuteResponse.java                    # 手动触发响应
        ├── BufferPendingStatsResponse.java               # 待入账统计响应
        ├── BufferMonitorResponse.java                    # 缓冲监控响应
        └── BufferStatusCount.java                        # 状态分组统计内部类

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   └── service/
    │       └── BufferPostingApplicationService.java       # 应用层编排
    │   └── assembler/
    │       └── BufferPostingAssembler.java                # DTO 转换器
    └── interfaces/
        └── BufferPostingController.java                  # Controller
```

### 修改

| 文件 | 变更内容 |
|------|---------|
| `BufferPostingDetailMapper.java` | 新增 4 个 default 方法（P0-1/P0-3/P0-4 用 LambdaQueryWrapper，P0-2 聚合用 XML） |
| `BufferPostingDetailMapper.xml` | 新增 1 段 SQL（sumByAccountNo 聚合查询） |
| `SubAccountMapper.java` | 确认 `updateBalance` 可用（Step 14 已有） |
| `ResultCode.java` | 新增错误码（见下方） |

### 新增错误码

| Code | Enum | Description |
|------|------|-------------|
| `"2023"` | `BUFFER_POSTING_BALANCE_MISMATCH` | 缓冲记账余额校验失败（Running Balance 不一致） |
| `"2024"` | `BUFFER_POSTING_LOCK_UPGRADE_FAILED` | 锁升级后仍失败，需人工介入 |
| `"2025"` | `BUFFER_POSTING_RETRY_EXHAUSTED` | 缓冲记账重试次数已达上限 |

---

## 7. BufferPostingEngineDomainService 核心方法

```java
@Service
@RequiredArgsConstructor
public class BufferPostingEngineDomainService {

    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final SubAccountRepository subAccountRepository;
    private final SubAccountDetailRepository subAccountDetailRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;
    private final RunningBalanceValidator runningBalanceValidator;

    /**
     * 逐条缓冲记账（buffer_mode=1）
     * 单笔独立事务，失败不中断
     */
    public BatchPostingResult executeSinglePosting(LocalDate accountingDate, int maxBatchSize);

    /**
     * 日间批量缓冲记账（buffer_mode=2）
     * 按账户汇总，一次事务更新一个账户
     */
    public BatchPostingResult executeBatchPosting(LocalDate accountingDate, int maxBatchSize);

    /**
     * 分片扫描缓冲记账（用于 Job 分片执行）
     */
    public BatchPostingResult executeShardedPosting(
            LocalDate accountingDate, Long shardingStart, Long shardingEnd, int maxBatchSize);

    /**
     * 单笔缓冲明细过账（核心逻辑，被上述方法调用）
     */
    public void processSingleDetail(BufferPostingDetailPO detail);

    /**
     * 按账户汇总过账（buffer_mode=2 专用）
     */
    public void processAccountSummary(
            String accountNo, LocalDate accountingDate, List<BufferPostingDetailPO> details);

    /**
     * 锁升级执行（乐观锁 → 悲观锁 → 告警）
     */
    public boolean executeWithLockUpgrade(
            String accountNo, BigDecimal amount, int debitCredit, int balanceDirection);
}
```

---

## 8. 编码要点

### 8.1 事务边界

```
- 逐条缓冲：每笔明细独立 TransactionTemplate 事务
- 日间批量：每个账户独立 TransactionTemplate 事务（该账户下所有缓冲明细在同一事务中）
- 分布式锁包裹整个事务执行
- Job 扫描为非事务性，逐笔/逐账户 try-catch
```

### 8.2 严禁负数运算

```java
// 正确做法：先校验后运算
BigDecimal availableBalance = subAccount.getBalance();
if (availableBalance.compareTo(amount) < 0) {
    throw new AccountException(ResultCode.INSUFFICIENT_BALANCE,
        "子账户余额不足: accountNo=" + accountNo + ", available=" + availableBalance + ", need=" + amount);
}
BigDecimal newBalance = availableBalance.subtract(amount);
```

### 8.3 凭证状态更新

```
单笔缓冲明细过账成功后：
  1. 更新对应分录状态：entry status = 2（已过账）
  2. 检查该凭证所有分录状态：
     - 全部为 2 → 凭证 status = 3（已过账），postTime = NOW()
     - 存在非 2 → 凭证 status = 2（过账中）
```

### 8.4 子账户明细快照

```java
// 写入 t_sub_account_detail
SubAccountDetailPO detailPO = new SubAccountDetailPO();
detailPO.setVoucherNo(bufferDetail.getVoucherNo());
detailPO.setEntryId(bufferDetail.getEntryId());
detailPO.setAccountNo(bufferDetail.getAccountNo());
detailPO.setBalanceType(bufferDetail.getDebitCredit() == 1 ? 1 : 2);
detailPO.setTradeTime(bufferDetail.getTradeTime());
detailPO.setDebitCredit(bufferDetail.getDebitCredit());
detailPO.setPreBalance(oldBalance);
detailPO.setAmount(bufferDetail.getAmount());
detailPO.setPostBalance(newBalance);
detailPO.setAccountingDate(bufferDetail.getAccountingDate());
detailPO.setSummary(bufferDetail.getSummary() != null ? bufferDetail.getSummary() : "缓冲记账");
subAccountDetailMapper.insertSubDetail(detailPO);
```

### 8.5 重试与限次

```
- 单笔缓冲明细最大重试次数：3 次
- retry_count >= 3 → 标记 FAILED，不再重试
- 失败原因记录到 fail_reason 字段
- 告警日志格式：[BUFFER-POSTING-FAILED] detailId=X, accountNo=Y, reason=Z, retryCount=3
```

### 8.6 分片计算

```java
// 分片值计算（与 Step 10 BufferPostingDomainService.calculateSharding 保持一致）
public Long calculateSharding(String accountNo) {
    return (long) Math.abs(accountNo.hashCode());
}

// Job 分片范围计算
long shardIndex = XxlJobHelper.getShardIndex();
long shardTotal = XxlJobHelper.getShardTotal();
long totalRange = Long.MAX_VALUE / 128;  // 假设 128 分片
long shardingStart = shardIndex * totalRange;
long shardingEnd = (shardIndex + 1) * totalRange - 1;
```

---

## 9. 完成标准（Checklist）

### P0 前置任务
- [X] P0-1/P0-3/P0-4: 使用 LambdaQueryWrapper 实现，不写 XML
- [X] P0-2: 聚合查询（GROUP BY + SUM）写 1 段 XML
- [X] P0-5: 确认 SubAccountMapper.updateBalance 可复用
- [X] P0-6: BufferPostingDetailRepository 封装完成

### Agent-B：缓冲记账引擎
- [X] `executeSinglePosting` 逐条缓冲（status=1→3/4，单笔失败不中断）
- [X] `executeBatchPosting` 日间批量（按账户汇总，一次事务）
- [X] 余额计算正确（借方/贷方 + 余额方向组合判断）
- [X] 严禁负数运算（先校验后运算）
- [X] 子账户余额更新含乐观锁校验
- [X] 子账户明细快照完整（preBalance / postBalance）
- [X] 凭证状态联动（全部过账 → POSTED，否则 POSTING）
- [X] 分布式锁包裹（`account:buffer:{accountNo}`）
- [X] 双重检查：加锁后再次查询余额

### Agent-C：定时任务 + 分片
- [X] `BufferPostingAsyncJobHandler` 异步逐条扫描（每 1 分钟）
- [X] `BufferPostingBatchJobHandler` 日间批量扫描（每 30 分钟）
- [X] `BufferPostingEodJobHandler` 日终批量扫描（每日 23:50）
- [X] 分片策略正确（按 sharding 范围扫描）
- [X] 单笔失败不中断 Job
- [X] 任务参数解析（会计日期可选，默认当日）

### Agent-D：Running Balance 校验
- [X] `validateRunningBalance` 校验逻辑正确
- [X] 差额阈值 0.000001
- [X] 告警日志格式正确
- [X] 日终批量后自动触发校验

### Agent-E：应用层 + Controller
- [X] `BufferPostingApplicationService` 编排 3 个用例
- [X] `BufferPostingController` 实现 3 个接口
- [X] DTO 完整（Request + Response + Assembler）
- [X] 参数校验：日期格式、bufferMode 枚举、分页参数

### TL Review
- [ ] 余额方向正确处理（借方/贷方余额增减逻辑正确）
- [ ] 严禁负数运算（先校验后运算）
- [ ] 事务边界正确（TransactionTemplate，非 @Transactional）
- [ ] 锁升级策略正确（乐观 → 悲观 → 告警）
- [ ] 分片策略不遗漏（同一账户在同一分片）
- [ ] Running Balance 校验准确（末条 postBalance = 账户实际余额）
- [ ] 凭证状态联动正确
- [ ] 子账户明细完整性（每笔余额变动均记录）
- [ ] Job 异常处理（单笔失败不中断 + 错误日志）

---

## 10. 与后续 Step 的关系

| Step | 依赖关系 |
|------|---------|
| Step 17（日切与试算平衡） | 日终批量缓冲记账必须在日切前完成；Running Balance 校验失败可能阻断日切 |
| Step 18（冲账与红冲） | 红冲场景可能产生新的缓冲明细 |
| Step 23（前端缓冲监控页面） | 依赖本 Step 的监控接口 |

---

## 11. 下一步行动

进入 **Step 17 · EOD & Trial Balance（日切与试算平衡）**，详见 `docs/prompt/step-17-eod.md`。
