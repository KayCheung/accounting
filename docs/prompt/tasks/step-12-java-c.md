# step-12-java-c · PostingEngineApplicationService + PostingEngineController

> **Step 12 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 12 Java-A（批量过账领域服务 + Job Handler 已完成）+ Java-B（过账监控领域服务已完成）

---

## 1. 任务目标

实现过账引擎应用服务、Controller、DTO、Assembler。

核心职责：
1. **PostingEngineApplicationService**：过账引擎用例编排（批量过账、进度查询、统计报表、异常治理）
2. **PostingEngineController**：7 个接口（批量过账、凭证/事务进度、统计报表、异常重试/跳过/列表）
3. **DTO**：BatchPostingRequest / PostingRetryRequest / PostingSkipRequest 及对应 Response
4. **PostingEngineAssembler**：PO/Result ↔ DTO 转换

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 财务律法、状态机、幂等设计 |
| 3 | `docs/prompt/step-12-posting.md` | Step 12 总体任务说明（§6 接口契约） |
| 4 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_transaction` DDL |
| 6 | `docs/design/domain-model.md` | 凭证域、流水域模型 |
| 7 | `accounting-core/.../domain/service/PostingEngineDomainService.java` | Java-A 已实现 |
| 8 | `accounting-core/.../domain/service/PostingMonitorDomainService.java` | Java-B 已实现 |
| 9 | `accounting-core/.../application/service/PostingApplicationService.java` | Step 11 过账编排应用服务 |
| 10 | `docs/prompt/tasks/step-11-java-c.md` | Step 11 Java-C DTO 定义参考 |

---

## 3. PostingEngineApplicationService（过账引擎应用服务）

新建 `accounting-core/.../application/service/PostingEngineApplicationService.java`。

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PostingEngineApplicationService {

    private final PostingEngineDomainService postingEngineDomainService;
    private final PostingMonitorDomainService postingMonitorDomainService;
    private final PostingEngineAssembler assembler;
}
```

### 方法列表

```java
/**
 * 批量过账（编排层）
 * 委托 PostingEngineDomainService.executeBatchPosting()
 */
public BatchPostingResponse executeBatchPosting(BatchPostingRequest request);

/**
 * 查询凭证过账进度
 * 委托 PostingMonitorDomainService.getVoucherProgress()
 */
public PostingMonitorResponse getVoucherProgress(String voucherNo);

/**
 * 查询事务过账进度
 * 委托 PostingMonitorDomainService.getTransactionProgress()
 */
public PostingMonitorResponse getTransactionProgress(String txnNo);

/**
 * 查询过账统计报表
 * 委托 PostingMonitorDomainService.getPostingStats()
 */
public PostingStatsResponse getPostingStats(
    LocalDate startDate, LocalDate endDate, String dimension);

/**
 * 异常凭证重试
 * 委托 PostingMonitorDomainService.retryAbnormalVoucher()
 */
public PostingRetryResponse retryAbnormalVoucher(PostingRetryRequest request);

/**
 * 异常凭证跳过
 * 委托 PostingMonitorDomainService.skipAbnormalVoucher()
 */
public void skipAbnormalVoucher(PostingSkipRequest request);

/**
 * 查询异常凭证列表
 * 委托 PostingMonitorDomainService.getAbnormalVouchers()
 */
public List<AbnormalVoucherResponse> getAbnormalVouchers(
    Integer status, LocalDate startDate, LocalDate endDate);
```

> **注意**：此应用服务为**编排层**，不包含任何业务逻辑。所有业务逻辑下沉到 Java-A/B 的领域服务。应用服务只做参数校验、DTO 转换、委托调用。

---

## 4. PostingEngineController（7 个接口）

新建 `accounting-core/.../controller/PostingEngineController.java`。

```java
@RestController
@RequestMapping("/accounting/posting-engine")
@RequiredArgsConstructor
@Validated
@Tag(name = "过账引擎", description = "批量过账、进度监控、异常治理")
public class PostingEngineController {

    private final PostingEngineApplicationService postingEngineApplicationService;

    /**
     * 批量过账
     */
    @PostMapping("/batch")
    @Operation(summary = "批量过账", description = "按会计日期范围批量执行过账，单笔失败不中断")
    public ApiResponse<BatchPostingResponse> executeBatchPosting(
        @Valid @RequestBody BatchPostingRequest request) {
        return ApiResponse.success(postingEngineApplicationService.executeBatchPosting(request));
    }

    /**
     * 查询凭证过账进度
     */
    @GetMapping("/monitor/voucher/{voucherNo}")
    @Operation(summary = "凭证过账进度", description = "查询单个凭证的过账进度（实时/异步/缓冲分录分组统计）")
    public ApiResponse<PostingMonitorResponse> getVoucherProgress(
        @PathVariable String voucherNo) {
        return ApiResponse.success(postingEngineApplicationService.getVoucherProgress(voucherNo));
    }

    /**
     * 查询事务过账进度
     */
    @GetMapping("/monitor/transaction/{txnNo}")
    @Operation(summary = "事务过账进度", description = "查询事务关联凭证的过账进度汇总")
    public ApiResponse<PostingMonitorResponse> getTransactionProgress(
        @PathVariable String txnNo) {
        return ApiResponse.success(postingEngineApplicationService.getTransactionProgress(txnNo));
    }

    /**
     * 过账统计报表
     */
    @GetMapping("/monitor/stats")
    @Operation(summary = "过账统计报表", description = "按日期/业务线/交易码/渠道维度统计过账数据")
    public ApiResponse<PostingStatsResponse> getPostingStats(
        @RequestParam @NotNull LocalDate startDate,
        @RequestParam @NotNull LocalDate endDate,
        @RequestParam(defaultValue = "date") String dimension) {
        return ApiResponse.success(
            postingEngineApplicationService.getPostingStats(startDate, endDate, dimension));
    }

    /**
     * 异常凭证重试
     */
    @PostMapping("/abnormal/retry")
    @Operation(summary = "异常凭证重试", description = "手动重试过账失败凭证（最多 5 次）")
    public ApiResponse<PostingRetryResponse> retryAbnormalVoucher(
        @Valid @RequestBody PostingRetryRequest request) {
        return ApiResponse.success(postingEngineApplicationService.retryAbnormalVoucher(request));
    }

    /**
     * 异常凭证跳过
     */
    @PostMapping("/abnormal/skip")
    @Operation(summary = "异常凭证跳过", description = "将凭证标记为已跳过（需二级确认）")
    public ApiResponse<Void> skipAbnormalVoucher(
        @Valid @RequestBody PostingSkipRequest request) {
        postingEngineApplicationService.skipAbnormalVoucher(request);
        return ApiResponse.success();
    }

    /**
     * 异常凭证列表
     */
    @GetMapping("/abnormal/list")
    @Operation(summary = "异常凭证列表", description = "查询过账失败或过账中的凭证")
    public ApiResponse<List<AbnormalVoucherResponse>> getAbnormalVouchers(
        @RequestParam(required = false) Integer status,
        @RequestParam(required = false) LocalDate startDate,
        @RequestParam(required = false) LocalDate endDate) {
        return ApiResponse.success(
            postingEngineApplicationService.getAbnormalVouchers(status, startDate, endDate));
    }
}
```

---

## 5. DTO 定义

### BatchPostingRequest（`accounting-api/.../request/BatchPostingRequest.java`）

```java
@Data
public class BatchPostingRequest {

    @NotNull(message = "起始日期不能为空")
    private LocalDate startDate;

    private LocalDate endDate;  // 默认等于 startDate

    @Size(max = 32, message = "业务线编码长度不能超过32")
    private String businessCode;  // null 表示全部业务线

    @Min(value = 1, message = "批次大小最小为1")
    @Max(value = 200, message = "批次大小最大为200")
    private Integer maxBatchSize = 50;  // 默认 50
}
```

### PostingRetryRequest（`accounting-api/.../request/PostingRetryRequest.java`）

```java
@Data
public class PostingRetryRequest {

    @NotBlank(message = "凭证号不能为空")
    @Size(max = 64, message = "凭证号长度不能超过64")
    private String voucherNo;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人姓名长度不能超过32")
    private String operatorName;

    @NotBlank(message = "重试原因不能为空")
    @Size(max = 200, message = "重试原因长度不能超过200")
    private String retryReason;
}
```

### PostingSkipRequest（`accounting-api/.../request/PostingSkipRequest.java`）

```java
@Data
public class PostingSkipRequest {

    @NotBlank(message = "凭证号不能为空")
    @Size(max = 64, message = "凭证号长度不能超过64")
    private String voucherNo;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人姓名长度不能超过32")
    private String operatorName;

    @NotBlank(message = "跳过原因不能为空")
    @Size(max = 200, message = "跳过原因长度不能超过200")
    private String skipReason;
}
```

### BatchPostingResponse（`accounting-api/.../response/BatchPostingResponse.java`）

```java
@Data
public class BatchPostingResponse {

    /** 总处理凭证数 */
    private int totalCount;

    /** 成功过账数 */
    private int successCount;

    /** 过账失败数 */
    private int failedCount;

    /** 总耗时（毫秒） */
    private long totalDurationMs;

    /** 失败凭证列表 */
    private List<FailedVoucherInfo> failedList;

    @Data
    @AllArgsConstructor
    public static class FailedVoucherInfo {
        private String voucherNo;
        private String failReason;
    }
}
```

### PostingMonitorResponse（`accounting-api/.../response/PostingMonitorResponse.java`）

```java
@Data
public class PostingMonitorResponse {

    /** 凭证号或事务号 */
    private String no;

    /** 状态 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 总分录数/凭证数 */
    private int totalCount;

    /** 已完成数 */
    int postedCount;

    /** 处理中数 */
    private int processingCount;

    /** 失败数 */
    private int failedCount;

    /** 进度百分比 */
    private BigDecimal progressPercent;

    /** 分录/凭证详情列表（可选） */
    private List<PostingDetailInfo> detailList;

    @Data
    public static class PostingDetailInfo {
        private String entryId;
        private String subjectCode;
        private String accountNo;
        private Integer debitCredit;
        private BigDecimal amount;
        private Integer status;
        private String statusDesc;
        private Integer unilateral;
        private Integer buffered;
    }
}
```

### PostingStatsResponse（`accounting-api/.../response/PostingStatsResponse.java`）

```java
@Data
public class PostingStatsResponse {

    /** 起始日期 */
    private LocalDate startDate;

    /** 结束日期 */
    private LocalDate endDate;

    /** 统计维度：date / businessCode / tradingCode / payChannel */
    private String dimension;

    /** 统计数据列表 */
    private List<StatsGroupData> statsList;

    @Data
    public static class StatsGroupData {
        /** 维度值 */
        private String dimensionValue;

        /** 总凭证数 */
        private int totalCount;

        /** 已过账数 */
        private int successCount;

        /** 过账失败数 */
        private int failedCount;

        /** 过账中数 */
        private int processingCount;

        /** 未过账数 */
        private int pendingCount;

        /** 成功率 */
        private BigDecimal successRate;

        /** 平均耗时（毫秒） */
        private Long avgDurationMs;

        /** 最大耗时（毫秒） */
        private Long maxDurationMs;

        /** 最小耗时（毫秒） */
        private Long minDurationMs;
    }
}
```

### PostingRetryResponse（`accounting-api/.../response/PostingRetryResponse.java`）

```java
@Data
public class PostingRetryResponse {

    /** 凭证号 */
    private String voucherNo;

    /** 重试后凭证状态 */
    private Integer voucherStatus;

    /** 重试后凭证状态描述 */
    private String voucherStatusDesc;

    /** 重试是否成功 */
    private boolean success;
}
```

### AbnormalVoucherResponse（`accounting-api/.../response/AbnormalVoucherResponse.java`）

```java
@Data
public class AbnormalVoucherResponse {

    /** 凭证号 */
    private String voucherNo;

    /** 凭证状态 */
    private Integer status;

    /** 状态描述 */
    private String statusDesc;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 失败原因 */
    private String failReason;

    /** 重试次数 */
    private Integer retryCount;

    /** 过账时间 */
    private LocalDateTime postTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
```

---

## 6. PostingEngineAssembler（PO/Result ↔ DTO 转换）

新建 `accounting-core/.../application/assembler/PostingEngineAssembler.java`。

```java
@Component
public class PostingEngineAssembler {

    /**
     * 批量过账结果 → Response DTO
     */
    public BatchPostingResponse toBatchResponse(
        PostingEngineDomainService.BatchPostingResult result);

    /**
     * 凭证过账进度 → Response DTO
     */
    public PostingMonitorResponse toVoucherMonitorResponse(
        VoucherProgressData progressData);

    /**
     * 事务过账进度 → Response DTO
     */
    public PostingMonitorResponse toTransactionMonitorResponse(
        TransactionProgressData progressData);

    /**
     * 统计数据列表 → Response DTO
     */
    public PostingStatsResponse toStatsResponse(
        LocalDate startDate,
        LocalDate endDate,
        String dimension,
        List<PostingStatsData> statsDataList);

    /**
     * 重试结果 → Response DTO
     */
    public PostingRetryResponse toRetryResponse(
        String voucherNo, boolean success, Integer status, String statusDesc);

    /**
     * 异常凭证列表 → Response DTO 列表
     */
    public List<AbnormalVoucherResponse> toAbnormalResponses(
        List<AbnormalVoucherData> dataList);
}
```

---

## 7. 需要创建的文件清单

| 文件 | 模块 | 说明 |
|------|------|------|
| `PostingEngineApplicationService.java` | accounting-core | 过账引擎应用服务（编排层） |
| `PostingEngineController.java` | accounting-core | 过账引擎 Controller（7 个接口） |
| `PostingEngineAssembler.java` | accounting-core | PO/Result ↔ DTO 转换器 |
| `BatchPostingRequest.java` | accounting-api | 批量过账请求 DTO |
| `PostingRetryRequest.java` | accounting-api | 凭证重试请求 DTO |
| `PostingSkipRequest.java` | accounting-api | 凭证跳过请求 DTO |
| `BatchPostingResponse.java` | accounting-api | 批量过账结果响应 DTO |
| `PostingMonitorResponse.java` | accounting-api | 过账监控响应 DTO |
| `PostingStatsResponse.java` | accounting-api | 过账统计报表响应 DTO |
| `PostingRetryResponse.java` | accounting-api | 凭证重试结果响应 DTO |
| `AbnormalVoucherResponse.java` | accounting-api | 异常凭证响应 DTO |

---

## 8. 完成标准（Checklist）

- [ ] `PostingEngineApplicationService` 过账引擎应用服务（编排层，无业务逻辑）
- [ ] 批量过账委托 PostingEngineDomainService.executeBatchPosting()
- [ ] 进度查询委托 PostingMonitorDomainService
- [ ] 统计报表委托 PostingMonitorDomainService.getPostingStats()
- [ ] 异常重试委托 PostingMonitorDomainService.retryAbnormalVoucher()
- [ ] 异常跳过委托 PostingMonitorDomainService.skipAbnormalVoucher()
- [ ] 异常列表委托 PostingMonitorDomainService.getAbnormalVouchers()
- [ ] `PostingEngineController` 实现 7 个接口
- [ ] `BatchPostingRequest` / `PostingRetryRequest` / `PostingSkipRequest` DTO
- [ ] `BatchPostingResponse` / `PostingMonitorResponse` / `PostingStatsResponse` / `PostingRetryResponse` / `AbnormalVoucherResponse` DTO
- [ ] 所有 DTO 使用 `jakarta.validation` 注解校验
- [ ] `PostingEngineAssembler` 完成 PO/Result ↔ DTO 转换
- [ ] 参数校验：startDate 必填、voucherNo 必填、重试/跳过原因必填
- [ ] maxBatchSize 范围校验（1-200）
- [ ] 批量过账部分失败不抛异常，通过响应体返回失败信息
- [ ] 凭证不存在 / 状态非法返回明确错误
- [ ] 重试次数超限返回明确错误（POSTING_RETRY_EXHAUSTED）
- [ ] Swagger/OpenAPI 注解完整（@Tag / @Operation）
- [ ] 单测：批量过账接口、进度查询接口、统计报表接口、重试/跳过接口、异常列表接口
