# step-09-java-c · Journaling Application Service + Controller + 入口幂等锁

> **Step 9 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 9 Java-A（流水持久化 + 事务编号生成）+ Java-B（预开户检查领域服务）

---

## 1. 任务目标

实现记账流水入库的应用层编排和 REST API 接口，包含入口幂等锁控制、参数校验、用例编排。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、幂等设计 |
| 3 | `docs/sql/5-journal.sql` | `t_business_record` / `t_business_detail` DDL |
| 4 | `docs/design/flowchart/accounting_flow.mmd` | 入账流程总览 |
| 5 | `accounting-core/.../domain/service/JournalingDomainService.java` | 流水入库领域服务（Java-A） |
| 6 | `accounting-core/.../domain/service/AccountPreCheckDomainService.java` | 预开户检查领域服务（Java-B） |
| 7 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 8 | `accounting-api/.../constant/ResultCode.java` | 统一结果码 |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   ├── JournalSubmitRequest.java            # 记账请求入参 DTO
    │   └── JournalDetailRequest.java            # 流水明细入参 DTO
    └── response/
        └── JournalSubmitResponse.java           # 记账结果响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   ├── service/
    │   │   └── JournalingApplicationService.java  # 流水入库应用服务
    │   └── assembler/
    │       └── JournalingAssembler.java         # PO ↔ Request/Response 转换
    └── controller/
        └── JournalingController.java            # 流水入库 Controller
```

---

## 4. Request / Response DTO

### 4.1 JournalSubmitRequest

```java
package com.kltb.accounting.api.request;

import lombok.Data;
import jakarta.validation.Valid;           // M6 修复：Spring Boot 3.x 使用 jakarta
import jakarta.validation.constraints.*;   // M6 修复
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class JournalSubmitRequest {

    @NotBlank(message = "traceNo不能为空")
    @Size(max = 64, message = "traceNo长度不能超过64")
    private String traceNo;

    @Min(value = 0, message = "traceSeq必须>=0")
    private Integer traceSeq = 0;

    @NotBlank(message = "businessCode不能为空")
    @Size(max = 32, message = "businessCode长度不能超过32")
    private String businessCode;

    @NotBlank(message = "tradingCode不能为空")
    @Size(max = 32, message = "tradingCode长度不能超过32")
    private String tradingCode;

    @NotBlank(message = "payChannel不能为空")
    @Size(max = 32, message = "payChannel长度不能超过32")
    private String payChannel;

    @NotNull(message = "tradeType不能为空")
    private Integer tradeType;

    @NotNull(message = "amount不能为空")
    @DecimalMin(value = "0.000001", message = "amount必须大于0")
    private BigDecimal amount;

    @NotNull(message = "tradeTime不能为空")
    private LocalDateTime tradeTime;

    @Size(max = 64, message = "summary长度不能超过64")
    private String summary;

    @NotEmpty(message = "details不能为空")
    @Valid
    private List<JournalDetailRequest> details;
}
```

### 4.2 JournalDetailRequest

```java
package com.kltb.accounting.api.request;

import lombok.Data;
import jakarta.validation.constraints.*;   // M6 修复
import java.math.BigDecimal;

@Data
public class JournalDetailRequest {

    @NotBlank(message = "customerId不能为空")
    @Size(max = 64, message = "customerId长度不能超过64")
    private String customerId;

    @NotNull(message = "customerType不能为空")
    private Integer customerType;

    @NotBlank(message = "fundsType不能为空")
    @Size(max = 32, message = "fundsType长度不能超过32")
    private String fundsType;

    /**
     * 款项明细编码（N2 修复）
     * DDL 唯一索引 uk_trace_no 包含此字段：(trace_no, trace_seq, customer_id, item_code)
     * 用于区分同一客户在同一笔流水中的不同款项明细
     */
    @NotBlank(message = "itemCode不能为空")
    @Size(max = 32, message = "itemCode长度不能超过32")
    private String itemCode;

    @NotNull(message = "amount不能为空")
    @DecimalMin(value = "0.000001", message = "amount必须大于0")
    private BigDecimal amount;
}
```

### 4.3 JournalSubmitResponse

```java
package com.kltb.accounting.api.response;

import lombok.Data;
import java.time.LocalDate;

@Data
public class JournalSubmitResponse {

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 事务编号（Step 9 生成） */
    private String txnNo;

    /** 是否需要凭证生成（true 表示需要 Step 10 继续处理） */
    private Boolean needVouchering;
}
```

---

## 5. JournalingAssembler 实现要点

```java
package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import org.springframework.stereotype.Component;

/**
 * 流水入库 DTO 转换器
 */
@Component
public class JournalingAssembler {

    /**
     * 领域结果 → API 响应 DTO
     */
    public JournalSubmitResponse toResponse(JournalSubmitResult result) {
        JournalSubmitResponse response = new JournalSubmitResponse();
        response.setTraceNo(result.getTraceNo());
        response.setAccountingDate(result.getAccountingDate());
        response.setTxnNo(result.getTxnNo());
        response.setNeedVouchering(true);
        return response;
    }

    /**
     * 幂等已存在结果 → API 响应 DTO（M5 修复）
     */
    public JournalSubmitResponse toIdempotentResponse(
        com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO record,
        TransactionPO transaction) {
        JournalSubmitResponse response = new JournalSubmitResponse();
        response.setTraceNo(record.getTraceNo());
        response.setAccountingDate(record.getAccountingDate());
        if (transaction != null) {
            response.setTxnNo(transaction.getTxnNo());
        }
        response.setNeedVouchering(false); // 已处理过
        return response;
    }
}
```

---

## 6. JournalingApplicationService 实现要点

```java
package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.JournalDetailRequest;
import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.application.assembler.JournalingAssembler;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.service.AccountPreCheckDomainService;
import com.kltb.accounting.core.domain.service.JournalSubmitResult;
import com.kltb.accounting.core.domain.service.JournalingDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JournalingApplicationService {

    private final JournalingDomainService journalingDomainService;
    private final AccountPreCheckDomainService accountPreCheckDomainService;
    private final BusinessRecordRepository businessRecordRepository;
    private final TransactionRepository transactionRepository;
    private final DistributedLockTemplate distributedLockTemplate;
    private final JournalingAssembler assembler;

    /**
     * 提交记账流水（含入口幂等锁控制）
     *
     * 流程：
     *   1. 参数校验（由 @Valid 完成）
     *   2. 明细金额合计校验
     *   3. 获取幂等锁
     *   4. 幂等检查：已存在 → 返回已有结果（M5 修复）
     *   5. 流水持久化
     *   6. 预开户检查
     *   7. 更新事务账户数
     *   8. 返回结果
     *   9. 开户失败 → 更新流水 FAILED（N4 修复）
     */
    public JournalSubmitResponse submitJournal(JournalSubmitRequest request) {
        // 1. 校验明细金额合计 = 总金额
        BigDecimal detailTotal = request.getDetails().stream()
            .map(JournalDetailRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (request.getAmount().compareTo(detailTotal) != 0) {
            throw new ServiceException(ResultCode.PARAM_ERROR,
                "流水明细金额合计(" + detailTotal + ")不等于总金额(" + request.getAmount() + ")");
        }

        // 2. 幂等锁
        String lockKey = "idempotent:trace:" + request.getTraceNo() + "-" + request.getTraceSeq();

        try {
            return distributedLockTemplate.execute(
                lockKey,
                0,   // wait 0s（立即失败）
                30,  // lease 30s
                () -> doSubmit(request)
            );
        } catch (AccountException e) {
            // N4 修复：预开户失败 → 更新流水 status=FAILED
            businessRecordRepository.updateStatusByTraceNo(
                request.getTraceNo(), BusinessRecordStatusEnum.FAILED);
            throw e;
        }
    }

    /**
     * 锁内执行
     */
    private JournalSubmitResponse doSubmit(JournalSubmitRequest request) {
        // 幂等检查
        BusinessRecordPO existing = journalingDomainService.checkIdempotent(
            request.getTraceNo(), request.getTraceSeq());
        if (existing != null) {
            // M5 修复：查询已有事务并返回
            TransactionPO txn = transactionRepository.selectByTraceNo(request.getTraceNo());
            return assembler.toIdempotentResponse(existing, txn);
        }

        // 确定会计日期
        var accountingDate = journalingDomainService.determineAccountingDate(request.getTradeTime());

        // 流水持久化（record + detail + transaction）
        JournalSubmitResult result = journalingDomainService.persistJournal(
            request.getTraceNo(), request.getTraceSeq(),
            request.getBusinessCode(), request.getTradingCode(), request.getPayChannel(),
            request.getTradeType(), request.getAmount(), request.getTradeTime(),
            request.getSummary(), request.getDetails(), accountingDate);

        // 预开户检查 + 自动开户
        Map<String, CustomerTypeEnum> customerMap = buildCustomerMap(request.getDetails());
        accountPreCheckDomainService.checkAndOpenAccounts(
            request.getBusinessCode(), request.getTradingCode(), request.getPayChannel(),
            customerMap);

        return assembler.toResponse(result);
    }

    /**
     * 构建 customerId → CustomerTypeEnum 映射（M3 修复：构建时绑定）
     */
    private Map<String, CustomerTypeEnum> buildCustomerMap(List<JournalDetailRequest> details) {
        Map<String, CustomerTypeEnum> map = new HashMap<>();
        for (JournalDetailRequest detail : details) {
            map.putIfAbsent(detail.getCustomerId(),
                CustomerTypeEnum.fromCode(detail.getCustomerType()));
        }
        return map;
    }
}
```

> **注意**：`distributedLockTemplate.execute()` 的 wait/lease 参数根据实际实现调整。如果模板签名不同，请按照现有 `DistributedLockTemplate` 的实际方法签名调用。

---

## 7. JournalingController 实现要点

```java
package com.kltb.accounting.core.controller;

import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.response.JournalSubmitResponse;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/accounting/journal")
@RequiredArgsConstructor
@Validated
public class JournalingController {

    private final JournalingApplicationService journalingApplicationService;

    /**
     * POST /accounting/journal/submit — 提交记账流水
     */
    @PostMapping("/submit")
    public ApiResponse<JournalSubmitResponse> submit(
            @Valid @RequestBody JournalSubmitRequest request) {
        JournalSubmitResponse response = journalingApplicationService.submitJournal(request);
        return ApiResponse.success(response);
    }

    /**
     * GET /accounting/journal/{traceNo} — 查询流水状态
     * 当前阶段返回空数据，Step 10 后补充实现
     */
    @GetMapping("/{traceNo}")
    public ApiResponse<?> getJournal(@PathVariable String traceNo) {
        return ApiResponse.success(null);
    }

    /**
     * GET /accounting/journal/trace/{traceNo}/transaction — 查询关联事务
     * 当前阶段返回空数据，Step 10 后补充实现
     */
    @GetMapping("/trace/{traceNo}/transaction")
    public ApiResponse<?> getTransaction(@PathVariable String traceNo) {
        return ApiResponse.success(null);
    }
}
```

---

## 8. 编码要点

- Application Service 负责幂等锁控制、用例编排、FAILED 状态更新（N4 修复），不含任何持久化逻辑
- 幂等锁：`accounting:{tenantId}:lock:idempotent:trace:{traceNo}-{traceSeq}`
- 锁等待时间：0s（立即失败，抛出 IDEMPOTENT_CONFLICT）
- 锁过期时间：30s
- 金额校验：明细金额合计必须等于流水总金额
- Controller 使用 `@Valid` + `@Validated` 实现参数校验
- DTO 使用 `jakarta.validation` 包（M6 修复，Spring Boot 3.x）
- 幂等重复请求时查询已有事务并返回 txnNo（M5 修复）
- catch AccountException 时更新流水 status=FAILED（N4 修复）
- `itemCode` 字段必填（N2 修复）

---

## 9. 完成标准

- [ ] `JournalSubmitRequest` / `JournalDetailRequest` / `JournalSubmitResponse` DTO 正确
- [ ] DTO 使用 `jakarta.validation` 包（M6 修复）
- [ ] `JournalDetailRequest` 含 `itemCode` 字段（N2 修复）
- [ ] 参数校验规则正确（必填、长度、金额 > 0、itemCode 必填）
- [ ] `JournalingApplicationService.submitJournal` 含幂等锁控制
- [ ] 幂等锁 Key 正确（含 tenantId + traceNo + traceSeq）
- [ ] 幂等重复请求时查询已有事务并返回 txnNo（M5 修复）
- [ ] 明细金额合计校验
- [ ] 预开户检查调用 AccountPreCheckDomainService（独立类）
- [ ] catch AccountException 时更新流水 status=FAILED（N4 修复）
- [ ] buildCustomerMap 构建时绑定 customerId + customerType（M3 修复）
- [ ] `JournalingController` 实现 3 个接口
- [ ] `JournalingAssembler` 完成 Result ↔ DTO 转换
- [ ] 单测全通，含正常流水、幂等重复、金额不匹配场景

---

## 10. 下一步

Step 9 三个子任务全部完成后，进入 **Step 10 · Vouchering（凭证生成引擎）**，详见 `docs/prompt/step-10-vouchering.md`。
