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
    @DisplayName("停用规则: 存在关联凭证报 OPERATION_NOT_ALLOWED")
    void disable_hasVouchers_shouldThrow() {
        AccountingRulePO rule = new AccountingRulePO()
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setStatus(RuleStatusEnum.ENABLED);
        when(accountingRuleRepository.selectRuleById(1L)).thenReturn(rule);
        when(accountingVoucherRepository.countByBusinessKey("PAYMENT", "CASH_PAY", "CASH")).thenReturn(3L);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> ruleApplicationService.disable(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("关联凭证");
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
