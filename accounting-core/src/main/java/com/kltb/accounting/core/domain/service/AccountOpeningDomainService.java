package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.OwnerTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import com.kltb.accounting.core.domain.model.BatchOpenResult;
import com.kltb.accounting.core.infrastructure.account.AccountNoGenerator;
import com.kltb.accounting.core.infrastructure.account.AccountRuleContext;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DictionaryCacheService;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 开户领域服务（核心业务逻辑）
 * <p>
 * 支持两种开户模式：
 * 1. 外部客户账户开户 — 基于开户模板，记账流程中自动触发或前端手动触发
 * 2. 内部账户开户 — 系统初始化或科目配置变更时自动创建
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountOpeningDomainService {

    private static final String OWNER_INNER = "INNER";
    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final AccountRepository accountRepository;
    private final SubAccountRepository subAccountRepository;
    private final SubjectRepository subjectRepository;
    private final AccountNoGenerator accountNoGenerator;
    private final DistributedLockTemplate distributedLockTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired(required = false)
    private DictionaryCacheService dictionaryCacheService;

    /**
     * 外部客户账户开户（单账户兼容接口）
     *
     * @param businessCode 业务线编码
     * @param customerId   客户ID（作为 ownerId）
     * @param customerType 客户类型枚举
     * @param subjectCode  科目编码（可选，传入则精确匹配模板，不传则匹配首个启用模板）
     * @param requestNo    开户请求号
     */
    public AccountPO openExternalAccount(String businessCode, String customerId,
                                          CustomerTypeEnum customerType, String subjectCode, String requestNo) {
        return openExternalAccount(businessCode, customerId, null, customerType, subjectCode, requestNo);
    }

    /**
     * 外部客户账户开户（含 customerName）
     */
    public AccountPO openExternalAccount(String businessCode, String customerId, String customerName,
                                          CustomerTypeEnum customerType, String subjectCode, String requestNo) {
        List<AccountPO> accounts = openExternalAccounts(businessCode, customerId, customerName, customerType, subjectCode, requestNo);
        if (accounts.isEmpty()) {
            throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND, "未开出任何账户");
        }
        return accounts.get(0);
    }

    /**
     * 外部客户开户（支持单模板配置多个会计科目账户一次性批量开出）
     *
     * @param businessCode 业务线编码
     * @param customerId   客户ID（作为 ownerId）
     * @param customerName 客户名称（用于户名生成规则）
     * @param customerType 客户类型枚举
     * @param subjectCode  科目编码（可选；传入则精确匹配单个模板开户，不传则开出该业务线+客户类型下全部启用的模板科目账户）
     * @param requestNo    开户请求号
     * @return 开出的账户列表
     */
    public List<AccountPO> openExternalAccounts(String businessCode, String customerId, String customerName,
                                                 CustomerTypeEnum customerType, String subjectCode, String requestNo) {
        return openExternalAccounts(businessCode, customerId, customerName, customerType, subjectCode, null, requestNo);
    }

    /**
     * 外部客户开户（支持单模板配置多个会计科目账户一次性批量开出，可指定模板名称）
     */
    public List<AccountPO> openExternalAccounts(String businessCode, String customerId, String customerName,
                                                 CustomerTypeEnum customerType, String subjectCode,
                                                 String templateName, String requestNo) {
        String ownerId = customerId;

        // 1. 匹配开户模板列表
        List<AccountTemplatePO> templates;
        if (subjectCode != null && !subjectCode.isEmpty()) {
            AccountTemplatePO t = matchTemplate(businessCode, customerType, subjectCode);
            templates = List.of(t);
        } else {
            List<AccountTemplatePO> allEnabled = subjectRepository.selectEnabledTemplates(businessCode, customerType.getCode());
            if (allEnabled != null && !allEnabled.isEmpty() && StrUtil.isNotBlank(templateName)) {
                List<AccountTemplatePO> filtered = allEnabled.stream()
                        .filter(t -> templateName.equalsIgnoreCase(t.getTemplateName()))
                        .collect(Collectors.toList());
                templates = !filtered.isEmpty() ? filtered : allEnabled;
            } else if (allEnabled != null && !allEnabled.isEmpty()) {
                templates = allEnabled;
            } else {
                // 兜底查首个启用
                AccountTemplatePO first = matchTemplate(businessCode, customerType, null);
                templates = List.of(first);
            }
        }

        final int totalTemplates = templates.size();
        List<AccountPO> openedAccounts = new ArrayList<>();
        int itemIndex = 0;

        for (AccountTemplatePO template : templates) {
            itemIndex++;
            // 校验模板状态与是否支持自动开户
            if (template.getStatus() != TemplateStatusEnum.ENABLED) {
                if (totalTemplates == 1) {
                    throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_ENABLED,
                            "开户模板未启用: templateId=" + template.getId());
                }
                continue;
            }
            if (Boolean.FALSE.equals(template.getAutoOpen())) {
                if (totalTemplates == 1) {
                    throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_AUTO_OPEN,
                            "开户模板不支持自动开户: templateId=" + template.getId());
                }
                continue;
            }

            final String templateSubjectCode = template.getSubjectCode();
            final String lockKey = "account:open:" + ownerId + ":" + templateSubjectCode;
            final int finalIndex = itemIndex;

            String subReqNo = requestNo != null ? requestNo : "";
            if (totalTemplates > 1 && requestNo != null) {
                subReqNo = requestNo + "-" + finalIndex;
            }
            if (subReqNo.length() > 32) {
                subReqNo = subReqNo.substring(0, 32);
            }
            final String finalActualRequestNo = subReqNo;

            AccountPO account = distributedLockTemplate.execute(lockKey, 5, -1, () -> {
                // 双重检查：若账户已存在
                if (accountRepository.existsByOwnerIdAndSubjectCode(ownerId, templateSubjectCode)) {
                    if (totalTemplates == 1) {
                        throw new AccountException(ResultCode.ACCOUNT_ALREADY_EXISTS,
                                "账户已存在: ownerId=" + ownerId + ", subjectCode=" + templateSubjectCode);
                    }
                    return accountRepository.selectByOwnerIdAndSubjectCode(ownerId, templateSubjectCode);
                }

                // 校验科目末级 + 允许开户
                AccountSubjectPO subject = subjectRepository.selectByCode(templateSubjectCode);
                if (subject == null) {
                    throw new AccountException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在: " + templateSubjectCode);
                }
                if (Boolean.FALSE.equals(subject.getLeaf())) {
                    throw new AccountException(ResultCode.SUBJECT_NOT_LEAF, "非末级科目不允许开户: " + templateSubjectCode);
                }
                if (Boolean.FALSE.equals(subject.getAllowOpenAccount())) {
                    throw new AccountException(ResultCode.SUBJECT_NOT_ALLOW_OPEN_ACCOUNT,
                            "科目不允许开户: " + templateSubjectCode);
                }

                CustomerTypeEnum effectiveCustomerType = customerType != null ? customerType : template.getCustomerType();
                AccountRuleContext ruleContext = AccountRuleContext.builder()
                        .businessCode(businessCode)
                        .accountType(template.getAccountType())
                        .accountTypeName(resolveAccountTypeName(template.getAccountType()))
                        .currency(template.getCurrency())
                        .currencyName(resolveCurrencyName(template.getCurrency()))
                        .balanceDirection(template.getBalanceDirection())
                        .balanceDirectionCode(template.getBalanceDirection() != null ? template.getBalanceDirection().getCode() : 1)
                        .directionName(template.getBalanceDirection() != null ? template.getBalanceDirection().getDesc() : "借")
                        .customerType(effectiveCustomerType)
                        .ownerTypeCode(effectiveCustomerType != null ? effectiveCustomerType.getCode() : 1)
                        .ownerTypeName(effectiveCustomerType != null ? effectiveCustomerType.getDesc() : "个人")
                        .subjectCode(templateSubjectCode)
                        .subjectName(subject.getSubjectName())
                        .ownerId(ownerId)
                        .ownerName(customerName)
                        .build();

                String rawAccountNo = accountNoGenerator.generateByRule(template.getAcctNoRule(), ruleContext);
                final String finalAccountNo = (rawAccountNo != null)
                        ? rawAccountNo
                        : accountNoGenerator.generateExternalAccountNo();

                String rawAccountName = accountNoGenerator.generateAccountName(template.getAcctNameRule(), ruleContext);
                final String finalAccountName = (rawAccountName != null)
                        ? rawAccountName
                        : (ownerId.length() > 32 ? ownerId.substring(0, 32) : ownerId);

                return transactionTemplate.execute(status -> {
                    AccountPO newAccount = buildExternalAccount(finalAccountNo, finalAccountName, template, ownerId, customerType, finalActualRequestNo);
                    accountRepository.insert(newAccount);

                    List<SubAccountPO> subAccounts = createSubAccountsInternal(newAccount);

                    log.info("External account opened: accountNo={} accountName={} ownerId={} subjectCode={} subAccounts={}",
                            finalAccountNo, finalAccountName, ownerId, templateSubjectCode, subAccounts.size());

                    return newAccount;
                });
            });

            if (account != null) {
                openedAccounts.add(account);
            }
        }

        return openedAccounts;
    }

    /**
     * 内部账户开户（完成持久化后返回 AccountPO）
     * 带分布式锁保护，防止并发重复开户。
     *
     * @param subjectCode 科目编码
     */
    public AccountPO openInternalAccount(String subjectCode) {
        String lockKey = "account:open:INNER:" + subjectCode;
        return distributedLockTemplate.execute(lockKey, 5, -1, () ->
                doOpenInternalAccount(subjectCode));
    }

    /**
     * 内部账户开户核心逻辑（必须在分布式锁内调用）
     */
    private AccountPO doOpenInternalAccount(String subjectCode) {
        // 1. 校验科目
        AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
        if (subject == null) {
            throw new AccountException(ResultCode.SUBJECT_NOT_FOUND, "科目不存在: " + subjectCode);
        }
        if (Boolean.FALSE.equals(subject.getLeaf())) {
            throw new AccountException(ResultCode.SUBJECT_NOT_LEAF, "非末级科目不允许开户: " + subjectCode);
        }
        if (Boolean.FALSE.equals(subject.getAllowOpenAccount())) {
            throw new AccountException(ResultCode.SUBJECT_NOT_ALLOW_OPEN_ACCOUNT, "科目不允许开户: " + subjectCode);
        }

        // 2. 双重检查：锁内再次校验账户是否已存在
        if (accountRepository.existsByOwnerIdAndSubjectCode(OWNER_INNER, subjectCode)) {
            throw new AccountException(ResultCode.ACCOUNT_ALREADY_EXISTS,
                    "内部账户已存在: subjectCode=" + subjectCode);
        }

        // 3. 生成内部账户编号（永久递增，不重置）与账户名称（科目名称 + "-内部账户"）
        String accountNo = accountNoGenerator.generateInternalAccountNo(subjectCode);
        String subjectName = subject.getSubjectName();
        String accountName = (StrUtil.isNotBlank(subjectName) ? subjectName : subjectCode) + "-内部账户";

        // 4. 在事务中创建主账户 + 两个子账户
        AccountPO account = buildInternalAccount(accountNo, accountName, subject, subjectCode);
        return transactionTemplate.execute(status -> {
            accountRepository.insert(account);
            List<SubAccountPO> subAccounts = createSubAccountsInternal(account);
            log.info("Internal account opened: accountNo={} subjectCode={} subAccounts={}",
                    accountNo, subjectCode, subAccounts.size());
            return account;
        });
    }

    /**
     * 批量扫描并创建内部账户（非事务性，逐账户 try-catch）
     *
     * @return BatchOpenResult 包含 totalCount / alreadyExists / newlyCreated / failed
     */
    public BatchOpenResult scanAndOpenInternalAccounts() {
        List<AccountSubjectPO> subjects = subjectRepository.selectAllowOpenAccountLeafSubjects();
        BatchOpenResult result = new BatchOpenResult();
        result.setTotalCount(subjects.size());

        for (AccountSubjectPO subject : subjects) {
            String subjectCode = subject.getSubjectCode();
            try {
                String lockKey = "account:open:INNER:" + subjectCode;
                distributedLockTemplate.execute(
                        lockKey, 2, -1,
                        () -> doOpenInternalAccount(subjectCode)
                );
                result.incrementNewlyCreated();
            } catch (AccountException e) {
                if (e.getResultCode() == ResultCode.ACCOUNT_ALREADY_EXISTS) {
                    result.incrementAlreadyExists();
                } else {
                    result.incrementFailed();
                    result.addFailedReason("科目 " + subjectCode + ": " + e.getMessage());
                }
            } catch (Exception e) {
                result.incrementFailed();
                result.addFailedReason("科目 " + subjectCode + ": 未知异常 - " + e.getMessage());
            }
        }

        log.info("Batch internal account scan completed: total={} newlyCreated={} alreadyExists={} failed={}",
                result.getTotalCount(), result.getNewlyCreated(), result.getAlreadyExists(), result.getFailed());
        return result;
    }

    // ==================== Private Methods ====================

    /**
     * 匹配开户模板
     */
    private AccountTemplatePO matchTemplate(String businessCode, CustomerTypeEnum customerType, String subjectCode) {
        AccountTemplatePO template;
        if (subjectCode != null && !subjectCode.isEmpty()) {
            // 精确匹配
            template = subjectRepository.selectTemplateByBusinessKey(businessCode, customerType.getCode(), subjectCode);
            if (template == null) {
                throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND,
                        "开户模板不存在: businessCode=" + businessCode + ", customerType=" + customerType.getDesc() + ", subjectCode=" + subjectCode);
            }
        } else {
            // 兜底匹配：首个启用的模板
            template = subjectRepository.selectFirstEnabledTemplate(businessCode, customerType.getCode());
            if (template == null) {
                throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND,
                        "开户模板不存在: businessCode=" + businessCode + ", customerType=" + customerType.getDesc());
            }
        }

        // 校验模板状态
        if (template.getStatus() != TemplateStatusEnum.ENABLED) {
            throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_ENABLED,
                    "开户模板未启用: templateId=" + template.getId());
        }
        if (Boolean.FALSE.equals(template.getAutoOpen())) {
            throw new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_AUTO_OPEN,
                    "开户模板不支持自动开户: templateId=" + template.getId());
        }

        return template;
    }

    /**
     * 构建外部客户账户 PO
     */
    private AccountPO buildExternalAccount(String accountNo, String accountName,
                                            AccountTemplatePO template, String ownerId,
                                            CustomerTypeEnum customerType, String requestNo) {
        AccountPO account = new AccountPO();
        account.setAccountNo(accountNo);
        account.setAccountName(accountName);
        account.setOwnerId(ownerId);
        account.setOwnerType(OwnerTypeEnum.fromCode(customerType.getCode()));
        account.setSubjectCode(template.getSubjectCode());
        account.setAccountType(template.getAccountType());
        account.setCurrency(template.getCurrency() != null ? template.getCurrency() : "CNY");
        account.setBalanceDirection(template.getBalanceDirection());
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatusEnum.NORMAL);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setRequestNo(requestNo);
        account.setOpenDate(LocalDate.now(ZONE_SHANGHAI));
        account.setVersion(0L);
        return account;
    }

    /**
     * 构建内部账户 PO
     */
    private AccountPO buildInternalAccount(String accountNo, String accountName,
                                            AccountSubjectPO subject, String subjectCode) {
        AccountPO account = new AccountPO();
        account.setAccountNo(accountNo);
        account.setAccountName(accountName);
        account.setOwnerId(OWNER_INNER);
        account.setOwnerType(OwnerTypeEnum.OTHER);
        account.setSubjectCode(subjectCode);
        account.setAccountType("INTERNAL");
        account.setCurrency("CNY");
        account.setBalanceDirection(subject.getDebitCredit() != null
                ? BalanceDirectionEnum.fromCode(subject.getDebitCredit().getCode())
                : BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatusEnum.NORMAL);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setRequestNo("SYSTEM_INIT");
        account.setOpenDate(LocalDate.now(ZONE_SHANGHAI));
        account.setVersion(0L);
        return account;
    }

    /**
     * 为主账户创建子账户（可用 + 冻结）
     * 注意：此方法必须在事务中被调用
     */
    private List<SubAccountPO> createSubAccountsInternal(AccountPO account) {
        List<SubAccountPO> subAccounts = new ArrayList<>(2);

        // 可用子账户
        SubAccountPO available = new SubAccountPO();
        available.setAccountNo(account.getAccountNo());
        available.setBalanceType(BalanceTypeEnum.AVAILABLE);
        available.setBalanceDirection(account.getBalanceDirection());
        available.setBalance(BigDecimal.ZERO);
        available.setVersion(0L);
        subAccountRepository.insert(available);
        subAccounts.add(available);

        // 冻结子账户
        SubAccountPO frozen = new SubAccountPO();
        frozen.setAccountNo(account.getAccountNo());
        frozen.setBalanceType(BalanceTypeEnum.FROZEN);
        frozen.setBalanceDirection(account.getBalanceDirection());
        frozen.setBalance(BigDecimal.ZERO);
        frozen.setVersion(0L);
        subAccountRepository.insert(frozen);
        subAccounts.add(frozen);

        return subAccounts;
    }

    private String resolveCurrencyName(String currency) {
        if (StrUtil.isBlank(currency)) {
            return "";
        }
        if (dictionaryCacheService != null) {
            try {
                List<DictionaryPO> dicts = dictionaryCacheService.getByType("currency");
                if (dicts != null) {
                    for (DictionaryPO po : dicts) {
                        if (currency.equalsIgnoreCase(po.getDictCode())) {
                            return po.getDictName();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        switch (currency.toUpperCase()) {
            case "CNY": return "人民币";
            case "USD": return "美元";
            case "EUR": return "欧元";
            case "HKD": return "港币";
            default: return currency;
        }
    }

    private String resolveAccountTypeName(String accountType) {
        if (StrUtil.isBlank(accountType)) {
            return "";
        }
        if (dictionaryCacheService != null) {
            try {
                List<DictionaryPO> dicts = dictionaryCacheService.getByType("account_type");
                if (dicts != null) {
                    for (DictionaryPO po : dicts) {
                        if (accountType.equalsIgnoreCase(po.getDictCode())) {
                            return po.getDictName();
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        switch (accountType.toUpperCase()) {
            case "CASH": return "现金账户";
            case "DEPOSIT": return "存款账户";
            case "SETTLE": return "结算账户";
            default: return accountType;
        }
    }
}
