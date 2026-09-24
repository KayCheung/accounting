# step-12-java-a · P0 补充 + PostingEngineDomainService + LocalMessageScanJob + BatchPostingJob

> **Step 12 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 11（事务管理已完成 — PostingApplicationService / LocalMessageService 已就绪）

---

## 1. 任务目标

完成 Step 12 七项前置补充任务（P0-1~P0-7），并实现批量过账领域服务、本地消息扫描 Job、批量过账 Job。

核心职责：
1. 补充 Mapper/Repository 方法（AccountingVoucherMapper、TransactionMapper、LocalMessageMapper）
2. 实现 PostingEngineDomainService 批量过账领域服务（按条件查询 → 逐笔委托 Step 11 → 统计结果）
3. 实现 LocalMessageScanJob XXL-JOB Handler（扫描 t_local_message → 投递 MQ → 失败重试）
4. 实现 BatchPostingJob XXL-JOB Handler（查询当日待过账凭证 → 逐笔执行）

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范（严禁 `@Transactional`）、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、借贷方向约束 |
| 3 | `docs/prompt/step-12-posting.md` | Step 12 总体任务说明 |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` / `t_business_record` DDL |
| 6 | `docs/sql/6-infra.sql` | `t_local_message` / `t_message_receipt` DDL |
| 7 | `docs/design/domain-model.md` | 账户域、凭证域模型 |
| 8 | `accounting-core/.../application/service/PostingApplicationService.java` | Step 11 过账编排应用服务 |
| 9 | `accounting-core/.../infrastructure/messaging/LocalMessageService.java` | 本地消息服务 |
| 10 | `accounting-core/.../repository/AccountingVoucherRepository.java` | 凭证仓储 |
| 11 | `accounting-core/.../repository/TransactionRepository.java` | 事务仓储 |
| 12 | `accounting-job/` 模块已有 Handler 示例 | XXL-JOB 注册模式 |

---

## 3. P0 前置补充任务（开始编码前必须完成）

### P0-1: AccountingVoucherMapper 补充批量查询方法

在 `AccountingVoucherMapper.java` 中追加两个方法：

```java
/**
 * 按凭证状态和会计日期范围查询凭证（批量过账 + 监控查询用）
 */
List<AccountingVoucherPO> selectByStatusAndDateRange(
    @Param("status") Integer status,
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    @Param("businessCode") String businessCode,
    @Param("limit") int limit);

/**
 * 按凭证状态分组统计（统计报表用）
 */
List<Map<String, Object>> countByStatusGroup(
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    @Param("businessCode") String businessCode);
```

对应 XML（在 `resources/mapper/AccountingVoucherMapper.xml` 中追加）：

```xml
<select id="selectByStatusAndDateRange" resultType="AccountingVoucherPO">
    SELECT * FROM t_accounting_voucher
    WHERE is_delete = 0
    <if test="status != null">
    AND status = #{status}
    </if>
    <if test="startDate != null">
    AND accounting_date &gt;= #{startDate}
    </if>
    <if test="endDate != null">
    AND accounting_date &lt;= #{endDate}
    </if>
    <if test="businessCode != null and businessCode != ''">
    AND business_code = #{businessCode}
    </if>
    AND posting_type IN ('REALTIME', 'ASYNC')
    ORDER BY create_time ASC
    LIMIT #{limit}
</select>

<select id="countByStatusGroup" resultType="java.util.Map">
    SELECT
        status,
        COUNT(*) AS count,
        business_code
    FROM t_accounting_voucher
    WHERE is_delete = 0
    <if test="startDate != null">
    AND accounting_date &gt;= #{startDate}
    </if>
    <if test="endDate != null">
    AND accounting_date &lt;= #{endDate}
    </if>
    <if test="businessCode != null and businessCode != ''">
    AND business_code = #{businessCode}
    </if>
    GROUP BY status, business_code
</select>
```

### P0-2: AccountingVoucherRepository 补充批量查询方法

在 `AccountingVoucherRepository.java` 中追加封装方法：

```java
public List<AccountingVoucherPO> selectByStatusAndDateRange(
    Integer status, LocalDate startDate, LocalDate endDate,
    String businessCode, int limit);

public List<Map<String, Object>> countByStatusGroup(
    LocalDate startDate, LocalDate endDate, String businessCode);
```

### P0-3: TransactionMapper 补充统计方法

在 `TransactionMapper.java` 中追加两个方法：

```java
/**
 * 按事务状态和日期范围统计数量
 */
int countByStatusAndDateRange(
    @Param("status") Integer status,
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate);

/**
 * 查询事务耗时统计（AVG/MAX/MIN，仅统计已完成事务）
 */
Map<String, Object> selectDurationStats(
    @Param("startDate") LocalDate startDate,
    @Param("endDate") LocalDate endDate,
    @Param("businessCode") String businessCode);
```

对应 XML：

```xml
<select id="countByStatusAndDateRange" resultType="java.lang.Integer">
    SELECT COUNT(*) FROM t_transaction
    WHERE is_delete = 0
    <if test="status != null">
    AND status = #{status}
    </if>
    <if test="startDate != null">
    AND accounting_date &gt;= #{startDate}
    </if>
    <if test="endDate != null">
    AND accounting_date &lt;= #{endDate}
    </if>
</select>

<select id="selectDurationStats" resultType="java.util.Map">
    SELECT
        ROUND(AVG(
            UNIX_TIMESTAMP(finish_time) * 1000 - UNIX_TIMESTAMP(create_time) * 1000
        )) AS avg_duration_ms,
        ROUND(MAX(
            UNIX_TIMESTAMP(finish_time) * 1000 - UNIX_TIMESTAMP(create_time) * 1000
        )) AS max_duration_ms,
        ROUND(MIN(
            UNIX_TIMESTAMP(finish_time) * 1000 - UNIX_TIMESTAMP(create_time) * 1000
        )) AS min_duration_ms
    FROM t_transaction
    WHERE is_delete = 0
    AND status = 2
    AND finish_time != '1970-01-01 00:00:00'
    <if test="startDate != null">
    AND accounting_date &gt;= #{startDate}
    </if>
    <if test="endDate != null">
    AND accounting_date &lt;= #{endDate}
    </if>
</select>
```

> **P0-2 修复**：MySQL 5.7 不支持 `TIMESTAMPDIFF(MILLISECOND, ...)`。使用 `UNIX_TIMESTAMP()` 差值计算秒级精度后 ×1000 得到毫秒。额外过滤 `finish_time != '1970-01-01 00:00:00'` 防止默认值产生异常数据。

### P0-4: TransactionRepository 补充统计方法

在 `TransactionRepository.java` 中追加封装：

```java
public int countByStatusAndDateRange(Integer status, LocalDate startDate, LocalDate endDate);
public Map<String, Object> selectDurationStats(LocalDate startDate, LocalDate endDate, String businessCode);
```

### P0-5: 确认 XXL-JOB Handler 注册可用

读取 `accounting-job` 模块已有 Handler 示例，确认 `@XxlJob` 注解注册模式。新建 Job Handler 文件时放在 `accounting-job/src/main/java/com/kltb/accounting/job/` 目录下。

### P0-6: LocalMessageService 补充查询方法

在 `LocalMessageService.java` 中追加方法：

```java
/**
 * 查询待发送的本地消息（status=1 且 retry_count < max_retry 且 next_retry_time <= now）
 */
public List<LocalMessagePO> selectPendingMessages(int limit);
```

### P0-7: LocalMessageMapper 补充查询方法

在 `LocalMessageMapper.java` 中追加方法：

```java
/**
 * 按状态和重试限制查询待发送消息
 */
List<LocalMessagePO> selectByStatusAndRetryLimit(
    @Param("status") Integer status,
    @Param("now") LocalDateTime now,
    @Param("limit") int limit);

/**
 * 更新消息重试信息（重试次数 +1 + 下次重试时间 + 错误原因）
 */
int updateRetryInfo(
    @Param("messageId") String messageId,
    @Param("retryCount") int retryCount,
    @Param("nextRetryTime") LocalDateTime nextRetryTime,
    @Param("errorCode") String errorCode);
```

对应 XML：

```xml
<select id="selectByStatusAndRetryLimit" resultType="LocalMessagePO">
    SELECT * FROM t_local_message
    WHERE status = #{status}
    AND retry_count &lt; max_retry
    AND next_retry_time &lt;= #{now}
    AND is_delete = 0
    ORDER BY create_time ASC
    LIMIT #{limit}
</select>

<update id="updateRetryInfo">
    UPDATE t_local_message
    SET retry_count = #{retryCount},
        next_retry_time = #{nextRetryTime},
        error_code = #{errorCode},
        update_time = NOW()
    WHERE message_id = #{messageId}
</update>
```

### P0-8: DDL 列补充（t_accounting_voucher）

在 `docs/sql/2-voucher.sql` 中追加 ALTER 语句：

```sql
-- t_accounting_voucher 新增列（P0-8 修复）
ALTER TABLE t_accounting_voucher ADD COLUMN fail_reason VARCHAR(500) DEFAULT NULL COMMENT '过账失败原因';
ALTER TABLE t_accounting_voucher ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT '手动重试次数';
ALTER TABLE t_accounting_voucher ADD COLUMN skip_flag TINYINT NOT NULL DEFAULT 0 COMMENT '跳过标记：0-未跳过,1-人工跳过';
```

同步在 `AccountingVoucherPO.java` 中追加属性：

```java
private String failReason;
private Integer retryCount;
private Integer skipFlag;  // 0-未跳过, 1-人工跳过
```

> P0-3 修复说明：凭证状态复用现有 status=4(过账失败)，通过 skip_flag 区分"系统判定失败"和"人工跳过"，不引入新的状态值 6。

---

## 4. PostingEngineDomainService（批量过账领域服务）

新建 `accounting-core/.../domain/service/PostingEngineDomainService.java`。

> **职责**：批量过账核心逻辑，不含事务，逐笔委托 Step 11 的 PostingApplicationService。

```java
@Service
@RequiredArgsConstructor
public class PostingEngineDomainService {

    private final PostingApplicationService postingApplicationService;
    private final AccountingVoucherRepository accountingVoucherRepository;
}
```

### executeBatchPosting 执行流程

```
输入：startDate, endDate, businessCode, maxBatchSize
  ↓
1. 查询待过账凭证列表（status=1 未过账，posting_type IN ('REALTIME','ASYNC')）
2. 逐笔凭证：
   try {
     postingApplicationService.executePosting(voucherNo) → 成功计数 +1
   } catch (Exception e) {
     失败计数 +1，记录 voucherNo + failReason → 不中断
   }
3. 返回 BatchPostingResult（总数 / 成功 / 失败 / 失败列表 / 总耗时）
```

### 方法签名

```java
/**
 * 批量过账：按条件查询待过账凭证 → 逐笔执行过账
 *
 * 是否记账：是（委托 Step 11）
 * 异常处理：
 *   - 单笔失败不抛异常，记录到 failedList 后继续处理
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
```

### BatchPostingResult 领域结果对象

```java
@Data
@AllArgsConstructor
public static class BatchPostingResult {
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

---

## 5. LocalMessageScanJobHandler（accounting-job 模块）

新建 `accounting-job/.../LocalMessageScanJobHandler.java`。

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class LocalMessageScanJobHandler {

    private final LocalMessageService localMessageService;
    private final RocketMQTemplate rocketMQTemplate;  // 或等价 Producer Bean
    private final MessageReceiptMapper messageReceiptMapper;

    private static final int BATCH_SIZE = 100;
    private static final String POSTING_TOPIC = "posting_topic";
    private static final String POSTING_TAG = "POSTING_ENTRY";

    /**
     * XXL-JOB Handler
     * 调度配置：每 30 秒执行一次
     * 路由策略：FIRST（单实例执行）
     *
     * 扫描 t_local_message（status=1 待发送）→ 投递 MQ → 更新状态
     */
    @XxlJob("localMessageScanJob")
    public ReturnT<String> execute() {
        // 1. 查询待发送消息
        List<LocalMessagePO> messages = localMessageService.selectPendingMessages(BATCH_SIZE);

        int success = 0;
        int failed = 0;

        for (LocalMessagePO message : messages) {
            try {
                // 2. 投递 MQ
                rocketMQTemplate.syncSend(POSTING_TOPIC,
                    new org.apache.rocketmq.common.message.Message(
                        POSTING_TAG,
                        message.getBusinessKey(),
                        message.getPayload().getBytes(StandardCharsets.UTF_8)
                    ));

                // 3. 更新状态为已发送
                localMessageService.markSent(message.getBusinessKey());
                success++;

                log.info("[LOCAL-MESSAGE-SCAN] 消息投递成功: messageId={}", message.getMessageId());
            } catch (Exception e) {
                // 4. 重试计数 +1，计算下次重试时间（指数退避）
                int newRetryCount = message.getRetryCount() + 1;
                LocalDateTime nextRetryTime = calculateNextRetryTime(newRetryCount);

                localMessageService.updateRetryInfo(message.getMessageId(), newRetryCount, nextRetryTime, e.getMessage());

                if (newRetryCount >= message.getMaxRetry()) {
                    localMessageService.markFailed(message.getMessageId());
                    log.error("[LOCAL-MESSAGE-SCAN] 消息投递失败且重试耗尽: messageId={}", message.getMessageId(), e);
                } else {
                    log.warn("[LOCAL-MESSAGE-SCAN] 消息投递失败，等待重试: messageId={}, retry={}/{}",
                        message.getMessageId(), newRetryCount, message.getMaxRetry(), e);
                }
                failed++;
            }
        }

        log.info("[LOCAL-MESSAGE-SCAN] 扫描完成: total={}, success={}, failed={}",
            messages.size(), success, failed);
        return ReturnT.SUCCESS;
    }

    /**
     * 指数退避：10s → 30s → 60s
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        long delaySeconds = switch (retryCount) {
            case 1 -> 10;
            case 2 -> 30;
            default -> 60;
        };
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
}
```

> **注意**：`RocketMQTemplate` 的 Bean 名称和注入方式需与项目实际 RocketMQ 集成方式保持一致（Aliyun ONS 封装）。如果项目使用自定义 Producer，请替换为对应的 Bean。

---

## 6. BatchPostingJobHandler（accounting-job 模块）

新建 `accounting-job/.../BatchPostingJobHandler.java`。

```java
@Component
@Slf4j
@RequiredArgsConstructor
public class BatchPostingJobHandler {

    private final PostingEngineDomainService postingEngineDomainService;

    /**
     * XXL-JOB Handler
     * 调度配置：每 5 分钟执行一次
     * 路由策略：FIRST（单实例执行）
     *
     * 查询当日待过账凭证 → 逐笔执行过账
     */
    @XxlJob("batchPostingJob")
    public ReturnT<String> execute() {
        LocalDate today = LocalDate.now();
        log.info("[BATCH-POSTING-JOB] 开始执行: date={}", today);

        long startTime = System.currentTimeMillis();
        BatchPostingResult result = postingEngineDomainService.executeBatchPosting(
            today, today, null, 50);

        long duration = System.currentTimeMillis() - startTime;
        log.info("[BATCH-POSTING-JOB] 执行完成: total={}, success={}, failed={}, duration={}ms",
            result.getTotalCount(), result.getSuccessCount(), result.getFailedCount(), duration);

        if (result.getFailedCount() > 0) {
            log.warn("[BATCH-POSTING-JOB] 部分失败明细: {}",
                result.getFailedList().stream()
                    .map(f -> f.getVoucherNo() + "(" + f.getFailReason() + ")")
                    .collect(Collectors.joining(", ")));
        }

        return ReturnT.SUCCESS;
    }
}
```

---

## 7. 需要创建的文件清单

| 文件 | 模块 | 说明 |
|------|------|------|
| `PostingEngineDomainService.java` | accounting-core | 批量过账领域服务 |
| `LocalMessageScanJobHandler.java` | accounting-job | 本地消息扫描 Job |
| `BatchPostingJobHandler.java` | accounting-job | 批量过账 Job |

---

## 8. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountingVoucherMapper.java` | 追加方法 | `selectByStatusAndDateRange` + `countByStatusGroup` |
| `AccountingVoucherMapper.xml` | 追加 SQL | 对应 XML |
| `AccountingVoucherRepository.java` | 追加方法 | 封装 P0-1 |
| `TransactionMapper.java` | 追加方法 | `countByStatusAndDateRange` + `selectDurationStats` |
| `TransactionMapper.xml` | 追加 SQL | 对应 XML |
| `TransactionRepository.java` | 追加方法 | 封装 P0-3 |
| `LocalMessageMapper.java` | 追加方法 | `selectByStatusAndRetryLimit` + `updateRetryInfo` |
| `LocalMessageMapper.xml` | 追加 SQL | 对应 XML |
| `LocalMessageService.java` | 追加方法 | `selectPendingMessages` + `updateRetryInfo` + `markFailed` |

---

## 9. 完成标准（Checklist）

- [ ] P0-1: `AccountingVoucherMapper` 补充 `selectByStatusAndDateRange` + `countByStatusGroup` + XML
- [ ] P0-2: `AccountingVoucherRepository` 补充批量查询方法
- [ ] P0-3: `TransactionMapper` 补充 `countByStatusAndDateRange` + `selectDurationStats` + XML
- [ ] P0-4: `TransactionRepository` 补充统计方法
- [ ] P0-5: 确认 XXL-JOB Handler 注册模式可用
- [ ] P0-6: `LocalMessageService` 补充 `selectPendingMessages`
- [ ] P0-7: `LocalMessageMapper` 补充 `selectByStatusAndRetryLimit` + `updateRetryInfo` + XML
- [ ] `PostingEngineDomainService` 批量过账领域服务
- [ ] `executeBatchPosting` 按条件查询 → 逐笔委托 Step 11 → 统计结果
- [ ] 单笔失败不中断（try-catch continue 模式）
- [ ] `selectPendingVouchers` 按状态+日期范围查询（排除 BUFFER 类型）
- [ ] `LocalMessageScanJobHandler` XXL-JOB Handler（每 30 秒执行，LIMIT 100）
- [ ] 本地消息扫描：查询 → 投递 MQ → 更新状态 → 失败指数退避重试
- [ ] 重试耗尽后标记 failed，触发日志告警
- [ ] `BatchPostingJobHandler` XXL-JOB Handler（每 5 分钟执行，LIMIT 50）
- [ ] 批量过账 Job 查询当日待过账凭证 → 逐笔执行
- [ ] 单测：批量过账正常流程、单笔失败不中断、本地消息扫描正常流程、消息重试退避
