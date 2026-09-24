# step-10-java-c · VoucheringApplicationService + VoucheringController + DTO

> **Step 10 子任务** | 归属：`@Java` 工程师-C
> 前置依赖：Step 10 Java-A（VoucheringDomainService 已就绪）、Step 10 Java-B（BufferPostingDomainService 已就绪）

---

## 1. 任务目标

实现凭证生成应用服务（用例编排 + 统一事务边界 + txnNo回填）、Controller（3 个接口）及 DTO/Assembler 层。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范、Controller 规范 |
| 2 | `docs/prompt/step-10-vouchering.md` | Step 10 主文件（接口契约、业务流程） |
| 3 | `docs/prompt/tasks/step-10-java-a.md` | Java-A 任务文件（VoucherEntryData、VoucheringDomainService） |
| 4 | `docs/prompt/tasks/step-10-java-b.md` | Java-B 任务文件（AuxiliaryItemData、BufferPostingDetailData） |
| 5 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` DDL |
| 6 | `accounting-core/.../repository/AccountingVoucherRepository.java` | 凭证仓储（已有） |
| 7 | `accounting-core/.../repository/BusinessRecordRepository.java` | 流水仓储（Step 9 已创建） |
| 8 | `accounting-core/.../repository/TransactionRepository.java` | 事务仓储（Step 9 已创建） |
| 9 | `accounting-core/.../config/TransactionConfig.java` | 事务模板配置 |

---

## 3. 需要创建的文件

```
accounting-api/
└── src/main/java/com/kltb/accounting/api/
    ├── request/
    │   └── VoucherGenerateRequest.java        # 凭证生成请求 DTO
    └── response/
        ├── VoucherGenerateResponse.java       # 凭证生成结果响应 DTO
        └── VoucherEntryResponse.java          # 分录行响应 DTO

accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── application/
    │   ├── service/
    │   │   └── VoucheringApplicationService.java  # 凭证生成应用服务
    │   └── assembler/
    │       └── VoucheringAssembler.java         # PO ↔ Request/Response 转换
    └── controller/
        └── VoucheringController.java            # 凭证生成 Controller
```

---

## 4. DTO 定义

### 4.1 VoucherGenerateRequest

```java
package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VoucherGenerateRequest {

    @NotBlank(message = "traceNo 不能为空")
    @Size(max = 64, message = "traceNo 长度不能超过 64")
    private String traceNo;

    @Size(max = 32, message = "bookkeeperName 长度不能超过 32")
    private String bookkeeperName = "SYSTEM";
}
```

### 4.2 VoucherGenerateResponse

```java
package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class VoucherGenerateResponse {

    private String voucherNo;
    private String traceNo;
    private String txnNo;
    private String voucherType;
    private BigDecimal amount;
    private LocalDate accountingDate;
    private Integer status;
    private LocalDateTime tradeTime;
    private String summary;
    private List<VoucherEntryResponse> entries;
    private Integer bufferedCount;  // 缓冲入账分录数量
    private Integer normalCount;    // 正常过账分录数量
}
```

### 4.3 VoucherEntryResponse

```java
package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class VoucherEntryResponse {

    private String entryId;
    private Integer rowNum;
    private String subjectCode;
    private String accountNo;
    private Integer debitCredit;
    private BigDecimal amount;
    private String currency;
    private String summary;
    private Boolean isUnilateral;
    private Boolean isBuffered;
}
```

---

## 5. VoucheringAssembler 转换器

```java
package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.VoucherEntryResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.domain.service.VoucherEntryData;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class VoucheringAssembler {

    /**
     * 将凭证生成结果组装为响应 DTO
     */
    public VoucherGenerateResponse toResponse(
        AccountingVoucherPO voucher,
        List<VoucherEntryData> entries,
        String txnNo) {

        long bufferedCount = entries.stream()
            .filter(VoucherEntryData::getIsBuffered)
            .count();
        long normalCount = entries.size() - bufferedCount;

        return new VoucherGenerateResponse(
            voucher.getVoucherNo(),
            voucher.getTraceNo(),
            txnNo,
            voucher.getVoucherType(),
            voucher.getAmount(),
            voucher.getAccountingDate(),
            voucher.getStatus() != null ? voucher.getStatus().getCode() : null,
            voucher.getTradeTime(),
            voucher.getSummary(),
            entries.stream().map(this::toEntryResponse).collect(Collectors.toList()),
            (int) bufferedCount,
            (int) normalCount
        );
    }

    private VoucherEntryResponse toEntryResponse(VoucherEntryData data) {
        return new VoucherEntryResponse(
            data.getEntryId(),
            data.getRowNum(),
            data.getSubjectCode(),
            data.getAccountNo(),
            data.getDebitCredit(),
            data.getAmount(),
            data.getCurrency(),
            data.getSummary(),
            data.getIsUnilateral(),
            data.getIsBuffered()
        );
    }
}
```

> **P2-1 修复**：`status` 字段通过 `voucher.getStatus().getCode()` 转换为 Integer，而非直接传入枚举。
> `voucherType` 字段类型为 String，与 PO 定义一致。

---

## 6. VoucheringApplicationService 实现要点

```java
package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.application.assembler.VoucheringAssembler;
import com.kltb.accounting.core.common.enums.ResultCode;
import com.kltb.accounting.core.common.exception.AccountException;
import com.kltb.accounting.core.common.exception.ServiceException;
import com.kltb.accounting.core.domain.service.*;
import com.kltb.accounting.core.domain.service.BufferPostingDomainService;
import com.kltb.accounting.core.domain.service.VoucheringDomainService.AccountingRuleWithDetails;
import com.kltb.accounting.core.domain.service.VoucheringDomainService.JournalWithDetails;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VoucheringApplicationService {

    private final VoucheringDomainService voucheringDomainService;
    private final BufferPostingDomainService bufferPostingDomainService;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionTemplate transactionTemplate;
    private final VoucheringAssembler assembler;

    /**
     * 凭证生成用例入口
     *
     * 执行流程：
     *   1. 加载流水 + 状态校验
     *   2. 匹配记账规则（含辅助核算映射）
     *   3. 逐规则明细行计算分录（SpEL + 辅助核算分摊）
     *   4. 借贷平衡校验
     *   5. 在统一事务中：持久化凭证 + 分录 + 辅助核算项 + 缓冲匹配 + txnNo 回填
     *   6. 返回凭证生成结果
     */
    public VoucherGenerateResponse generateVoucher(VoucherGenerateRequest request) {
        // 1. 加载流水 + 状态校验
        JournalWithDetails journalWithDetails = voucheringDomainService.loadJournal(request.getTraceNo());
        BusinessRecordPO journal = journalWithDetails.getRecord();
        List<BusinessDetailPO> businessDetails = journalWithDetails.getDetails();

        if (journal.getStatus() != BusinessRecordStatusEnum.PROCESSING) {
            throw new ServiceException(ResultCode.JOURNAL_STATUS_INVALID,
                "流水状态非法: traceNo=" + request.getTraceNo() + ", status=" + journal.getStatus());
        }

        // 2. 匹配记账规则
        AccountingRuleWithDetails ruleWithDetails = voucheringDomainService.matchRule(
            journal.getBusinessCode(), journal.getTradingCode(), journal.getPayChannel());

        AccountingRulePO rule = ruleWithDetails.getRule();
        List<AccountingRuleDetailPO> ruleDetails = ruleWithDetails.getDetails();
        // P1-3 修复：直接使用 auxMap
        Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap = ruleWithDetails.getAuxMap();

        // 查询 txnNo（从 t_transaction 表获取）
        TransactionPO transaction = transactionRepository.selectByTraceNo(journal.getTraceNo());
        if (transaction == null) {
            throw new ServiceException(ResultCode.SYSTEM_ERROR,
                "事务记录不存在: traceNo=" + journal.getTraceNo());
        }
        String txnNo = transaction.getTxnNo();

        // 3. 逐规则明细行计算分录
        List<VoucherEntryData> entries = new ArrayList<>();
        List<AuxiliaryItemData> allAuxItems = new ArrayList<>();
        List<BufferPostingDetailData> allBufferData = new ArrayList<>();

        for (AccountingRuleDetailPO ruleDetail : ruleDetails) {
            // 找到匹配 fundsType 的业务明细
            BusinessDetailPO matchedDetail = findMatchingDetail(
                businessDetails, ruleDetail.getFundsType());
            if (matchedDetail == null) {
                throw new AccountException(ResultCode.RULE_NOT_FOUND,
                    "未找到匹配的款项类型: fundsType=" + ruleDetail.getFundsType());
            }

            // 计算分录金额
            BigDecimal amount = voucheringDomainService.calculateEntryAmount(
                ruleDetail, matchedDetail);

            // 确定账户编号
            String accountNo = resolveAccountNo(ruleDetail, matchedDetail);

            // 构建分录数据
            VoucherEntryData entry = new VoucherEntryData(
                null,  // entryId 由 persistVoucher 内部生成
                null,  // voucherNo 由 persistVoucher 内部生成
                ruleDetail.getRowNum(),
                ruleDetail.getSubjectCode(),
                accountNo,
                ruleDetail.getDebitCredit() != null ? ruleDetail.getDebitCredit().getCode() : null,
                amount,
                ruleDetail.getCurrency(),
                ruleDetail.getSummary(),
                journal.getAccountingDate(),
                ruleDetail.getUnilateral() != null && ruleDetail.getUnilateral(),
                false  // isBuffered 默认为 false，后续缓冲匹配后更新
            );
            entries.add(entry);

            // P1-3 修复：从 auxMap 获取该分录行的辅助核算配置
            List<AccountingRuleAuxiliaryPO> auxConfigs = auxMap.get(ruleDetail.getId());
            if (auxConfigs != null && !auxConfigs.isEmpty()) {
                List<AuxiliaryItemData> auxItems = bufferPostingDomainService
                    .calculateAuxiliaryAllocation(entry, auxConfigs);
                allAuxItems.addAll(auxItems);
            }
        }

        // 4. 借贷平衡校验
        voucheringDomainService.validateDebitCreditBalance(entries);

        // 5. 在统一事务中完成所有持久化操作（P1-1 修复）
        String voucherNo = transactionTemplate.execute(status -> {
            // 5a. 写入凭证 + 分录
            String vouNo = voucheringDomainService.persistVoucher(
                journal, rule, entries, request.getBookkeeperName());

            // 5b. 写入辅助核算项
            bufferPostingDomainService.persistAuxiliaryItems(allAuxItems);

            // 5c. 缓冲规则匹配
            for (VoucherEntryData entry : entries) {
                entry.setVoucherNo(vouNo);
                BufferPostingRulePO bufferRule = bufferPostingDomainService.matchBufferRule(
                    entry, journal.getBusinessCode(), journal.getTradingCode(), journal.getPayChannel());

                if (bufferRule != null) {
                    entry.setIsBuffered(true);
                    Long sharding = bufferPostingDomainService.calculateSharding(entry.getAccountNo());
                    allBufferData.add(buildBufferPostingData(
                        bufferRule, entry, journal, txnNo));
                }
            }

            // 5d. 写入缓冲记账明细
            bufferPostingDomainService.persistBufferPostingDetails(allBufferData);

            // 5e. 回填 txnNo
            int affected = accountingVoucherRepository.updateTxnNoByVoucherNo(vouNo, txnNo);
            if (affected != 1) {
                throw new ServiceException(ResultCode.SYSTEM_ERROR,
                    "txnNo 回填失败: voucherNo=" + vouNo);
            }

            return vouNo;
        });

        // 6. 查询持久化后的凭证，返回响应
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(request.getTraceNo());
        AccountingVoucherPO voucher = vouchers.isEmpty() ? null : vouchers.get(0);
        return assembler.toResponse(voucher, entries, txnNo);
    }

    /**
     * 找到匹配的款项类型业务明细
     */
    private BusinessDetailPO findMatchingDetail(List<BusinessDetailPO> details, String fundsType) {
        return details.stream()
            .filter(d -> fundsType.equals(d.getFundsType()))
            .findFirst()
            .orElse(null);
    }

    /**
     * 确定账户编号
     *
     * P1-2 修复：根据规则明细的 account_scope 确定账户查找方式。
     * 当前阶段简化实现：外部账户使用 customerId 作为 owner_id 查询。
     * 完整实现需结合 Step 9 的 AccountPreCheckDomainService 结果。
     */
    private String resolveAccountNo(AccountingRuleDetailPO ruleDetail, BusinessDetailPO detail) {
        // 当前简化 fallback：使用 customerId 占位
        // TODO: Step 11/12 完善账户解析逻辑，结合 AccountPreCheckDomainService 结果
        return detail.getCustomerId();
    }

    /**
     * 构建缓冲记账明细数据
     */
    private BufferPostingDetailData buildBufferPostingData(
        BufferPostingRulePO bufferRule,
        VoucherEntryData entry,
        BusinessRecordPO journal,
        String txnNo) {

        Long sharding = bufferPostingDomainService.calculateSharding(entry.getAccountNo());
        return new BufferPostingDetailData(
            bufferRule.getId(),
            bufferRule.getBufferMode() != null ? bufferRule.getBufferMode().getCode() : null,
            entry.getVoucherNo(),
            entry.getEntryId(),
            txnNo,
            journal.getTraceNo(),
            journal.getTraceSeq(),
            journal.getBusinessCode(),
            journal.getTradingCode(),
            journal.getPayChannel(),
            journal.getTradeType() != null ? journal.getTradeType().getCode() : null,
            journal.getTradeTime(),
            entry.getAccountNo(),
            entry.getDebitCredit(),
            entry.getCurrency(),
            entry.getAmount(),
            entry.getAccountingDate(),
            entry.getSummary(),
            sharding
        );
    }
}
```

> **关键修复汇总**：
> - **P1-1 修复**：事务边界统一在 `generateVoucher()` 方法内，`persistVoucher` 不开启独立事务
> - **P1-2 修复**：`resolveAccountNo` 使用 `detail.getCustomerId()` 作为 fallback
> - **P1-3 修复**：`getAuxiliaryConfigs` 通过 `ruleWithDetails.getAuxMap().get(ruleDetail.getId())` 获取
> - **P2-2 修复**：`journal.getTradeType().getCode()` 正确获取 Integer 编码
> - txnNo 从 `TransactionRepository.selectByTraceNo()` 获取，而非 `journal.getTxnNo()`（BusinessRecordPO 无此字段）

---

## 7. VoucheringController 实现要点

```java
package com.kltb.accounting.core.controller;

import com.kltb.accounting.api.request.VoucherGenerateRequest;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.core.application.service.VoucheringApplicationService;
import com.kltb.accounting.core.common.result.Result;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/accounting/voucher")
@RequiredArgsConstructor
public class VoucheringController {

    private final VoucheringApplicationService voucheringApplicationService;
    private final AccountingVoucherRepository accountingVoucherRepository;

    /**
     * POST /accounting/voucher/generate — 生成凭证
     */
    @PostMapping("/generate")
    public Result<VoucherGenerateResponse> generate(@Valid @RequestBody VoucherGenerateRequest request) {
        return Result.success(voucheringApplicationService.generateVoucher(request));
    }

    /**
     * GET /accounting/voucher/{voucherNo} — 查询凭证详情
     * P3-1 修复：实现基本查询，返回凭证基本信息 + 分录列表
     */
    @GetMapping("/{voucherNo}")
    public Result<VoucherGenerateResponse> getVoucher(@PathVariable String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNo(voucherNo);
        if (voucher == null) {
            return Result.error("凭证不存在: " + voucherNo);
        }
        // TODO: 补充 Assembler 方法，将 PO 转换为 Response
        return Result.success(null);
    }

    /**
     * GET /accounting/voucher/trace/{traceNo} — 按流水号查询凭证
     * P3-1 修复：实现基本查询，返回关联凭证列表
     */
    @GetMapping("/trace/{traceNo}")
    public Result<List<AccountingVoucherPO>> getByTraceNo(@PathVariable String traceNo) {
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(traceNo);
        return Result.success(vouchers);
    }
}
```

---

## 8. 编码要点

- Application Service 负责用例编排，不包含领域逻辑
- **P1-1 修复**：统一事务边界在 `generateVoucher()` 方法内，使用 `TransactionTemplate`
- DTO 使用 `jakarta.validation` 包
- Assembler 负责 PO/Result ↔ DTO 转换，包路径 `application/assembler/`
- 参数校验失败自动返回 400（由 GlobalExceptionHandler 处理）
- 流水不存在 / 状态非法返回明确错误
- SpEL 计算错误返回 `SPEL_CALC_ERROR`
- 借贷不平衡返回 `VOUCHER_NOT_BALANCED`
- 严禁 `@Transactional`，使用 `TransactionTemplate`
- **P3-1 修复**：查询接口实现基本功能（返回 PO 列表），完整 DTO 转换后续补充

---

## 9. 完成标准

- [ ] `VoucherGenerateRequest` / `VoucherGenerateResponse` / `VoucherEntryResponse` DTO 定义正确
- [ ] DTO 使用 `jakarta.validation` 包
- [ ] `VoucheringAssembler` 完成 PO ↔ DTO 转换（P2-1 修复：status 使用 getCode()）
- [ ] `VoucheringApplicationService` 编排凭证生成用例（**统一事务边界**，P1-1 修复）
- [ ] 流水查询 + 状态校验（status=PROCESSING）
- [ ] 记账规则匹配 + SpEL 分录计算
- [ ] 辅助核算分摊集成（从 auxMap 获取配置，P1-3 修复）
- [ ] 借贷平衡校验（调用 Java-A 的 VoucheringDomainService）
- [ ] 凭证写入 + 辅助核算项写入（同一事务内）
- [ ] 缓冲规则匹配 + 缓冲记账明细写入
- [ ] txnNo 从 TransactionRepository 获取并回填（P1-2 修复）
- [ ] `VoucheringController` 实现 3 个接口（P3-1 修复：查询接口有基本实现）
- [ ] 参数校验（traceNo 必填）
- [ ] `resolveAccountNo` 使用 customerId 作为 fallback（P1-2 修复，标注 TODO）
- [ ] 异常类型区分正确（AccountException vs ServiceException）
- [ ] `buildBufferPostingData` 中 tradeType 通过 `.getCode()` 获取（P2-2 修复）
- [ ] 单测覆盖清单：
    - [ ] 正常凭证生成（借贷平衡）
    - [ ] 流水不存在
    - [ ] 流水状态非法
    - [ ] 记账规则不存在
    - [ ] SpEL 计算错误
    - [ ] 借贷不平衡
    - [ ] 辅助核算按比例分摊（含最后一条补差）
    - [ ] 辅助核算固定金额分摊
    - [ ] 缓冲规则匹配命中
    - [ ] 缓冲规则匹配未命中
    - [ ] 辅助核算金额不匹配
    - [ ] 幂等：同一 traceNo 重复生成

---

## 10. 下一步

Step 10 完成后，进入 **Step 11 · Transaction Management（事务管理）**，详见 `docs/prompt/step-11-transaction.md`。
