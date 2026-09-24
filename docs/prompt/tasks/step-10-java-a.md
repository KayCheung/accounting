# step-10-java-a · P0 补充 + VoucherNoGenerator/EntryIdGenerator + VoucheringDomainService

> **Step 10 子任务** | 归属：`@Java` 工程师-A
> 前置依赖：Step 9（流水入库已完成）、Step 7（SpEL 预加载校验已就绪）

---

## 1. 任务目标

完成 Step 10 五项前置补充任务（P0-1~P0-5），并实现凭证号/分录号生成器、凭证生成领域服务（规则匹配 + SpEL计算 + 分录生成 + 借贷平衡校验 + 凭证持久化）。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范、POJO 规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、状态机、借贷方向约束 |
| 3 | `docs/sql/2-voucher.sql` | `t_accounting_voucher` / `t_accounting_voucher_entry` DDL |
| 4 | `docs/sql/3-rule.sql` | `t_accounting_rule` / `t_accounting_rule_detail` DDL |
| 5 | `docs/sql/5-journal.sql` | `t_business_record` / `t_business_detail` DDL |
| 6 | `docs/design/domain-model.md` | 凭证域、规则域、流水域模型 |
| 7 | `accounting-core/.../entity/AccountingVoucherPO.java` | 凭证 PO |
| 8 | `accounting-core/.../entity/AccountingVoucherEntryPO.java` | 分录 PO |
| 9 | `accounting-core/.../mapper/AccountingVoucherMapper.java` | **已有**空壳 Mapper |
| 10 | `accounting-core/.../mapper/AccountingVoucherEntryMapper.java` | **已有**空壳 Mapper |
| 11 | `accounting-core/.../mapper/AccountingRuleMapper.java` | **已有**（含 selectByBusinessKey） |
| 12 | `accounting-core/.../application/RuleApplicationService.java` | SpEL 表达式引擎已有实现，参考其 SPEL_PARSER |
| 13 | `accounting-core/.../config/TransactionConfig.java` | 事务模板配置 |
| 14 | `accounting-core/.../repository/BusinessRecordRepository.java` | Step 9 已有，用于 loadJournal |
| 15 | `accounting-core/.../repository/BusinessDetailRepository.java` | Step 9 已有，用于 loadJournal |
| 14 | `accounting-core/.../repository/AccountingRuleRepository.java` | 已有 selectByBusinessKey + selectDetailsWithAuxiliary + **selectAuxiliariesByRuleId**（P1-3 修复关键） |
| 15 | `accounting-core/.../repository/AccountingVoucherRepository.java` | **已有**（Step 6 创建），内含 insert/insertEntry/insertAuxiliary/updateById |
| 16 | `accounting-core/.../account/TransactionNoGenerator.java` | **参考**：Lua 脚本原子化模式 |

---

## 3. 前置补充任务（P0-1~P0-5）

### 3.1 P0-1：AccountingVoucherMapper 补充 updateTxnNoByVoucherNo

**修改文件**：`AccountingVoucherMapper.java`（已有，追加方法）

> **注意**：`AccountingVoucherRepository` 已在 Step 6 创建，内含 `insert()`、`selectByTraceNo()`、`insertEntry()`、`insertAuxiliary()` 等方法，无需新建。

```java
// AccountingVoucherMapper.java — 追加
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountingVoucherMapper extends BaseMapper<AccountingVoucherPO> {

    // 已有方法：selectWithEntries、selectByTraceNo、selectReversalByOrig、countByBusinessKey

    /**
     * 回填事务编号到凭证
     */
    @Update("UPDATE t_accounting_voucher SET txn_no = #{txnNo}, version = version + 1 WHERE voucher_no = #{voucherNo} AND is_delete = 0")
    int updateTxnNoByVoucherNo(@Param("voucherNo") String voucherNo,
                                @Param("txnNo") String txnNo);
}
```

**同时补充 `AccountingVoucherRepository` 中的便捷方法**（如已有则跳过）：

```java
// AccountingVoucherRepository.java — 追加（如不存在）

/**
 * 回填事务编号到凭证
 */
public int updateTxnNoByVoucherNo(String voucherNo, String txnNo) {
    return voucherMapper.updateTxnNoByVoucherNo(voucherNo, txnNo);
}
```

### 3.2 P0-2：AccountingVoucherEntryMapper 补充 updateStatusByVoucherNo

**修改文件**：`AccountingVoucherEntryMapper.java`（已有，追加方法）

```java
// AccountingVoucherEntryMapper.java — 追加
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AccountingVoucherEntryMapper extends BaseMapper<AccountingVoucherEntryPO> {

    // 已有方法：selectPendingPosting、selectByVoucherNo

    /**
     * 按凭证号更新分录状态
     */
    @Update("UPDATE t_accounting_voucher_entry SET status = #{status}, version = version + 1 WHERE voucher_no = #{voucherNo} AND is_delete = 0")
    int updateStatusByVoucherNo(@Param("voucherNo") String voucherNo,
                                 @Param("status") Integer status);
}
```

### 3.3 P0-3：VoucherNoGenerator 凭证号生成器

**新建文件**：`VoucherNoGenerator.java`

> **P0-1 修复**：使用 Lua 脚本原子化初始化，消除 `isNotExists() → set() → expire()` 的 TOCTOU 竞态窗口。
> 直接复用 Step 9 修复后的 `TransactionNoGenerator` 模式。

```java
package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 凭证号生成器（P0-1 修复：Lua 脚本原子化初始化，消除 TOCTOU 竞态）
 * 格式：VOU + yyyyMMdd + seq6，如 VOU20260512000001
 * Redis key：vou:seq:{yyyyMMdd}
 * TTL：25 小时（每日自动重置）
 */
@Component
@RequiredArgsConstructor
public class VoucherNoGenerator {

    private final RedissonClient redissonClient;

    public String generate() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "vou:seq:" + date;

        String luaScript =
                "if redis.call('exists', KEYS[1]) == 0 then " +
                "  redis.call('set', KEYS[1], '0') " +
                "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
                "end " +
                "return redis.call('incr', KEYS[1])";

        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
                .evalAsync(RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(TimeUnit.HOURS.toSeconds(25)));

        long seq;
        try {
            seq = (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成凭证编号失败: " + key, e);
        }
        return "VOU" + date + String.format("%06d", seq);
    }
}
```

### 3.4 P0-4：EntryIdGenerator 分录流水号生成器

**新建文件**：`EntryIdGenerator.java`

> **P0-2 修复**：同上，使用 Lua 脚本原子化。

```java
package com.kltb.accounting.core.infrastructure.account;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RFuture;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.LongCodec;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * 分录流水号生成器（P0-2 修复：Lua 脚本原子化初始化，消除 TOCTOU 竞态）
 * 格式：ENT + yyyyMMddHHmmssSSS + seq4，如 ENT20260512103000001001
 * Redis key：ent:seq:{yyyyMMddHHmmssSSS}
 * TTL：2 小时（按秒级时间窗口重置）
 */
@Component
@RequiredArgsConstructor
public class EntryIdGenerator {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final RedissonClient redissonClient;

    public String generate() {
        String timeSuffix = LocalDateTime.now().format(TIME_FMT);
        String key = "ent:seq:" + timeSuffix;

        String luaScript =
                "if redis.call('exists', KEYS[1]) == 0 then " +
                "  redis.call('set', KEYS[1], '0') " +
                "  redis.call('expire', KEYS[1], tonumber(ARGV[1])) " +
                "end " +
                "return redis.call('incr', KEYS[1])";

        RFuture<Object> future = redissonClient.getScript(LongCodec.INSTANCE)
                .evalAsync(RScript.Mode.READ_WRITE,
                        luaScript,
                        RScript.ReturnType.INTEGER,
                        Collections.singletonList(key),
                        String.valueOf(TimeUnit.HOURS.toSeconds(2)));

        long seq;
        try {
            seq = (Long) future.get();
        } catch (Exception e) {
            throw new RuntimeException("生成分录流水号失败: " + key, e);
        }
        return "ENT" + timeSuffix + String.format("%04d", seq);
    }
}
```

### 3.5 P0-5：RuleScriptExecutor SpEL 脚本执行器

**新建文件**：`RuleScriptExecutor.java`

```java
package com.kltb.accounting.core.infrastructure.spel;

import com.kltb.accounting.core.common.exception.AccountException;
import com.kltb.accounting.core.common.enums.ResultCode;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * SpEL 规则脚本执行器
 */
@Component
public class RuleScriptExecutor {

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * 执行 SpEL 脚本，返回 BigDecimal 金额
     *
     * @param script SpEL 表达式（来自 extend_script 字段）
     * @param rootObject 根上下文对象（BusinessDetailPO）
     * @return 计算结果金额
     */
    public BigDecimal execute(String script, Object rootObject) {
        try {
            StandardEvaluationContext context = new StandardEvaluationContext(rootObject);
            Object result = PARSER.parseExpression(script).getValue(context);
            if (result == null) {
                throw new AccountException(ResultCode.SPEL_CALC_ERROR, "SpEL 脚本返回 null: " + script);
            }
            if (result instanceof BigDecimal) {
                return (BigDecimal) result;
            }
            if (result instanceof Number) {
                return BigDecimal.valueOf(((Number) result).doubleValue());
            }
            throw new AccountException(ResultCode.SPEL_CALC_ERROR,
                "SpEL 脚本返回值非数值类型: " + script + ", 实际类型: " + result.getClass().getName());
        } catch (AccountException e) {
            throw e;
        } catch (Exception e) {
            throw new AccountException(ResultCode.SPEL_CALC_ERROR,
                "SpEL 脚本执行失败: " + script + ", 错误: " + e.getMessage());
        }
    }
}
```

> **P1-4 修复说明**：`#root` 为 `BusinessDetailPO` 对象，其有 `amount` 字段（BigDecimal），因此 `#root.amount` 可直接引用业务明细金额。

---

## 4. 需要创建的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    ├── domain/
    │   └── service/
    │       └── VoucheringDomainService.java     # 凭证生成领域服务
    └── infrastructure/
        ├── account/
        │   ├── VoucherNoGenerator.java          # 凭证号生成器（P0-3）
        │   └── EntryIdGenerator.java            # 分录流水号生成器（P0-4）
        └── spel/
            └── RuleScriptExecutor.java          # SpEL 脚本执行器（P0-5）
```

---

## 5. VoucherEntryData 领域数据对象

```java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class VoucherEntryData {
    private String entryId;
    private String voucherNo;
    private Integer rowNum;
    private String subjectCode;
    private String accountNo;
    private Integer debitCredit;   // 1=借, 2=贷
    private BigDecimal amount;
    private String currency;
    private String summary;
    private LocalDate accountingDate;
    private Boolean isUnilateral;  // 来自规则明细 is_unilateral
    private Boolean isBuffered;    // 缓冲匹配后标记（Java-B 设置）
}
```

---

## 6. VoucheringDomainService 实现要点

### 6.1 类结构

> **P1-1 修复**：不注入 TransactionTemplate，不开启独立事务。持久化操作由 Application Service 统一控制。

```java
@Service
@RequiredArgsConstructor
public class VoucheringDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessDetailRepository businessDetailRepository;
    private final VoucherNoGenerator voucherNoGenerator;
    private final EntryIdGenerator entryIdGenerator;
    private final RuleScriptExecutor ruleScriptExecutor;
}
```

### 6.2 loadJournal 方法

```java
/**
 * 按 traceNo 查询流水及其明细
 */
public JournalWithDetails loadJournal(String traceNo) {
    BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
    if (record == null) {
        throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "流水不存在: " + traceNo);
    }
    List<BusinessDetailPO> details = businessDetailRepository.selectByTraceNo(traceNo);
    return new JournalWithDetails(record, details);
}

@Data @AllArgsConstructor
public static class JournalWithDetails {
    private final BusinessRecordPO record;
    private final List<BusinessDetailPO> details;
}
```

### 6.3 matchRule 方法

> **P1-3 修复**：`AccountingRuleWithDetails` 额外携带 `Map<Long, List<AccountingRuleAuxiliaryPO>>` 辅助核算映射，
> 通过 `AccountingRuleRepository.selectAuxiliariesByRuleId()` 一次查询获取。

```java
/**
 * 匹配记账规则（含 status=2 校验，P0-4 已在 XML 中补充）
 */
public AccountingRuleWithDetails matchRule(
    String businessCode, String tradingCode, String payChannel) {

    AccountingRulePO rule = accountingRuleRepository.selectByBusinessKey(
        businessCode, tradingCode, payChannel);
    if (rule == null) {
        throw new AccountException(ResultCode.RULE_NOT_FOUND,
            "记账规则不存在: businessCode=" + businessCode + ", tradingCode=" + tradingCode
            + ", payChannel=" + payChannel);
    }
    List<AccountingRuleDetailPO> details = accountingRuleRepository.selectDetailsWithAuxiliary(rule.getId());

    // P1-3 修复：一次查询获取该规则下所有辅助核算配置，按 ruleDetailId 分组
    Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap =
        accountingRuleRepository.selectAuxiliariesByRuleId(rule.getId());

    return new AccountingRuleWithDetails(rule, details, auxMap);
}

@Data @AllArgsConstructor
public static class AccountingRuleWithDetails {
    private final AccountingRulePO rule;
    private final List<AccountingRuleDetailPO> details;
    // P1-3 修复：ruleDetailId → 辅助核算配置列表的映射
    private final Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap;
}
```

### 6.4 calculateEntryAmount 方法

> **P1-4 修复说明**：`#root` 为 `BusinessDetailPO`，其有 `amount`（BigDecimal）、`fundsType`（String）、`customerId` 等字段。
> 脚本 `#root.amount` 等价于 `businessDetail.getAmount()`。

```java
/**
 * 执行 SpEL 脚本计算分录金额
 */
public BigDecimal calculateEntryAmount(
    AccountingRuleDetailPO ruleDetail,
    BusinessDetailPO businessDetail) {

    String script = ruleDetail.getExtendScript();
    if (StringUtils.isBlank(script)) {
        // 无 SpEL 脚本时，直接使用业务明细金额
        return businessDetail.getAmount();
    }
    return ruleScriptExecutor.execute(script, businessDetail);
}
```

### 6.5 validateDebitCreditBalance 方法

```java
/**
 * 借贷平衡校验
 */
public void validateDebitCreditBalance(List<VoucherEntryData> entries) {
    BigDecimal totalDebit = entries.stream()
        .filter(e -> e.getDebitCredit() == 1)
        .map(VoucherEntryData::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal totalCredit = entries.stream()
        .filter(e -> e.getDebitCredit() == 2)
        .map(VoucherEntryData::getAmount)
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalDebit.compareTo(totalCredit) != 0) {
        BigDecimal diff = totalDebit.subtract(totalCredit).abs();
        throw new AccountException(ResultCode.VOUCHER_NOT_BALANCED,
            "借贷不平衡: ΣDebit=" + totalDebit + ", ΣCredit=" + totalCredit + ", 差额=" + diff);
    }
}
```

### 6.6 persistVoucher 方法

> **P1-1 修复**：不使用 `transactionTemplate.execute()`，只做 save 操作，由 Application Service 控制事务。
> **P1-5 修复**：`voucherType` 从 `rule.getVoucherType()` 获取，而非 journal。

```java
/**
 * 写入凭证 + 分录（不开启事务，由 Application Service 控制事务边界）
 *
 * @param journal 流水记录
 * @param rule 记账规则（P1-5 修复：从中获取 voucherType）
 * @param entries 分录列表
 * @param bookkeeperName 记账人
 * @return 生成的凭证号
 */
public String persistVoucher(
    BusinessRecordPO journal,
    AccountingRulePO rule,
    List<VoucherEntryData> entries,
    String bookkeeperName) {

    String voucherNo = voucherNoGenerator.generate();

    // 1. 写入 t_accounting_voucher
    AccountingVoucherPO voucher = new AccountingVoucherPO();
    voucher.setVoucherNo(voucherNo);
    voucher.setTxnNo(""); // 空字符串占位，后续回填
    voucher.setTraceNo(journal.getTraceNo());
    voucher.setTraceSeq(journal.getTraceSeq());
    voucher.setVoucherType(rule.getVoucherType());  // P1-5 修复：从规则获取
    voucher.setPostingType(PostingTypeEnum.AUTOMATIC); // 2=机制凭证
    voucher.setBusinessCode(journal.getBusinessCode());
    voucher.setTradingCode(journal.getTradingCode());
    voucher.setPayChannel(journal.getPayChannel());
    voucher.setTradeType(journal.getTradeType());
    voucher.setTradeTime(journal.getTradeTime());
    voucher.setAmount(journal.getAmount());
    voucher.setStatus(VoucherStatusEnum.PENDING); // 1=未过账
    voucher.setAccountingDate(journal.getAccountingDate());
    voucher.setSummary(journal.getSummary());
    voucher.setBookkeeperName(bookkeeperName);
    accountingVoucherRepository.insert(voucher);

    // 2. 写入 t_accounting_voucher_entry（逐条 insert，复用已有 insertEntry）
    for (VoucherEntryData entry : entries) {
        entry.setVoucherNo(voucherNo);
        entry.setEntryId(entryIdGenerator.generate());

        AccountingVoucherEntryPO entryPO = new AccountingVoucherEntryPO();
        entryPO.setVoucherNo(voucherNo);
        entryPO.setEntryId(entry.getEntryId());
        entryPO.setRowNum(entry.getRowNum());
        entryPO.setSubjectCode(entry.getSubjectCode());
        entryPO.setAccountNo(entry.getAccountNo());
        entryPO.setDebitCredit(entry.getDebitCredit());
        entryPO.setAmount(entry.getAmount());
        entryPO.setCurrency(StringUtils.defaultIfBlank(entry.getCurrency(), "CNY"));
        entryPO.setSummary(entry.getSummary());
        entryPO.setStatus(VoucherEntryStatusEnum.PENDING); // 1=未过账
        entryPO.setAccountingDate(entry.getAccountingDate());
        accountingVoucherRepository.insertEntry(entryPO);
    }

    return voucherNo;
}
```

---

## 7. 编码要点

- 领域服务加 `@Service` 注解，构造器注入依赖
- **P1-1 修复**：persistVoucher 不开启事务，只做 save
- SpEL 脚本执行异常统一由 `RuleScriptExecutor` 转换为 `AccountException(SPEL_CALC_ERROR)`
- 借贷平衡校验使用 `BigDecimal.compareTo()`
- 凭证号/分录号生成器使用 Lua 脚本原子化（P0-1/P0-2 修复）
- **P1-5 修复**：voucherType 从 `rule.getVoucherType()` 获取
- **P1-3 修复**：matchRule 返回 AccountingRuleWithDetails 含 auxMap 映射
- 金额计算使用 `BigDecimal`，禁止 `new BigDecimal(double)`，禁止 `equals()` 比较
- 复用已有 `AccountingVoucherRepository.insert()` / `insertEntry()` / `insertAuxiliary()`，无需新建 Repository

---

## 8. 完成标准

- [ ] P0-1: `AccountingVoucherMapper` 补充 `updateTxnNoByVoucherNo`
- [ ] P0-2: `AccountingVoucherEntryMapper` 补充 `updateStatusByVoucherNo`
- [ ] P0-3: `VoucherNoGenerator` 使用 Lua 脚本原子化（无 TOCTOU 竞态）
- [ ] P0-4: `EntryIdGenerator` 使用 Lua 脚本原子化（无 TOCTOU 竞态）
- [ ] P0-5: `RuleScriptExecutor` SpEL 执行器正确（含异常处理，#root 为 BusinessDetailPO）
- [ ] `VoucheringDomainService` 凭证生成全流程（独立类，**不含事务**）
- [ ] `loadJournal` 流水查询 + 状态校验
- [ ] `matchRule` 记账规则匹配（含 auxMap 映射，P1-3 修复）
- [ ] `calculateEntryAmount` SpEL 计算分录金额（含空脚本回退）
- [ ] `validateDebitCreditBalance` 借贷平衡校验
- [ ] `persistVoucher` 凭证 + 分录写入（**不开启事务**，P1-1 修复）
- [ ] voucherType 从 rule 获取（P1-5 修复）
- [ ] `VoucherEntryData` / `JournalWithDetails` / `AccountingRuleWithDetails` 领域数据对象定义正确
- [ ] 复用已有 `AccountingVoucherRepository` 的 insert/insertEntry 方法

---

## 9. 下一步

完成后通知 Java-B 可以开始，详见 `docs/prompt/tasks/step-10-java-b.md`。
