package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BusinessRecordStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.PostingTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VoucheringDomainServiceTest {

    @Mock private AccountingRuleRepository accountingRuleRepository;
    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @Mock private BusinessRecordRepository businessRecordRepository;
    @Mock private BusinessDetailRepository businessDetailRepository;
    @Mock private RedisSequenceGenerator seqGen;
    @Mock private RuleScriptExecutor ruleScriptExecutor;

    @InjectMocks private VoucheringDomainService voucheringDomainService;

    @Test
    @DisplayName("借贷平衡校验: 借=贷 通过")
    void validateBalance_balanced_shouldPass() {
        List<VoucherEntryData> entries = List.of(
                new VoucherEntryData("E1", "VOU1", 1, "1001", "A001", 1, new BigDecimal("1000"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE),
                new VoucherEntryData("E2", "VOU1", 2, "2001", "A001", 2, new BigDecimal("1000"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE)
        );
        voucheringDomainService.validateDebitCreditBalance(entries);
    }

    @Test
    @DisplayName("借贷平衡校验: 借!=贷 抛出异常")
    void validateBalance_unbalanced_shouldThrow() {
        List<VoucherEntryData> entries = List.of(
                new VoucherEntryData("E1", "VOU1", 1, "1001", "A001", 1, new BigDecimal("1000"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE),
                new VoucherEntryData("E2", "VOU1", 2, "2001", "A001", 2, new BigDecimal("999.99"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE)
        );
        assertThatThrownBy(() -> voucheringDomainService.validateDebitCreditBalance(entries))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.VOUCHER_NOT_BALANCED);
                    assertThat(e.getMessage()).contains("借贷不平衡");
                });
    }

    @Test
    @DisplayName("借贷平衡校验: 多借多贷平衡")
    void validateBalance_multiDebitMultiCredit_balanced() {
        List<VoucherEntryData> entries = List.of(
                new VoucherEntryData("E1", "VOU1", 1, "1001", "A001", 1, new BigDecimal("600"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE),
                new VoucherEntryData("E2", "VOU1", 2, "1002", "A001", 1, new BigDecimal("400"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE),
                new VoucherEntryData("E3", "VOU1", 3, "2001", "A001", 2, new BigDecimal("1000"), "CNY", "test", LocalDate.now(), Boolean.FALSE, Boolean.FALSE)
        );
        voucheringDomainService.validateDebitCreditBalance(entries);
    }

    @Test
    @DisplayName("SpEL 金额计算: 无脚本返回业务金额")
    void calculateAmount_noScript_returnsBusinessAmount() {
        AccountingRuleDetailPO ruleDetail = new AccountingRuleDetailPO();
        ruleDetail.setExtendScript(null);
        BusinessDetailPO businessDetail = new BusinessDetailPO();
        businessDetail.setAmount(new BigDecimal("5000"));
        assertThat(voucheringDomainService.calculateEntryAmount(ruleDetail, businessDetail))
                .isEqualTo(new BigDecimal("5000"));
    }

    @Test
    @DisplayName("SpEL 金额计算: 空脚本返回业务金额")
    void calculateAmount_blankScript_returnsBusinessAmount() {
        AccountingRuleDetailPO ruleDetail = new AccountingRuleDetailPO();
        ruleDetail.setExtendScript("   ");
        BusinessDetailPO businessDetail = new BusinessDetailPO();
        businessDetail.setAmount(new BigDecimal("3000"));
        assertThat(voucheringDomainService.calculateEntryAmount(ruleDetail, businessDetail))
                .isEqualTo(new BigDecimal("3000"));
    }

    @Test
    @DisplayName("SpEL 金额计算: 有脚本返回脚本计算结果")
    void calculateAmount_withScript_returnsScriptResult() {
        AccountingRuleDetailPO ruleDetail = new AccountingRuleDetailPO();
        ruleDetail.setExtendScript("#root.amount");
        BusinessDetailPO businessDetail = new BusinessDetailPO();
        businessDetail.setAmount(new BigDecimal("10000"));
        when(ruleScriptExecutor.execute("#root.amount", businessDetail)).thenReturn(new BigDecimal("10000"));
        assertThat(voucheringDomainService.calculateEntryAmount(ruleDetail, businessDetail)).isEqualTo(new BigDecimal("10000"));
        verify(ruleScriptExecutor).execute("#root.amount", businessDetail);
    }

    @Test
    @DisplayName("规则匹配: 规则不存在抛出异常")
    void matchRule_ruleNotFound_shouldThrow() {
        when(accountingRuleRepository.selectByBusinessKey("LOAN", "DISBURSE", "BANK")).thenReturn(null);
        assertThatThrownBy(() -> voucheringDomainService.matchRule("LOAN", "DISBURSE", "BANK"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.RULE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("规则匹配: 规则未启用抛出异常")
    void matchRule_ruleDisabled_shouldThrow() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(1L);
        rule.setRuleName("test");
        rule.setBusinessCode("LOAN");
        rule.setTradingCode("DISBURSE");
        rule.setPayChannel("BANK");
        rule.setStatus(com.kltb.accounting.core.domain.enums.RuleStatusEnum.DISABLED);
        when(accountingRuleRepository.selectByBusinessKey("LOAN", "DISBURSE", "BANK")).thenReturn(rule);
        assertThatThrownBy(() -> voucheringDomainService.matchRule("LOAN", "DISBURSE", "BANK"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.RULE_DISABLED);
                });
    }

    @Test
    @DisplayName("规则匹配: 正常匹配返回规则明细")
    void matchRule_normalFlow_shouldReturnRuleWithDetails() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(1L);
        rule.setRuleName("test");
        rule.setBusinessCode("LOAN");
        rule.setTradingCode("DISBURSE");
        rule.setPayChannel("BANK");
        rule.setVoucherType("PAYMENT");
        rule.setStatus(com.kltb.accounting.core.domain.enums.RuleStatusEnum.ENABLED);
        List<AccountingRuleDetailPO> details = List.of(
                buildRuleDetail(1L, "1301", 1),
                buildRuleDetail(2L, "1001", 2)
        );
        when(accountingRuleRepository.selectByBusinessKey("LOAN", "DISBURSE", "BANK")).thenReturn(rule);
        when(accountingRuleRepository.selectDetailsWithAuxiliary(1L)).thenReturn(details);
        when(accountingRuleRepository.selectAuxiliariesByRuleId(1L)).thenReturn(Map.of());
        VoucheringDomainService.AccountingRuleWithDetails result = voucheringDomainService.matchRule("LOAN", "DISBURSE", "BANK");
        assertThat(result.getRule()).isEqualTo(rule);
        assertThat(result.getDetails()).hasSize(2);
    }

    @Test
    @DisplayName("流水加载: 流水不存在抛出异常")
    void loadJournal_journalNotFound_shouldThrow() {
        when(businessRecordRepository.selectByTraceNo("TRC001")).thenReturn(null);
        assertThatThrownBy(() -> voucheringDomainService.loadJournal("TRC001"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException e = (ServiceException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.JOURNAL_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("流水加载: 正常加载返回流水明细")
    void loadJournal_normalFlow_shouldReturnJournalWithDetails() {
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo("TRC001");
        record.setBusinessCode("LOAN");
        record.setTradingCode("DISBURSE");
        record.setPayChannel("BANK");
        record.setAmount(new BigDecimal("10000"));
        record.setStatus(BusinessRecordStatusEnum.PROCESSING);
        record.setAccountingDate(LocalDate.now());
        record.setTradeTime(LocalDateTime.now());
        record.setSummary("test");
        List<BusinessDetailPO> details = List.of(buildBusinessDetail("CUST001", "PRINCIPAL", new BigDecimal("10000")));
        when(businessRecordRepository.selectByTraceNo("TRC001")).thenReturn(record);
        when(businessDetailRepository.selectByTraceNo("TRC001")).thenReturn(details);
        VoucheringDomainService.JournalWithDetails result = voucheringDomainService.loadJournal("TRC001");
        assertThat(result.getRecord()).isEqualTo(record);
        assertThat(result.getDetails()).hasSize(1);
    }

    @Test
    @DisplayName("凭证持久化: 正常流程生成凭证号并写入")
    void persistVoucher_normalFlow_shouldCreateVoucherAndEntries() {
        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setTraceNo("TRC001");
        journal.setTraceSeq(0);
        journal.setBusinessCode("LOAN");
        journal.setTradingCode("DISBURSE");
        journal.setPayChannel("BANK");
        journal.setAmount(new BigDecimal("10000"));
        journal.setTradeTime(LocalDateTime.now());
        journal.setSummary("test");
        journal.setAccountingDate(LocalDate.of(2026, 6, 24));
        journal.setTradeType(com.kltb.accounting.core.domain.enums.TradeTypeEnum.NORMAL);
        AccountingRulePO rule = new AccountingRulePO();
        rule.setVoucherType("PAYMENT");
        List<VoucherEntryData> entries = List.of(
                new VoucherEntryData(null, null, 1, "1301", "A001", 1, new BigDecimal("10000"), "CNY", "test", LocalDate.of(2026, 6, 24), Boolean.FALSE, Boolean.FALSE),
                new VoucherEntryData(null, null, 2, "1001", "A001", 2, new BigDecimal("10000"), "CNY", "test", LocalDate.of(2026, 6, 24), Boolean.FALSE, Boolean.FALSE)
        );
        when(seqGen.generate("VOU", LocalDate.now(), 6, 25)).thenReturn("VOU20260624000001");
        when(seqGen.generate(eq("ENT"), any(LocalDateTime.class), eq("yyyyMMddHHmmssSSS"), eq(4), eq(2)))
                .thenReturn("ENT202606241030000001")
                .thenReturn("ENT202606241030000002");
        String voucherNo = voucheringDomainService.persistVoucher(journal, rule, entries, "SYSTEM");
        assertThat(voucherNo).isEqualTo("VOU20260624000001");
        verify(accountingVoucherRepository).insert(argThat(v ->
                v.getVoucherNo().equals("VOU20260624000001")
                        && v.getPostingType() == PostingTypeEnum.AUTOMATIC
                        && v.getStatus() == VoucherStatusEnum.PENDING
        ));
        verify(accountingVoucherRepository, times(2)).insertEntry(any());
    }

    private AccountingRuleDetailPO buildRuleDetail(Long id, String subjectCode, int rowNum) {
        AccountingRuleDetailPO detail = new AccountingRuleDetailPO();
        detail.setId(id);
        detail.setSubjectCode(subjectCode);
        detail.setDebitCredit(rowNum == 1 ? DebitCreditEnum.DEBIT : DebitCreditEnum.CREDIT);
        detail.setExtendScript("");
        detail.setRowNum(rowNum);
        detail.setFundsType("PRINCIPAL");
        detail.setCurrency("CNY");
        detail.setSummary("test");
        return detail;
    }

    private BusinessDetailPO buildBusinessDetail(String customerId, String fundsType, BigDecimal amount) {
        BusinessDetailPO detail = new BusinessDetailPO();
        detail.setCustomerId(customerId);
        detail.setCustomerType(com.kltb.accounting.core.domain.enums.CustomerTypeEnum.ENTERPRISE);
        detail.setFundsType(fundsType);
        return detail;
    }
}
