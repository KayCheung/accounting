package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountScopeEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.domain.model.CustomerCheckInfo;
import com.kltb.accounting.core.domain.model.AccountPreCheckResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AccountPreCheckDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AccountPreCheckDomainServiceTest {

    @Mock
    private AccountingRuleRepository accountingRuleRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountOpeningDomainService accountOpeningDomainService;

    @InjectMocks
    private AccountPreCheckDomainService accountPreCheckDomainService;

    @Test
    @DisplayName("预开户检查: 规则不存在抛出 RULE_NOT_FOUND")
    void preCheckAndOpenAccounts_ruleNotFound_shouldThrow() {
        when(accountingRuleRepository.selectByBusinessKey("BIZ001", "TRADE001", "CH01"))
                .thenReturn(null);

        assertThatThrownBy(() -> accountPreCheckDomainService.preCheckAndOpenAccounts(
                "BIZ001", "TRADE001", "CH01", Map.of(), "TRACE_001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.RULE_NOT_FOUND));
    }

    @Test
    @DisplayName("预开户检查: 规则未启用抛出 RULE_DISABLED")
    void preCheckAndOpenAccounts_ruleDisabled_shouldThrow() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(1L);
        rule.setRuleName("测试规则");
        rule.setStatus(RuleStatusEnum.DISABLED);
        when(accountingRuleRepository.selectByBusinessKey("BIZ001", "TRADE001", "CH01"))
                .thenReturn(rule);

        assertThatThrownBy(() -> accountPreCheckDomainService.preCheckAndOpenAccounts(
                "BIZ001", "TRADE001", "CH01", Map.of(), "TRACE_001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.RULE_DISABLED));
    }

    @Test
    @DisplayName("预开户检查: 自动预开户时透传 customerName，开户号requestNo必须与traceNo完全一致")
    void preCheckAndOpenAccounts_autoOpenExternalAccount_shouldPassExactTraceNo() {
        String traceNo = "TR2026101411200010001";
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(10L);
        rule.setBusinessCode("PAYMENT");
        rule.setStatus(RuleStatusEnum.ENABLED);

        AccountingRuleDetailPO d1 = new AccountingRuleDetailPO();
        d1.setAccountScope(AccountScopeEnum.EXTERNAL);
        d1.setSubjectCode("1001");

        AccountingRuleDetailPO d2 = new AccountingRuleDetailPO();
        d2.setAccountScope(AccountScopeEnum.INTERNAL);
        d2.setSubjectCode("2001");

        when(accountingRuleRepository.selectByBusinessKey("PAYMENT", "PAY", "ALIPAY"))
                .thenReturn(rule);
        when(accountingRuleRepository.selectDetailsWithAuxiliary(10L))
                .thenReturn(List.of(d1, d2));

        // 外部账户不存在，触发自动开户（透传 customerName="张三"）
        when(accountRepository.selectByOwnerIdAndSubjectCode("USER123", "1001"))
                .thenReturn(null);
        AccountPO openedExt = new AccountPO();
        openedExt.setAccountNo("ACCT_EXT_999");
        when(accountOpeningDomainService.openExternalAccount(
                eq("PAYMENT"), eq("USER123"), eq("张三"), eq(CustomerTypeEnum.PERSONAL), eq("1001"), eq(traceNo)))
                .thenReturn(openedExt);

        // 内部账户不存在，触发自动开内部户
        when(accountRepository.selectByOwnerIdAndSubjectCode("INNER", "2001"))
                .thenReturn(null);
        AccountPO openedInner = new AccountPO();
        openedInner.setAccountNo("ACCT_INN_111");
        when(accountOpeningDomainService.openInternalAccount("2001"))
                .thenReturn(openedInner);

        CustomerCheckInfo checkInfo = new CustomerCheckInfo(CustomerTypeEnum.PERSONAL, "张三");
        AccountPreCheckResult result = accountPreCheckDomainService.preCheckAndOpenAccounts(
                "PAYMENT", "PAY", "ALIPAY", Map.of("USER123", checkInfo), traceNo);

        assertThat(result).isNotNull();
        // 账户按 account_no 升序排列 ("ACCT_EXT_999" < "ACCT_INN_111")
        assertThat(result.getSortedAccountNos()).containsExactly("ACCT_EXT_999", "ACCT_INN_111");
        assertThat(result.getAccountMapping().get("USER123:1001")).isEqualTo("ACCT_EXT_999");
        assertThat(result.getAccountMapping().get("INNER:2001")).isEqualTo("ACCT_INN_111");

        // 核心断言：openExternalAccount 收到的 customerName 必须为 "张三"，requestNo 必须完全等于 traceNo
        ArgumentCaptor<String> reqCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountOpeningDomainService).openExternalAccount(
                eq("PAYMENT"), eq("USER123"), eq("张三"), eq(CustomerTypeEnum.PERSONAL), eq("1001"), reqCaptor.capture());
        assertThat(reqCaptor.getValue()).isEqualTo(traceNo);
    }
}
