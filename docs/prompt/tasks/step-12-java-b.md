# step-12-java-b · PostingMonitorDomainService + 异常治理 + 统计报表

> **Step 12 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 12 Java-A（批量过账领域服务 + P0 补充已完成）

---

## 1. 任务目标

实现过账监控领域服务，提供进度查询、统计报表、异常凭证治理能力。

核心职责：
1. **PostingMonitorDomainService**：过账进度监控（凭证/事务/批次维度）
2. **异常凭证治理**：重试（限次校验）+ 跳过（触发告警）+ 僵尸凭证检测
3. **统计报表**：按日期/业务线/交易码/渠道维度分组统计

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、幂等设计 |
| 3 | `docs/prompt/step-12-posting.md` | Step 12 总体任务说明（§4.3 进度监控 / §4.4 异常治理 / §4.5 统计报表） |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` / `t_business_record` DDL |
| 6 | `docs/design/domain-model.md` | 凭证域、流水域模型 |
| 7 | `accounting-core/.../application/service/PostingApplicationService.java` | Step 11 过账编排应用服务 |
| 8 | `accounting-core/.../repository/AccountingVoucherRepository.java` | 凭证仓储 |
| 9 | `accounting-core/.../repository/TransactionRepository.java` | 事务仓储 |
| 10 | `accounting-core/.../domain/service/PostingEngineDomainService.java` | Java-A 已实现 |

---

## 3. PostingMonitorDomainService（过账监控领域服务）

新建 `accounting-core/.../domain/service/PostingMonitorDomainService.java`。

```java
@Service
@RequiredArgsConstructor
public class PostingMonitorDomainService {

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final PostingEngineDomainService postingEngineDomainService;
    private final PostingApplicationService postingApplicationService;
}
```

### 方法签名

```java
/**
 * 查询凭证过账进度
 *
 * 是否记账：否（纯查询）
 */
public VoucherProgressData getVoucherProgress(String voucherNo);

/**
 * 查询事务过账进度
 *
 * 是否记账：否（纯查询）
 */
public TransactionProgressData getTransactionProgress(String txnNo);

/**
 * 查询过账统计报表（按维度分组）
 *
 * @param startDate   起始日期
 * @param endDate     结束日期
 * @param dimension   维度：date / businessCode / tradingCode / payChannel
 * @return 按维度分组的统计数据列表
 */
public List<PostingStatsData> getPostingStats(
    LocalDate startDate, LocalDate endDate, String dimension);

/**
 * 查询异常凭证列表
 *
 * @param status      异常状态：4=过账失败，2=过账中（僵尸凭证）
 * @param startDate   起始会计日期
 * @param endDate     结束会计日期
 */
public List<AbnormalVoucherData> getAbnormalVouchers(
    Integer status, LocalDate startDate, LocalDate endDate);

/**
 * 僵尸凭证检测：status=2(过账中) 且 postTime < NOW() - 1h
 *
 * 是否记账：是（更新凭证状态为 4 过账失败）
 * 异常处理：
 *   - [RuntimeException] → 独立事务包裹，不影响调用方
 */
public List<AbnormalVoucherData> detectZombieVouchers();

/**
 * 凭证重试（限次校验 + 委托 Step 11）
 *
 * 是否记账：是（委托 Step 11）
 * 异常处理：
 *   - [ServiceException] → 凭证状态非法 / 重试次数超限 → 阻断
 */
public PostingExecuteResult retryAbnormalVoucher(
    String voucherNo, String operatorName, String retryReason);

/**
 * 凭证跳过
 *
 * 是否记账：是（更新状态为 6 已跳过）
 * 异常处理：
 *   - [ServiceException] → 凭证状态非法 → 阻断
 */
public void skipAbnormalVoucher(
    String voucherNo, String operatorName, String skipReason);
```

---

## 4. getVoucherProgress 执行流程（凭证维度）

```
输入：voucherNo
  ↓
1. 查询凭证基本信息（t_accounting_voucher）
   ↓ 凭证不存在 → 返回 null 或抛异常（由上层决定）
  ↓
2. 查询凭证所有分录（t_accounting_voucher_entry）
  ↓
3. 按分录类型分组统计：
   - realTimeTotal =  is_unilateral=1 的分录总数
   - realTimePosted = is_unilateral=1 且 status=2 的分录数
   - asyncTotal =     is_unilateral=0 且 is_buffered=0 的分录总数
   - asyncPosted =    is_unilateral=0 且 is_buffered=0 且 status=2 的分录数
   - bufferTotal =    is_unilateral=0 且 is_buffered=1 的分录总数
   - bufferPosted =   is_unilateral=0 且 is_buffered=1 且 status=2 的分录数
     （当前阶段 bufferPosted 始终为 0，Step 16 完成后更新）
  ↓
4. 计算进度百分比：
   progress = (realTimePosted + asyncPosted) / (realTimeTotal + asyncTotal) × 100
   （排除缓冲分录，因为 Step 16 单独处理）
  ↓
5. 返回 VoucherProgressData
```

### VoucherProgressData 数据对象

```java
@Data
@AllArgsConstructor
public class VoucherProgressData {
    private String voucherNo;
    private Integer status;              // 凭证状态码
    private String statusDesc;           // 凭证状态描述
    private LocalDate accountingDate;
    private int totalEntries;            // 总分录数（含缓冲）
    private int realTimePosted;
    private int realTimeTotal;
    private int asyncPosted;
    private int asyncTotal;
    private int bufferPosted;            // Step 16 更新，当前为 0
    private int bufferTotal;
    private BigDecimal progressPercent;  // 进度百分比，排除缓冲
}
```

---

## 5. getTransactionProgress 执行流程（事务维度）

```
输入：txnNo
  ↓
1. 查询事务基本信息（t_transaction）
  ↓
2. 查询事务关联凭证（t_accounting_voucher WHERE txn_no = ?）
  ↓
3. 对每个关联凭证调用 getVoucherProgress(voucherNo)
  ↓
4. 汇总所有凭证的进度：
   - totalVouchers = 关联凭证总数
   - postedVouchers = status=3(已过账) 的凭证数
   - progress = postedVouchers / totalVouchers × 100
  ↓
5. 返回 TransactionProgressData
```

### TransactionProgressData 数据对象

```java
@Data
@AllArgsConstructor
public class TransactionProgressData {
    private String txnNo;
    private Integer status;              // 事务状态码
    private String statusDesc;
    private LocalDate accountingDate;
    private int totalVouchers;
    private int postedVouchers;
    private int processingVouchers;
    private int failedVouchers;
    private BigDecimal progressPercent;
    private List<VoucherProgressData> voucherProgressList;
}
```

---

## 6. getPostingStats 执行流程（统计报表）

```
输入：startDate, endDate, dimension
  ↓
1. 按维度查询统计数据：
   - dimension=date:     按 accounting_date 分组
   - dimension=businessCode: 按 business_code 分组
   - dimension=tradingCode:  按 trading_code 分组
   - dimension=payChannel:   按 pay_channel 分组
  ↓
2. 对每个分组：
   a. 查询凭证按状态分组计数（countByStatusGroup）
   b. 查询事务耗时统计（selectDurationStats）
   c. 计算成功率 = success_count / total_count
  ↓
3. 返回统计数据列表
```

### PostingStatsData 数据对象

```java
@Data
@AllArgsConstructor
public class PostingStatsData {
    private String dimensionValue;  // 日期/业务线/交易码/渠道
    private int totalCount;
    private int successCount;       // status=3(已过账)
    private int failedCount;        // status=4(过账失败)
    private int processingCount;    // status=2(过账中)
    private int pendingCount;       // status=1(未过账)
    private BigDecimal successRate; // successCount / totalCount
    private Long avgDurationMs;
    private Long maxDurationMs;
    private Long minDurationMs;
}
```

---

## 7. detectZombieVouchers 执行流程

```
1. 查询僵尸凭证：
   SELECT * FROM t_accounting_voucher
   WHERE status = 2(过账中)
     AND post_time < NOW() - INTERVAL 1 HOUR
     AND is_delete = 0
  ↓
2. 对每个僵尸凭证（独立事务）：
   a. 更新凭证 status = 4(过账失败)
   b. 记录失败原因："过账超时，自动标记失败"
   c. 触发日志告警
  ↓
3. 返回僵尸凭证列表
```

### AbnormalVoucherData 数据对象

```java
@Data
@AllArgsConstructor
public class AbnormalVoucherData {
    private String voucherNo;
    private Integer status;
    private String statusDesc;
    private LocalDate accountingDate;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime postTime;
    private LocalDateTime updateTime;
}
```

---

## 8. retryAbnormalVoucher 执行流程

```
输入：voucherNo + operatorName + retryReason
  ↓
1. 查询凭证
   ↓ 凭证不存在 → 抛出 ServiceException(VOUCHER_NOT_FOUND)
   ↓ 凭证 status != 4(过账失败) 且 status != 2(过账中) → 抛出 ServiceException(VOUCHER_STATUS_ILLEGAL)
  ↓
2. 校验重试次数
   ↓ retryCount >= 5 → 抛出 ServiceException(POSTING_RETRY_EXHAUSTED)
  ↓
3. 调用 postingApplicationService.executePosting(voucherNo)
   ↓ 成功 → 返回过账结果
   ↓ 失败 → 抛出原异常（由上层 GlobalExceptionHandler 处理）
  ↓
4. 记录重试日志
```

> **重试限次说明**：最多 5 次（含系统自动重试 3 次 + 手动重试 2 次）。retryCount 字段如果 DDL 中存在则直接读取；如果不存在，可通过查询过账操作日志或简单限制手动重试次数不超过 2 次。

---

## 9. skipAbnormalVoucher 执行流程

```
输入：voucherNo + operatorName + skipReason
  ↓
1. 查询凭证
   ↓ 凭证不存在 → 抛出 ServiceException(VOUCHER_NOT_FOUND)
   ↓ 凭证 status != 4(过账失败) 且 status != 2(过账中) → 抛出 ServiceException(VOUCHER_STATUS_ILLEGAL)
  ↓
2. 在独立 TransactionTemplate 事务中：
   a. 更新凭证 status = 6(已跳过)
   b. 记录跳过原因和操作人
   c. 提交
  ↓
3. 触发告警日志（跳过意味着数据不一致，需要人工介入）
   log.error("[SKIP-VOUCHER] 凭证被跳过: voucherNo={}, operator={}, reason={}", ...)
```

---

## 10. 需要创建的文件清单

| 文件 | 说明 |
|------|------|
| `PostingMonitorDomainService.java` | 过账监控领域服务 |

---

## 11. 需要修改的文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `AccountingVoucherMapper.java` | 确认方法 | 确认 P0-1 补充的 `selectByStatusAndDateRange` 可用 |
| `TransactionMapper.java` | 确认方法 | 确认 P0-3 补充的方法可用 |
| `LocalMessageService.java` | 确认方法 | 确认 P0-6 补充的方法可用 |

---

## 12. 完成标准（Checklist）

- [ ] `PostingMonitorDomainService` 过账监控领域服务
- [ ] `getVoucherProgress` 凭证过账进度查询（按分录类型分组统计）
- [ ] 进度计算排除缓冲分录（Step 16 单独处理）
- [ ] `getTransactionProgress` 事务过账进度查询（汇总关联凭证进度）
- [ ] `getPostingStats` 过账统计报表（按日期/业务线/交易码/渠道分组）
- [ ] 统计数据正确计算成功率 = success_count / total_count
- [ ] 耗时统计仅统计 status=2(成功) 的事务
- [ ] `getAbnormalVouchers` 异常凭证列表查询（按状态+日期过滤）
- [ ] `detectZombieVouchers` 僵尸凭证检测（status=2 且 postTime < NOW() - 1h）
- [ ] 僵尸凭证自动标记为 4(过账失败)，记录失败原因
- [ ] `retryAbnormalVoucher` 凭证重试（限次校验 + 委托 Step 11）
- [ ] 重试次数校验（最多 5 次，超限抛出 POSTING_RETRY_EXHAUSTED）
- [ ] 凭证状态校验（仅允许 4/2 状态的凭证重试）
- [ ] `skipAbnormalVoucher` 凭证跳过（独立事务更新 status=6）
- [ ] 跳过操作触发日志告警（ERROR 级别）
- [ ] 凭证状态校验（仅允许 4/2 状态的凭证跳过）
- [ ] VoucherProgressData / TransactionProgressData / PostingStatsData / AbnormalVoucherData 数据对象
- [ ] 单测：进度计算、统计分组、僵尸凭证检测、重试限次、跳过流程
