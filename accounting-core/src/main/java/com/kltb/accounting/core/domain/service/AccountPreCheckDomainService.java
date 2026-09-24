// accounting-core/src/main/java/com/kltb/accounting/core/domain/service/AccountPreCheckDomainService.java
package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 预开户检查领域服务（S1 修复：独立类，不修改 JournalingDomainService）
 * <p>
 * 负责：解析记账规则、构建待检查账户列表、逐账户检查 + 自动开户
 * <p>
 * 是否记账：是（预开户是记账流程的必要前置环节）
 * 异常处理：规则不存在/停用/开户失败 → 抛 AccountException 阻断流程
 */
@Service
@RequiredArgsConstructor
public class AccountPreCheckDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountRepository accountRepository;
    private final AccountOpeningDomainService accountOpeningDomainService;

    /**
     * 预开户检查：根据业务参数解析所需账户，不存在则自动开户
     * <p>
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
     * @param traceNo      系统跟踪号（用于生成唯一的 requestNo，P1-7 修复）
     * @return 账户编号列表（已按 account_no 升序排列）
     */
    public List<String> checkAndOpenAccounts(
            String businessCode, String tradingCode, String payChannel,
            Map<String, CustomerTypeEnum> customerMap, String traceNo) {

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
        AtomicInteger index = new AtomicInteger(0);
        for (AccountCheckItem item : checkItems.values()) {
            String accountNo;

            // 先检查已存在账户
            AccountPO existing = accountRepository.selectByOwnerIdAndSubjectCode(
                    item.ownerId, item.subjectCode);
            if (existing != null) {
                accountNo = existing.getAccountNo();
            } else {
                // P1-7 修复：requestNo = traceNo + 循环序号 + 纳秒，防止并发开户碰撞
                int i = index.incrementAndGet();
                String requestNo = "JOURNAL-" + traceNo + "-" + i + "-" + System.nanoTime();
                AccountPO opened = accountOpeningDomainService.openExternalAccount(
                        businessCode, item.ownerId, item.customerType, item.subjectCode,
                        requestNo);
                accountNo = opened.getAccountNo();
            }

            accountNos.add(accountNo);
        }

        // 5. 按真实 account_no 升序排列（M4 修复，为后续 Step 10/11/12 加锁防死锁做准备）
        return accountNos.stream().sorted().collect(Collectors.toList());
    }

    /**
     * 账户检查项（M3 修复：绑定 ownerId + customerType + subjectCode）
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
}
