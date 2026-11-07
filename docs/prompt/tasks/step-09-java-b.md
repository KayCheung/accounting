# step-09-java-b · 预开户检查领域服务（独立类）+ 账户列表解析 + 集成 Step 8

> **Step 9 子任务** | 归属：`@Java` 工程师-B
> 前置依赖：Step 8（`AccountOpeningDomainService`）、Step 9 Java-A（流水持久化 + P0-4 规则 XML 修复）

---

## 1. 任务目标

实现独立的 `AccountPreCheckDomainService` 领域服务，解析记账规则并预开户检查，确保后续凭证生成和过账所需账户全部就绪。

**S1 修复说明**：此服务是独立类，**不修改** `JournalingDomainService`，避免多人合并冲突。

---

## 2. 必读资源

| # | 文件 | 用途 |
|---|------|------|
| 1 | `docs/ai-rules/java.md` | 分层架构、事务规范 |
| 2 | `docs/ai-rules/accounting.md` | 账务领域规范、并发控制（多账户加锁升序） |
| 3 | `docs/sql/3-rule.sql` | `t_accounting_rule` / `t_accounting_rule_detail` DDL |
| 4 | `docs/design/flowchart/auto_account_opening_flow.mmd` | 记账自动开户流程图 |
| 5 | `docs/design/domain-model.md` | 规则域模型 |
| 6 | `accounting-core/.../entity/AccountingRulePO.java` | 记账规则 PO |
| 7 | `accounting-core/.../entity/AccountingRuleDetailPO.java` | 规则明细 PO |
| 8 | `accounting-core/.../domain/service/AccountOpeningDomainService.java` | Step 8 开户领域服务 |
| 9 | `accounting-core/.../repository/AccountingRuleRepository.java` | **已有**规则仓储（含 `selectByBusinessKey` + `selectDetailsWithAuxiliary`） |
| 10 | `accounting-core/.../repository/AccountRepository.java` | **已有**账户仓储 |

---

## 3. 需要创建的文件

```
accounting-core/
└── src/main/java/com/kltb/accounting/core/
    └── domain/
        └── service/
            └── AccountPreCheckDomainService.java # 预开户检查领域服务（独立类）
```

---

## 4. 需要修改的文件

**修改文件**：`AccountRepository.java` + `AccountMapper.java` + `AccountMapper.xml`

补充 `selectByOwnerIdAndSubjectCode` 方法，用于查询已存在账户的完整信息：

```java
// AccountMapper.java — 追加
AccountPO selectByOwnerIdAndSubjectCode(@Param("ownerId") String ownerId,
                                         @Param("subjectCode") String subjectCode);
```

```xml
<!-- AccountMapper.xml — 追加 -->
<select id="selectByOwnerIdAndSubjectCode" resultType="...AccountPO">
    SELECT * FROM t_account
    WHERE owner_id = #{ownerId} AND subject_code = #{subjectCode} AND is_delete = 0
    LIMIT 1
</select>
```

```java
// AccountRepository.java — 追加
/**
 * 按所有者ID + 科目编码查询账户
 */
public AccountPO selectByOwnerIdAndSubjectCode(String ownerId, String subjectCode) {
    return accountMapper.selectByOwnerIdAndSubjectCode(ownerId, subjectCode);
}
```

---

## 5. AccountPreCheckDomainService 实现要点（S1 修复：独立类）

### 5.1 类结构

```java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 预开户检查领域服务
 * 独立于 JournalingDomainService，避免多人修改同一文件（S1 修复）
 */
@Service
@RequiredArgsConstructor
public class AccountPreCheckDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountRepository accountRepository;
    private final AccountOpeningDomainService accountOpeningDomainService;
```

### 5.2 checkAndOpenAccounts 方法

```java
/**
 * 预开户检查：根据业务参数解析所需账户，不存在则自动开户
 *
 * 执行流程：
 *   1. 匹配记账规则（复用已有 selectByBusinessKey，P0-4 已确保只返回 status=2）
 *   2. 解析规则明细（复用已有 selectDetailsWithAuxiliary，N1 修复）
 *   3. 构建待检查账户列表（customerId + customerType 在构建时绑定，M3 修复）
 *   4. 逐账户检查 + 自动开户
 *   5. 收集所有 account_no 后升序排列（M4 修复：排的是真实 account_no）
 *
 * @param businessCode 业务线编码
 * @param tradingCode  交易编码
 * @param payChannel   支付渠道
 * @param customerMap  客户ID → CustomerTypeEnum 映射
 * @return 账户编号列表（已按 account_no 升序排列）
 */
public List<String> checkAndOpenAccounts(
    String businessCode, String tradingCode, String payChannel,
    Map<String, CustomerTypeEnum> customerMap) {

    // 1. 匹配记账规则（已有方法，P0-4 确保只返回 status=2）
    AccountingRulePO rule = accountingRuleRepository.selectByBusinessKey(
        businessCode, tradingCode, payChannel);
    if (rule == null) {
        throw new AccountException(ResultCode.RULE_NOT_FOUND,
            "记账规则不存在: " + businessCode + "/" + tradingCode + "/" + payChannel);
    }
    if (rule.getStatus() != RuleStatusEnum.ENABLED) {
        // 防御性检查：即使 XML 有 status=2 过滤，也加一层 Java 判断
        throw new AccountException(ResultCode.RULE_DISABLED,
            "记账规则已停用: " + rule.getRuleName());
    }

    // 2. 解析规则明细（复用已有方法，N1 修复）
    List<AccountingRuleDetailPO> ruleDetails = accountingRuleRepository
        .selectDetailsWithAuxiliary(rule.getId());
    if (ruleDetails.isEmpty()) {
        throw new AccountException(ResultCode.RULE_NOT_FOUND, "记账规则明细为空");
    }

    // 3. 构建去重的账户检查列表（M3 修复：customerId + customerType 绑定）
    // Key: ownerId:subjectCode, Value: (ownerId, customerType, subjectCode)
    Map<String, AccountCheckItem> checkItems = new LinkedHashMap<>();
    for (Map.Entry<String, CustomerTypeEnum> entry : customerMap.entrySet()) {
        String customerId = entry.getKey();
        CustomerTypeEnum customerType = entry.getValue();
        for (AccountingRuleDetailPO detail : ruleDetails) {
            String key = customerId + ":" + detail.getSubjectCode();
            if (!checkItems.containsKey(key)) {
                checkItems.put(key, new AccountCheckItem(
                    customerId, customerType, detail.getSubjectCode()));
            }
        }
    }

    // 4. 逐账户检查 + 自动开户，收集 account_no
    List<String> accountNos = new ArrayList<>();
    for (AccountCheckItem item : checkItems.values()) {
        String accountNo;

        // 先检查已存在账户
        AccountPO existing = accountRepository.selectByOwnerIdAndSubjectCode(
            item.ownerId, item.subjectCode);
        if (existing != null) {
            accountNo = existing.getAccountNo();
        } else {
            // 不存在 → 自动开户（使用 requestNo = traceNo 的上下文，由调用方传入）
            AccountPO opened = accountOpeningDomainService.openExternalAccount(
                businessCode, item.ownerId, item.customerType, item.subjectCode,
                "JOURNAL-" + System.currentTimeMillis());
            accountNo = opened.getAccountNo();
        }

        accountNos.add(accountNo);
    }

    // 5. 按真实 account_no 升序排列（M4 修复）
    return accountNos.stream().sorted().collect(Collectors.toList());
}

/**
 * 账户检查项（M3 修复：绑定 customerId + customerType + subjectCode）
 */
private static class AccountCheckItem {
    final String ownerId;
    final CustomerTypeEnum customerType;
    final String subjectCode;

    AccountCheckItem(String ownerId, CustomerTypeEnum customerType, String subjectCode) {
        this.ownerId = ownerId;
        this.customerType = customerType;
        this.subjectCode = subjectCode;
    }
}
```

> **S4 修复说明**：`selectByBusinessKey` 的 XML 已在 P0-4 补充 `AND status = 2` 过滤。但领域服务中仍保留防御性判断 `rule.getStatus() != RuleStatusEnum.ENABLED`，确保 SQL 层漏掉时 Java 层能拦截。

---

## 6. 编码要点

- 此类是独立 `@Service`，不修改 `JournalingDomainService`（S1 修复）
- 规则匹配复用已有 `selectByBusinessKey`（N1 修复），不新建重复方法
- 规则明细复用已有 `selectDetailsWithAuxiliary`（N1 修复）
- 账户检查时 `customerId + customerType` 在构建 `AccountCheckItem` 时绑定（M3 修复）
- 返回的是真实 `account_no` 升序列表（M4 修复），不是临时字符串 key 排序
- 开户失败必须抛出 `AccountException` 阻断流程
- 多账户按 `account_no` 升序排列（防死锁，为后续 Step 10/11/12 加锁做准备）

---

## 7. 完成标准

- [ ] `AccountPreCheckDomainService` 为独立领域服务类（S1 修复）
- [ ] `AccountRepository` 补充 `selectByOwnerIdAndSubjectCode` 方法
- [ ] 规则匹配复用已有 `selectByBusinessKey`（N1 修复）
- [ ] 规则明细复用已有 `selectDetailsWithAuxiliary`（N1 修复）
- [ ] 规则不存在/停用的异常处理（含防御性 status 判断，S4/N1 修复）
- [ ] 账户列表构建时绑定 customerId + customerType（M3 修复）
- [ ] 逐账户检查 + 不存在时自动开户（集成 Step 8）
- [ ] 返回真实 account_no 升序列表（M4 修复）
- [ ] 开户失败抛出 AccountException 阻断流程

---

## 8. 下一步

完成后通知 Java-C 可以开始，详见 `docs/prompt/tasks/step-09-java-c.md`。
