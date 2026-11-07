# step-10-java-b · 辅助核算分摊 + 缓冲规则匹配 + BufferPostingDomainService

> **Step 10 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 10 Java-A（VoucheringDomainService + VoucherEntryData 已就绪）

---

## 1. 任务目标

实现辅助核算分摊引擎（按比例/固定金额/不分摊）、缓冲规则匹配引擎，以及独立的 `BufferPostingDomainService` 领域服务。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、辅助核算规则 |
| 3 | `docs/sql/2-voucher.sql` | `t_accounting_voucher_auxiliary` DDL |
| 4 | `docs/sql/3-rule.sql` | `t_accounting_rule_auxiliary` / `t_buffer_posting_rule` / `t_buffer_posting_detail` DDL |
| 5 | `docs/design/domain-model.md` | 规则域、凭证域模型 |
| 6 | `docs/prompt/tasks/step-10-java-a.md` | Java-A 任务文件（VoucherEntryData 定义） |
| 7 | `accounting-core/.../entity/AccountingVoucherAuxiliaryPO.java` | 辅助核算 PO |
| 8 | `accounting-core/.../entity/AccountingRuleAuxiliaryPO.java` | 规则辅助核算 PO |
| 9 | `accounting-core/.../entity/BufferPostingRulePO.java` | 缓冲规则 PO |
| 10 | `accounting-core/.../entity/BufferPostingDetailPO.java` | 缓冲记账明细 PO |
| 11 | `accounting-core/.../mapper/AccountingVoucherAuxiliaryMapper.java` | **已有**空壳 Mapper（BaseMapper.insert 可用） |
| 12 | `accounting-core/.../mapper/BufferPostingRuleMapper.java` | **已有**空壳 Mapper |
| 13 | `accounting-core/.../mapper/BufferPostingDetailMapper.java` | **已有**空壳 Mapper（BaseMapper.insert 可用） |

---

## 3. 前置补充任务

### 3.1 BufferPostingRuleMapper 补充 selectMatchingRules

**修改文件**：`BufferPostingRuleMapper.java`（已有，追加方法）

```java
// BufferPostingRuleMapper.java — 追加
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BufferPostingRuleMapper extends BaseMapper<BufferPostingRulePO> {

    /**
     * 查询匹配的缓冲规则
     * 匹配条件：businessCode + tradingCode + payChannel + (subjectCode IS NULL/空或匹配) + (accountNo IS NULL/空或匹配) + debitCredit + 时间区间
     */
    List<BufferPostingRulePO> selectMatchingRules(
        @Param("businessCode") String businessCode,
        @Param("tradingCode") String tradingCode,
        @Param("payChannel") String payChannel,
        @Param("subjectCode") String subjectCode,
        @Param("accountNo") String accountNo,
        @Param("debitCredit") Integer debitCredit,
        @Param("accountingDate") LocalDate accountingDate);
}
```

**对应 XML**：

> **P1-6 修复**：使用 `IS NULL OR 空串` 双重判断，兼容 DDL 中 NOT NULL 默认值和可能的 NULL 值。

```xml
<!-- resources/mapper/BufferPostingRuleMapper.xml — 追加 -->
<select id="selectMatchingRules" resultType="com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO">
    SELECT * FROM t_buffer_posting_rule
    WHERE business_code = #{businessCode}
      AND trading_code = #{tradingCode}
      AND pay_channel = #{payChannel}
      AND debit_credit = #{debitCredit}
      AND effective_time &lt;= #{accountingDate}
      AND expiration_time &gt;= #{accountingDate}
      AND (subject_code IS NULL OR subject_code = '' OR subject_code = #{subjectCode})
      AND (account_no IS NULL OR account_no = '' OR account_no = #{accountNo})
      AND is_delete = 0
    ORDER BY id ASC
    LIMIT 1
</select>
```

---

## 4. 需要创建的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    └── domain/
        └── service/
            └── BufferPostingDomainService.java  # 缓冲规则匹配 + 辅助核算分摊领域服务
```

> **说明**：无需新建 Repository。辅助核算项使用已有 `AccountingVoucherRepository.insertAuxiliary()`，
> 缓冲记账明细使用已有 `BaseMapper.insert()` 逐条写入。

---

## 5. AuxiliaryItemData 领域数据对象

```java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class AuxiliaryItemData {
    private String entryId;
    private String voucherNo;
    private String subjectCode;
    private String auxType;
    private String auxCode;
    private String auxName;
    private Integer changeDirection;  // 1=增, 2=减
    private BigDecimal amount;
    private LocalDate accountingDate;
}
```

---

## 6. BufferPostingDetailData 领域数据对象

```java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class BufferPostingDetailData {
    private Long ruleId;
    private Integer bufferMode;
    private String voucherNo;
    private String entryId;
    private String txnNo;
    private String traceNo;
    private Integer traceSeq;
    private String businessCode;
    private String tradingCode;
    private String payChannel;
    private Integer tradeType;
    private LocalDateTime tradeTime;
    private String accountNo;
    private Integer debitCredit;
    private String currency;
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String summary;
    private Long sharding;  // account_no 哈希取模
}
```

---

## 7. BufferPostingDomainService 实现要点

### 7.1 类结构

```java
@Service
@RequiredArgsConstructor
public class BufferPostingDomainService {

    private final BufferPostingRuleMapper bufferPostingRuleMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;
    private final AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;
}
```

### 7.2 calculateAuxiliaryAllocation 方法

```java
/**
 * 辅助核算分摊引擎
 *
 * 执行流程：
 *   1. 遍历该分录行对应的辅助核算配置列表
 *   2. 按 allocation_method 计算每条辅助核算项的金额
 *      - 1（不分摊）→ 不生成记录，跳过
 *      - 2（固定金额）→ auxAmount = allocation_value
 *      - 3（按比例）→ 前 N-1 按比例计算，最后一条补差
 *   3. 校验：辅助核算分摊金额合计 == 分录金额
 *   4. 返回 AuxiliaryItemData 列表
 *
 * @param entryData 分录数据
 * @param auxiliaryConfigs 该分录行对应的辅助核算配置列表（来自 t_accounting_rule_auxiliary）
 * @return 分摊后的辅助核算项列表
 * @throws AccountException 当分摊金额校验失败时抛出
 */
public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
    VoucherEntryData entryData,
    List<AccountingRuleAuxiliaryPO> auxiliaryConfigs) {

    List<AuxiliaryItemData> result = new ArrayList<>();

    // 按分摊方式分组
    List<AccountingRuleAuxiliaryPO> proportional = auxiliaryConfigs.stream()
        .filter(c -> c.getAllocationMethod() == AllocationMethodEnum.PROPORTIONAL)
        .collect(Collectors.toList());

    List<AccountingRuleAuxiliaryPO> fixed = auxiliaryConfigs.stream()
        .filter(c -> c.getAllocationMethod() == AllocationMethodEnum.FIXED)
        .collect(Collectors.toList());

    // 固定金额分摊
    BigDecimal fixedTotal = BigDecimal.ZERO;
    for (AccountingRuleAuxiliaryPO config : fixed) {
        BigDecimal auxAmount = config.getAllocationValue();
        if (auxAmount.compareTo(entryData.getAmount()) > 0) {
            throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                "辅助核算固定金额超过分录金额: auxCode=" + config.getAuxCode()
                + ", auxAmount=" + auxAmount + ", entryAmount=" + entryData.getAmount());
        }
        fixedTotal = fixedTotal.add(auxAmount);
        result.add(buildAuxiliaryItem(entryData, config, auxAmount));
    }

    // 按比例分摊（前 N-1 按比例，最后一条补差）
    if (!proportional.isEmpty()) {
        BigDecimal remaining = entryData.getAmount().subtract(fixedTotal);
        BigDecimal allocated = BigDecimal.ZERO;

        for (int i = 0; i < proportional.size(); i++) {
            AccountingRuleAuxiliaryPO config = proportional.get(i);
            BigDecimal auxAmount;

            if (i < proportional.size() - 1) {
                // 前 N-1 条按比例计算（allocation_value 为百分比，如 30 表示 30%）
                BigDecimal ratio = config.getAllocationValue().divide(
                    new BigDecimal("100"), 6, RoundingMode.HALF_UP);
                auxAmount = remaining.multiply(ratio).setScale(6, RoundingMode.HALF_UP);
                allocated = allocated.add(auxAmount);
            } else {
                // 最后一条补差
                auxAmount = remaining.subtract(allocated);
            }

            result.add(buildAuxiliaryItem(entryData, config, auxAmount));
        }
    }

    // 最终校验
    BigDecimal auxTotal = result.stream()
        .map(AuxiliaryItemData::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (auxTotal.compareTo(entryData.getAmount()) != 0) {
        throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
            "辅助核算分摊金额合计不等于分录金额: auxTotal=" + auxTotal
            + ", entryAmount=" + entryData.getAmount());
    }

    return result;
}

/**
 * 构建辅助核算项
 */
private AuxiliaryItemData buildAuxiliaryItem(
    VoucherEntryData entryData,
    AccountingRuleAuxiliaryPO config,
    BigDecimal amount) {

    return new AuxiliaryItemData(
        entryData.getEntryId(),
        entryData.getVoucherNo(),
        entryData.getSubjectCode(),
        config.getAuxType(),
        config.getAuxCode(),
        config.getAuxType(),  // auxName 暂用 auxType 填充
        entryData.getDebitCredit(),  // 增减方向与分录借贷方向一致
        amount,
        entryData.getAccountingDate()
    );
}
```

### 7.3 persistAuxiliaryItems 方法

```java
/**
 * 逐条写入辅助核算项到 t_accounting_voucher_auxiliary
 */
public void persistAuxiliaryItems(List<AuxiliaryItemData> items) {
    if (items.isEmpty()) return;

    for (AuxiliaryItemData item : items) {
        AccountingVoucherAuxiliaryPO po = new AccountingVoucherAuxiliaryPO();
        po.setVoucherNo(item.getVoucherNo());
        po.setEntryId(item.getEntryId());
        po.setSubjectCode(item.getSubjectCode());
        po.setAuxType(item.getAuxType());
        po.setAuxCode(item.getAuxCode());
        po.setAuxName(item.getAuxName());
        po.setChangeDirection(item.getChangeDirection());
        po.setAmount(item.getAmount());
        po.setAccountingDate(item.getAccountingDate());
        voucherAuxiliaryMapper.insert(po);
    }
}
```

### 7.4 matchBufferRule 方法

```java
/**
 * 缓冲规则匹配
 *
 * 匹配条件（AND）：
 *   1. businessCode + tradingCode + payChannel 与流水一致
 *   2. subject_code = 规则 subject_code（或规则 subject_code IS NULL/空时匹配任意）
 *   3. account_no = 规则 account_no（或规则 account_no IS NULL/空时匹配任意）
 *   4. debit_credit 一致
 *   5. accounting_date 在 [effective_time, expiration_time] 区间内
 *
 * @return 匹配到的缓冲规则（无则 null）
 */
public BufferPostingRulePO matchBufferRule(
    VoucherEntryData entryData,
    String businessCode, String tradingCode, String payChannel) {

    List<BufferPostingRulePO> rules = bufferPostingRuleMapper.selectMatchingRules(
        businessCode, tradingCode, payChannel,
        entryData.getSubjectCode(), entryData.getAccountNo(),
        entryData.getDebitCredit(), entryData.getAccountingDate());

    return rules.isEmpty() ? null : rules.get(0);
}
```

### 7.5 persistBufferPostingDetails 方法

```java
/**
 * 逐条写入缓冲记账明细
 */
public void persistBufferPostingDetails(List<BufferPostingDetailData> details) {
    if (details.isEmpty()) return;

    for (BufferPostingDetailData d : details) {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setRuleId(d.getRuleId());
        po.setBufferMode(BufferModeEnum.fromCode(d.getBufferMode()));
        po.setVoucherNo(d.getVoucherNo());
        po.setEntryId(d.getEntryId());
        po.setTxnNo(d.getTxnNo());
        po.setTraceNo(d.getTraceNo());
        po.setTraceSeq(d.getTraceSeq());
        po.setBusinessCode(d.getBusinessCode());
        po.setTradingCode(d.getTradingCode());
        po.setPayChannel(d.getPayChannel());
        po.setTradeType(TradeTypeEnum.fromCode(d.getTradeType()));
        po.setTradeTime(d.getTradeTime());
        po.setAccountNo(d.getAccountNo());
        po.setDebitCredit(DebitCreditEnum.fromCode(d.getDebitCredit()));
        po.setCurrency(d.getCurrency());
        po.setAmount(d.getAmount());
        po.setAccountingDate(d.getAccountingDate());
        po.setSummary(d.getSummary());
        po.setStatus(BufferStatusEnum.PENDING); // 1=待入账
        po.setSharding(d.getSharding());
        bufferPostingDetailMapper.insert(po);
    }
}
```

### 7.6 calculateSharding 工具方法

```java
/**
 * 计算分片值（同一账户必须在同一分片）
 * 按 account_no 的 hashCode 取绝对值
 */
public Long calculateSharding(String accountNo) {
    return (long) Math.abs(accountNo.hashCode());
}
```

---

## 8. 编码要点

- 领域服务加 `@Service` 注解，构造器注入依赖
- 按比例分摊最后一条补差，防止浮点精度累积
- 辅助核算金额合计必须等于分录金额（使用 `compareTo()` 校验）
- 缓冲规则匹配时，同一分录最多匹配一条（LIMIT 1）
- **P1-6 修复**：缓冲规则 XML 中使用 `IS NULL OR 空串` 双重判断
- 分片值按 account_no 哈希取模，保证同一账户落在同一分片
- 固定金额分摊时校验不超过分录总金额
- 逐条 insert 复用已有 BaseMapper.insert()，无需 batchInsert

---

## 9. 完成标准

- [ ] `BufferPostingRuleMapper` 补充 `selectMatchingRules` 方法 + 对应 XML（含 NULL/空串判断，P1-6 修复）
- [ ] `BufferPostingDomainService` 独立领域服务
- [ ] `calculateAuxiliaryAllocation` 按比例分摊正确（最后一条补差）
- [ ] `calculateAuxiliaryAllocation` 固定金额分摊正确
- [ ] `calculateAuxiliaryAllocation` 不分摊正确（跳过）
- [ ] 辅助核算项金额校验（合计 = 分录金额）
- [ ] `persistAuxiliaryItems` 逐条写入辅助核算项
- [ ] `matchBufferRule` 缓冲规则匹配条件完整
- [ ] `persistBufferPostingDetails` 逐条写入缓冲记账明细
- [ ] `calculateSharding` 分片值计算正确
- [ ] `AuxiliaryItemData` / `BufferPostingDetailData` 领域数据对象定义正确
- [ ] 金额计算全部使用 `BigDecimal`，禁止 `new BigDecimal(double)`
- [ ] BigDecimal 比较使用 `compareTo()`，禁止 `equals()`
- [ ] AllocationMethodEnum / BufferModeEnum / TradeTypeEnum / DebitCreditEnum 枚举转换正确

---

## 10. 下一步

完成后通知 Java-C 可以开始，详见 `docs/prompt/tasks/step-10-java-c.md`。
