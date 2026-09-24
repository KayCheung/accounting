package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.TemplateCreateRequest;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TemplateApplicationService 单元测试
 *
 * 测试覆盖：科目联动校验、唯一键校验、停用联动校验
 */
@ExtendWith(MockitoExtension.class)
class TemplateApplicationServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private TemplateApplicationService templateApplicationService;

    @Test
    @DisplayName("创建模板: 科目不存在报 SUBJECT_NOT_FOUND")
    void create_subjectNotFound_shouldThrow() {
        when(subjectRepository.selectByCode("999999")).thenReturn(null);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        TemplateCreateRequest request = new TemplateCreateRequest()
                .setTemplateName("测试模板")
                .setBusinessCode("PAYMENT")
                .setCustomerType(1)
                .setAutoOpen(true)
                .setSubjectCode("999999")
                .setAccountType("CASH")
                .setCurrency("CNY")
                .setBalanceDirection(1)
                .setAcctNoRule("RULE")
                .setAcctNameRule("RULE")
                .setStatus(1);

        assertThatThrownBy(() -> templateApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("科目不存在");
    }

    @Test
    @DisplayName("创建模板: 科目非末级报 OPERATION_NOT_ALLOWED")
    void create_subjectNotLeaf_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO()
                .setSubjectCode("101")
                .setLeaf(false)
                .setAllowOpenAccount(true);
        when(subjectRepository.selectByCode("101")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        TemplateCreateRequest request = new TemplateCreateRequest()
                .setTemplateName("测试模板")
                .setBusinessCode("PAYMENT")
                .setCustomerType(1)
                .setAutoOpen(true)
                .setSubjectCode("101")
                .setAccountType("CASH")
                .setCurrency("CNY")
                .setBalanceDirection(1)
                .setAcctNoRule("RULE")
                .setAcctNameRule("RULE")
                .setStatus(1);

        assertThatThrownBy(() -> templateApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("末级科目");
    }

    @Test
    @DisplayName("创建模板: 科目不允许开户报 OPERATION_NOT_ALLOWED")
    void create_subjectNotAllowed_shouldThrow() {
        AccountSubjectPO subject = new AccountSubjectPO()
                .setSubjectCode("101001")
                .setLeaf(true)
                .setAllowOpenAccount(false);
        when(subjectRepository.selectByCode("101001")).thenReturn(subject);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        TemplateCreateRequest request = new TemplateCreateRequest()
                .setTemplateName("测试模板")
                .setBusinessCode("PAYMENT")
                .setCustomerType(1)
                .setAutoOpen(true)
                .setSubjectCode("101001")
                .setAccountType("CASH")
                .setCurrency("CNY")
                .setBalanceDirection(1)
                .setAcctNoRule("RULE")
                .setAcctNameRule("RULE")
                .setStatus(1);

        assertThatThrownBy(() -> templateApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("允许开户");
    }

    @Test
    @DisplayName("停用模板: 存在关联账户报 OPERATION_NOT_ALLOWED")
    void disable_hasActiveAccounts_shouldThrow() {
        AccountTemplatePO template = new AccountTemplatePO()
                .setSubjectCode("101001")
                .setCustomerType(com.kltb.accounting.core.domain.enums.CustomerTypeEnum.PERSONAL);
        when(subjectRepository.selectTemplateById(1L)).thenReturn(template);
        when(accountMapper.countBySubjectCodeAndOwnerType("101001", 1)).thenReturn(5L);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> templateApplicationService.disable(1L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已关联账户");
    }
}
