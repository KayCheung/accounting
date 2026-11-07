# step-09-java-a · P0 补充 + 幂等校验 + 流水持久化 + 会计日期确定 + 事务编号生成

> **Step 9 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 8（`AccountOpeningDomainService` 已存在）、Step 4（`DistributedLockTemplate` 已存在）

---

## 1. 任务目标

完成 Step 9 四项前置补充任务（P0-1/P0-2/P0-3/P0-4），并实现流水持久化、会计日期确定和事务编号生成的领域服务。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、幂等设计 |
| 3 | `docs/sql/5-journal.sql` | `t_business_record` / `t_business_detail` / `t_transaction` DDL |
| 4 | `docs/design/domain-model.md` | 流水域模型 |
| 5 | `accounting-core/.../entity/BusinessRecordPO.java` | 业务流水 PO |
| 6 | `accounting-core/.../entity/BusinessDetailPO.java` | 流水明细 PO |
| 7 | `accounting-core/.../entity/TransactionPO.java` | 事务 PO |
| 8 | `accounting-core/.../redis/DistributedLockTemplate.java` | 分布式锁模板 |
| 9 | `accounting-core/.../config/TransactionConfig.java` | 事务模板配置（Step 8 已创建） |
| 10 | `accounting-core/.../mapper/BusinessRecordMapper.java` | **已有**空壳 Mapper |
| 11 | `accounting-core/.../mapper/BusinessDetailMapper.java` | **已有**空壳 Mapper |
| 12 | `accounting-core/.../mapper/TransactionMapper.java` | **已有**空壳 Mapper |

---

## 3. 前置补充任务（P0-1/P0-2/P0-3/P0-4）

### 3.1 P0-1：BusinessRecordMapper 补充 + BusinessRecordRepository/BusinessDetailRepository 新建

**修改文件**：`BusinessRecordMapper.java`（已有，追加方法）

```java
// BusinessRecordMapper.java — 追加
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface BusinessRecordMapper extends BaseMapper<BusinessRecordPO> {

    /**
     * 按 traceNo + traceSeq 查询流水（幂等检查用）
     */
    @Select("SELECT * FROM t_business_record WHERE trace_no = #{traceNo} AND trace_seq = #{traceSeq} AND is_delete = 0 LIMIT 1")
    BusinessRecordPO selectByTraceNo(@Param("traceNo") String traceNo,
                                      @Param("traceSeq") Integer traceSeq);

    /**
     * 按 traceNo 更新流水状态（开户失败时使用）
     */
    int updateStatusByTraceNo(@Param("traceNo") String traceNo,
                               @Param("status") Integer status);
}
```

**新建文件**：`BusinessRecordRepository.java`

```java
@Repository
@RequiredArgsConstructor
public class BusinessRecordRepository {

    private final BusinessRecordMapper businessRecordMapper;

    /**
     * 按 traceNo + traceSeq 查询流水（幂等检查用）
     */
    public BusinessRecordPO selectByTraceNo(String traceNo, Integer traceSeq) {
        return businessRecordMapper.selectByTraceNo(traceNo, traceSeq);
    }

    /**
     * 保存流水记录
     */
    public void save(BusinessRecordPO record) {
        businessRecordMapper.insert(record);
    }

    /**
     * 按 traceNo 更新流水状态（开户失败时使用）
     */
    public void updateStatusByTraceNo(String traceNo, BusinessRecordStatusEnum status) {
        businessRecordMapper.updateStatusByTraceNo(traceNo, status.getCode());
    }
}
```

**新建文件**：`BusinessDetailRepository.java`（S3/M1 修复：独立仓储）

```java
@Repository
@RequiredArgsConstructor
public class BusinessDetailRepository {

    private final BusinessDetailMapper businessDetailMapper;

    /**
     * 保存流水明细
     */
    public void save(BusinessDetailPO detail) {
        businessDetailMapper.insert(detail);
    }

    /**
     * 按 traceNo 查询流水明细列表
     */
    public List<BusinessDetailPO> selectByTraceNo(String traceNo) {
        return businessDetailMapper.selectList(
            new LambdaQueryWrapper<BusinessDetailPO>()
                .eq(BusinessDetailPO::getTraceNo, traceNo)
                .eq(BusinessDetailPO::getIsDelete, 0));
    }
}
```

### 3.2 P0-2：TransactionMapper 补充 + TransactionRepository 新建

**修改文件**：`TransactionMapper.java`（已有，追加方法）

```java
// TransactionMapper.java — 追加
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TransactionMapper extends BaseMapper<TransactionPO> {

    /**
     * 按 traceNo 查询事务记录
     */
    @Select("SELECT * FROM t_transaction WHERE trace_no = #{traceNo} AND is_delete = 0 LIMIT 1")
    TransactionPO selectByTraceNo(@Param("traceNo") String traceNo);
}
```

**新建文件**：`TransactionRepository.java`

```java
@Repository
@RequiredArgsConstructor
public class TransactionRepository {

    private final TransactionMapper transactionMapper;

    /**
     * 按 traceNo 查询事务记录
     */
    public TransactionPO selectByTraceNo(String traceNo) {
        return transactionMapper.selectByTraceNo(traceNo);
    }

    /**
     * 保存事务记录
     */
    public void save(TransactionPO transaction) {
        transactionMapper.insert(transaction);
    }
}
```

### 3.3 P0-3：确认 TransactionConfig 已存在

Step 8 P0-4 已创建 `TransactionConfig`，确认 `TransactionTemplate` Bean 可用即可。

### 3.4 P0-4：修复 AccountingRuleMapper.xml selectByBusinessKey

**修改文件**：`accounting-core/src/main/resources/mapper/AccountingRuleMapper.xml`

当前 SQL 缺少 `status=2` 过滤（S4 修复）：

```xml
<!-- 修改前 -->
WHERE business_code = #{businessCode}
  AND trading_code = #{tradingCode}
  AND pay_channel = #{payChannel}
  AND is_delete = 0

<!-- 修改后 -->
WHERE business_code = #{businessCode}
  AND trading_code = #{tradingCode}
  AND pay_channel = #{payChannel}
  AND status = 2
  AND is_delete = 0
```

> 这确保 `selectByBusinessKey` 只匹配已启用的规则，后续 `AccountPreCheckDomainService` 无需再额外判断 status。

---

## 4. 需要创建的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       ├── JournalingDomainService.java     # 流水入库领域服务
    │       └── JournalSubmitResult.java         # 领域层结果对象（S2 修复）
    └── infrastructure/
        └── account/
            └── TransactionNoGenerator.java      # 事务编号生成器（M2 修复）
```

---

## 5. JournalSubmitResult 领域结果对象（S2 修复）

```java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

/**
 * 流水入库领域层结果对象
 * Application Service 通过 Assembler 将此转换为 JournalSubmitResponse DTO
 */
@Data
@AllArgsConstructor
public class JournalSubmitResult {

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 事务编号 */
    private String txnNo;
}
```

---

## 6. TransactionNoGenerator 事务编号生成器（M2 修复）

```java
package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 事务编号生成器
 * 格式：TXN + yyyyMMdd + seq6，如 TXN20260512000001
 * Redis key：txn:seq:{yyyyMMdd}
 * TTL：25 小时（每日自动重置）
 */
@Component
@RequiredArgsConstructor
public class TransactionNoGenerator {

    private final RedissonClient redissonClient;

    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "txn:seq:" + date;
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        if (atomicLong.isNotExists()) {
            atomicLong.set(0);
            atomicLong.expire(25, TimeUnit.HOURS);
        }
        long seq = atomicLong.incrementAndGet();
        return "TXN" + date + String.format("%06d", seq);
    }
}
```

> 与 Step 8 的 `AccountNoGenerator` 保持一致的设计风格。序号从 1 开始，格式化为 6 位数字。

---

## 7. JournalingDomainService 实现要点

### 7.1 类结构

```java
@Service
@RequiredArgsConstructor
public class JournalingDomainService {

    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessDetailRepository businessDetailRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionNoGenerator transactionNoGenerator;
    private final TransactionTemplate transactionTemplate;
}
```

> **注意**：此类不包含 `checkAndOpenAccounts` 方法（S1 修复），该方法在独立的 `AccountPreCheckDomainService` 中（Java-B 负责）。

### 7.2 checkIdempotent 方法

```java
/**
 * 幂等检查：按 traceNo + traceSeq 查询已存在的流水
 */
public BusinessRecordPO checkIdempotent(String traceNo, Integer traceSeq) {
    return businessRecordRepository.selectByTraceNo(traceNo, traceSeq);
}
```

### 7.3 determineAccountingDate 方法

```java
/**
 * 确定会计日期
 * 当前阶段：直接使用 tradeTime.toLocalDate()
 * 日切逻辑（Step 17）：根据配置决定是否使用 T+1
 */
public LocalDate determineAccountingDate(LocalDateTime tradeTime) {
    return tradeTime.toLocalDate();
}
```

### 7.4 persistJournal 方法

```java
/**
 * 在事务中写入流水 + 明细 + 创建事务记录
 *
 * @param traceNo 系统跟踪号
 * @param traceSeq 序列号
 * @param businessCode 业务线编码
 * @param tradingCode 交易编码
 * @param payChannel 支付渠道
 * @param tradeType 交易类别
 * @param amount 交易金额
 * @param tradeTime 交易时间
 * @param summary 摘要
 * @param details 流水明细列表
 * @param accountingDate 会计日期
 * @return 领域层结果对象
 */
public JournalSubmitResult persistJournal(
    String traceNo, Integer traceSeq, String businessCode,
    String tradingCode, String payChannel, Integer tradeType,
    BigDecimal amount, LocalDateTime tradeTime, String summary,
    List<JournalDetailRequest> details, LocalDate accountingDate) {

    final String[] txnNoRef = new String[1];
    transactionTemplate.execute(status -> {
        // 1. 生成事务编号
        String txnNo = transactionNoGenerator.generate();
        txnNoRef[0] = txnNo;

        // 2. 写入 t_business_record
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo).setTraceSeq(traceSeq);
        record.setBusinessCode(businessCode).setTradingCode(tradingCode);
        record.setPayChannel(payChannel);
        record.setTradeType(TradeTypeEnum.fromCode(tradeType));
        record.setAmount(amount).setTradeTime(tradeTime);
        record.setAccountingDate(accountingDate).setSummary(summary);
        record.setStatus(BusinessRecordStatusEnum.PROCESSING);
        businessRecordRepository.save(record);

        // 3. 写入 t_business_detail（逐条）
        for (JournalDetailRequest detail : details) {
            BusinessDetailPO detailPO = new BusinessDetailPO();
            detailPO.setTraceNo(traceNo).setTraceSeq(traceSeq);
            detailPO.setCustomerId(detail.getCustomerId());
            detailPO.setCustomerType(CustomerTypeEnum.fromCode(detail.getCustomerType()));
            detailPO.setFundsType(detail.getFundsType());
            detailPO.setItemCode(detail.getItemCode()); // N2 修复
            detailPO.setAmount(detail.getAmount());
            businessDetailRepository.save(detailPO);
        }

        // 4. 创建 t_transaction
        TransactionPO transaction = new TransactionPO();
        transaction.setTxnNo(txnNo).setTraceNo(traceNo);
        transaction.setAccountingDate(accountingDate);
        transaction.setAmount(amount).setCurrency("CNY");
        transaction.setStatus(TransactionStatusEnum.PROCESSING);
        transaction.setRelateAccountCount(0); // 预开户后更新
        transactionRepository.save(transaction);

        return null;
    });

    return new JournalSubmitResult(traceNo, accountingDate, txnNoRef[0]);
}
```

---

## 8. 编码要点

- 领域服务加 `@Service` 注解，构造器注入依赖
- 持久化操作在领域服务内部完成
- 所有异常使用 `AccountException` / `ServiceException` + `ResultCode` 枚举
- 金额计算使用 `BigDecimal`，禁止 `new BigDecimal(double)`，禁止 `equals()` 比较
- 流水明细金额合计校验在 Application Service 层完成（`details.stream().map(...).reduce(...)` 与 `request.getAmount()` 比较）
- 使用 `compareTo()` 比较 BigDecimal：`request.getAmount().compareTo(detailTotal) != 0`

---

## 9. 完成标准

- [ ] P0-1: `BusinessRecordMapper` 补充 `selectByTraceNo` + `updateStatusByTraceNo`
- [ ] P0-1: `BusinessRecordRepository` 新建正确
- [ ] P0-1: `BusinessDetailRepository` 新建正确（S3/M1 修复）
- [ ] P0-2: `TransactionMapper` 补充 `selectByTraceNo`
- [ ] P0-2: `TransactionRepository` 新建正确
- [ ] P0-3: 确认 `TransactionConfig` 存在
- [ ] P0-4: `AccountingRuleMapper.xml` selectByBusinessKey 补充 `AND status = 2`（S4 修复）
- [ ] `JournalSubmitResult` 领域结果对象定义正确（S2 修复）
- [ ] `TransactionNoGenerator` 事务编号生成正确（Redis 序号，每日重置，M2 修复）
- [ ] `JournalingDomainService.checkIdempotent` 正确
- [ ] `JournalingDomainService.determineAccountingDate` 正确
- [ ] `JournalingDomainService.persistJournal` 流水入库全流程正确
- [ ] `t_business_record` + `t_business_detail` + `t_transaction` 事务写入
- [ ] BusinessDetailPO 设置 itemCode 字段（N2 修复）
- [ ] 枚举转换使用 `fromCode()` 方法（TradeTypeEnum / CustomerTypeEnum / 各 StatusEnum）

---

## 10. 下一步

完成后通知 Java-B 可以开始，详见 `docs/prompt/tasks/step-09-java-b.md`。
