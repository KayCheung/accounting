package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.RuleAuxiliaryRequest;
import com.kltb.accounting.api.request.RuleCreateRequest;
import com.kltb.accounting.api.request.RuleEntryRequest;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RuleApplicationService 单元测试
 *
 * 测试覆盖：借贷双方校验、SpEL 脚本校验、辅助核算分摊比例校验、停用凭证联动校验
 */
@ExtendWith(MockitoExtension.class)
class RuleApplicationServiceTest {

    @Mock
    private AccountingRuleRepository accountingRuleRepository;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private RuleApplicationService ruleApplicationService;

    @Test
    @DisplayName("创建规则: 缺少借方分录报 PARAM_ERROR")
    void create_noDebit_shouldThrow() {
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleCreateRequest request = buildRequestWithEntries(List.of(
                buildEntry("101001", 2),  // 只有贷方
                buildEntry("201001", 2)   // 只有贷方
        ));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("借贷双方");
    }

    @Test
    @DisplayName("创建规则: 缺少贷方分录报 PARAM_ERROR")
    void create_noCredit_shouldThrow() {
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleCreateRequest request = buildRequestWithEntries(List.of(
                buildEntry("101001", 1),  // 只有借方
                buildEntry("201001", 1)   // 只有借方
        ));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("借贷双方");
    }

    @Test
    @DisplayName("创建规则: 科目不存在报 SUBJECT_NOT_FOUND")
    void create_subjectNotFound_shouldThrow() {
        when(subjectRepository.selectByCode("101001")).thenReturn(null);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleCreateRequest request = buildRequestWithEntries(List.of(
                buildEntry("101001", 1),
                buildEntry("201001", 2)
        ));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("科目不存在");
    }

    @Test
    @DisplayName("创建规则: SpEL 脚本语法错误报 PARAM_ERROR")
    void create_invalidSpel_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO().setSubjectCode("101001");
        when(subjectRepository.selectByCode("101001")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleEntryRequest entry = buildEntry("101001", 1);
        entry.setExtendScript("this.is[invalid");
        RuleCreateRequest request = buildRequestWithEntries(List.of(entry, buildEntry("201001", 2)));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SpEL脚本语法错误");
    }

    @Test
    @DisplayName("创建规则: 辅助核算按比例分摊总和不等于1报 PARAM_ERROR")
    void create_auxiliaryRatioNotEqualOne_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO().setSubjectCode("101001");
        when(subjectRepository.selectByCode("101001")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleAuxiliaryRequest aux1 = new RuleAuxiliaryRequest()
                .setAuxType("DEPT").setAuxCode("D1").setAllocationMethod(3)
                .setAllocationValue(new BigDecimal("0.600000"));
        RuleAuxiliaryRequest aux2 = new RuleAuxiliaryRequest()
                .setAuxType("DEPT").setAuxCode("D2").setAllocationMethod(3)
                .setAllocationValue(new BigDecimal("0.300000"));

        RuleEntryRequest entry = buildEntry("101001", 1);
        entry.setAuxiliaries(List.of(aux1, aux2));
        RuleCreateRequest request = buildRequestWithEntries(List.of(entry, buildEntry("201001", 2)));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("分摊值之和必须等于1");
    }

    @Test
    @DisplayName("创建规则: 辅助核算SpEL分摊脚本为空报 PARAM_ERROR")
    void create_auxiliarySpelScriptEmpty_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO().setSubjectCode("101001");
        when(subjectRepository.selectByCode("101001")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleAuxiliaryRequest aux = new RuleAuxiliaryRequest()
                .setAuxType("PARTNER").setAuxCode("P01").setAllocationMethod(4)
                .setExtendScript("");
        RuleEntryRequest entry = buildEntry("101001", 1);
        entry.setAuxiliaries(List.of(aux));
        RuleCreateRequest request = buildRequestWithEntries(List.of(entry, buildEntry("201001", 2)));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SpEL脚本不能为空");
    }

    @Test
    @DisplayName("创建规则: 辅助核算SpEL语法错误报 PARAM_ERROR")
    void create_auxiliarySpelScriptInvalid_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO().setSubjectCode("101001");
        when(subjectRepository.selectByCode("101001")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleAuxiliaryRequest aux = new RuleAuxiliaryRequest()
                .setAuxType("PARTNER").setAuxCode("P01").setAllocationMethod(4)
                .setExtendScript("invalid[[syntax");
        RuleEntryRequest entry = buildEntry("101001", 1);
        entry.setAuxiliaries(List.of(aux));
        RuleCreateRequest request = buildRequestWithEntries(List.of(entry, buildEntry("201001", 2)));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("SpEL脚本语法错误");
    }

    @Test
    @DisplayName("创建规则: 业务键已存在报 PARAM_ERROR")
    void create_duplicateBusinessKey_shouldThrow() {
        AccountingRulePO existing = new AccountingRulePO()
                .setStatus(RuleStatusEnum.DISABLED);
        when(accountingRuleRepository.selectAnyByBusinessKey("PAYMENT", "CASH_PAY", "CASH")).thenReturn(existing);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleCreateRequest request = buildRequestWithEntries(List.of(
                buildEntry("101001", 1),
                buildEntry("201001", 2)
        ));

        assertThatThrownBy(() -> ruleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已存在记账规则");
    }

    @Test
    @DisplayName("停用规则: 成功将规则状态变更为 DISABLED（解耦历史凭证）")
    void disable_success() {
        AccountingRulePO rule = new AccountingRulePO()
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setStatus(RuleStatusEnum.ENABLED);
        when(accountingRuleRepository.selectRuleById(1L)).thenReturn(rule);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        ruleApplicationService.disable(1L);

        org.assertj.core.api.Assertions.assertThat(rule.getStatus()).isEqualTo(RuleStatusEnum.DISABLED);
        verify(accountingRuleRepository).updateRuleById(rule);
    }

    @Test
    @DisplayName("启用规则: 成功将规则状态变更为 ENABLED")
    void enable_success() {
        AccountingRulePO rule = new AccountingRulePO()
                .setStatus(RuleStatusEnum.PENDING);
        when(accountingRuleRepository.selectRuleById(1L)).thenReturn(rule);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        ruleApplicationService.enable(1L);

        org.assertj.core.api.Assertions.assertThat(rule.getStatus()).isEqualTo(RuleStatusEnum.ENABLED);
        verify(accountingRuleRepository).updateRuleById(rule);
    }

    @Test
    @DisplayName("更新规则: 替换分录时先逻辑删除旧明细再批量插入新明细")
    void update_shouldDeleteDetailsAndInsertNewEntries() {
        AccountingRulePO rule = new AccountingRulePO()
                .setStatus(RuleStatusEnum.DISABLED);
        when(accountingRuleRepository.selectRuleById(2L)).thenReturn(rule);
        when(subjectRepository.selectByCode("1001")).thenReturn(new AccountSubjectPO());
        when(subjectRepository.selectByCode("2001")).thenReturn(new AccountSubjectPO());
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        com.kltb.accounting.api.request.RuleUpdateRequest request = new com.kltb.accounting.api.request.RuleUpdateRequest();
        request.setRuleName("更新后规则");
        request.setVoucherType("GENERAL");
        request.setStatus(1);
        request.setEntries(List.of(
                buildEntry("1001", 1),
                buildEntry("2001", 2)
        ));

        ruleApplicationService.update(2L, request);

        verify(accountingRuleRepository).deleteRuleDetailByRuleId(2L);
        verify(accountingRuleRepository, times(2)).insertRuleDetail(any());
    }

    @Test
    @DisplayName("更新规则: 分录明细单边更新状态正确持久化")
    void update_shouldPreserveIsUnilateral() {
        AccountingRulePO rule = new AccountingRulePO().setStatus(RuleStatusEnum.DISABLED);
        when(accountingRuleRepository.selectRuleById(3L)).thenReturn(rule);
        when(subjectRepository.selectByCode("1001")).thenReturn(new AccountSubjectPO());
        when(subjectRepository.selectByCode("2001")).thenReturn(new AccountSubjectPO());
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        RuleEntryRequest entry1 = buildEntry("1001", 1).setIsUnilateral(true);
        RuleEntryRequest entry2 = buildEntry("2001", 2).setIsUnilateral(false);

        com.kltb.accounting.api.request.RuleUpdateRequest request = new com.kltb.accounting.api.request.RuleUpdateRequest()
                .setRuleName("更新单边状态")
                .setEntries(List.of(entry1, entry2));

        ruleApplicationService.update(3L, request);

        org.mockito.ArgumentCaptor<com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO> captor =
                org.mockito.ArgumentCaptor.forClass(com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO.class);
        verify(accountingRuleRepository, times(2)).insertRuleDetail(captor.capture());

        List<com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO> captured = captor.getAllValues();
        org.assertj.core.api.Assertions.assertThat(captured.get(0).getIsUnilateral()).isTrue();
        org.assertj.core.api.Assertions.assertThat(captured.get(0).getUnilateral()).isTrue();
        org.assertj.core.api.Assertions.assertThat(captured.get(1).getIsUnilateral()).isFalse();
    }

    @Test
    @DisplayName("查询规则: 单边更新状态正确回显到 Response DTO")
    void getById_shouldMapIsUnilateral() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(5L);
        rule.setRuleName("测试规则");
        when(accountingRuleRepository.selectRuleById(5L)).thenReturn(rule);

        com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO detail =
                new com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO();
        detail.setId(101L);
        detail.setRowNum(1)
                .setFundsType("PRINCIPAL")
                .setSubjectCode("1001")
                .setUnilateral(true);
        when(accountingRuleRepository.selectDetailsWithAuxiliary(5L)).thenReturn(List.of(detail));
        when(accountingRuleRepository.selectAuxiliariesByRuleId(5L)).thenReturn(java.util.Collections.emptyMap());

        com.kltb.accounting.core.application.dto.RuleResponse response = ruleApplicationService.getById(5L);

        org.assertj.core.api.Assertions.assertThat(response.getEntries()).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(response.getEntries().get(0).getIsUnilateral()).isTrue();
    }

    // ===== 辅助方法 =====

    private RuleEntryRequest buildEntry(String subjectCode, int debitCredit) {
        RuleEntryRequest entry = new RuleEntryRequest();
        entry.setRowNum(1);
        entry.setFundsType("PRINCIPAL");
        entry.setSubjectCode(subjectCode);
        entry.setAccountScope(1);
        entry.setDebitCredit(debitCredit);
        entry.setCurrency("CNY");
        entry.setIsUnilateral(false);
        entry.setExtendScript("");
        entry.setSummary("测试");
        entry.setAuxiliaries(List.of());
        return entry;
    }

    private RuleCreateRequest buildRequestWithEntries(List<RuleEntryRequest> entries) {
        RuleCreateRequest request = new RuleCreateRequest();
        request.setRuleName("测试规则");
        request.setVoucherType("PAYMENT");
        request.setBusinessCode("PAYMENT");
        request.setTradingCode("CASH_PAY");
        request.setPayChannel("CASH");
        request.setIsOpenAccount(false);
        request.setFreezeDuration(0);
        request.setPreRuleId(0L);
        request.setStatus(1);
        request.setEntries(entries);
        return request;
    }
}
