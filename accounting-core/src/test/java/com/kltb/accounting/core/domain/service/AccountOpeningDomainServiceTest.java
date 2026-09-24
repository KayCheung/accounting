package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import com.kltb.accounting.core.domain.model.BatchOpenResult;
import com.kltb.accounting.core.infrastructure.account.AccountNoGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.*;

/**
 * AccountOpeningDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AccountOpeningDomainServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubAccountRepository subAccountRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AccountNoGenerator accountNoGenerator;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AccountOpeningDomainService accountOpeningDomainService;

    // ==================== openExternalAccount 测试 ====================

    @Test
    @DisplayName("外部开户: 正常流程 — 模板匹配成功，科目校验通过，创建主账户+2个子账户")
    void openExternalAccount_normalFlow_shouldCreateAccountWithSubAccounts() {
        AccountTemplatePO template = buildTemplate();
        AccountSubjectPO subject = buildSubject();
        AccountPO account = buildAccount();

        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001"))
                .thenReturn(false);
        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        when(accountNoGenerator.generateExternalAccountNo())
                .thenReturn("0012026030100001");
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        AccountPO result = accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("0012026030100001");
        assertThat(result.getOwnerId()).isEqualTo("CUST001");
        assertThat(result.getSubjectCode()).isEqualTo("1001");

        verify(accountRepository).insert(any(AccountPO.class));
        verify(subAccountRepository, times(2)).insert(any(SubAccountPO.class));
    }

    @Test
    @DisplayName("外部开户: 模板不存在 → 抛出 ACCOUNT_TEMPLATE_NOT_FOUND")
    void openExternalAccount_templateNotFound_shouldThrow() {
        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(null);

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("外部开户: 模板未启用(status != ENABLED) → 抛出 ACCOUNT_TEMPLATE_NOT_ENABLED")
    void openExternalAccount_templateNotEnabled_shouldThrow() {
        AccountTemplatePO template = buildTemplate().setStatus(TemplateStatusEnum.DISABLED);
        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_TEMPLATE_NOT_ENABLED);
                });
    }

    @Test
    @DisplayName("外部开户: 模板 auto_open=false → 抛出 ACCOUNT_TEMPLATE_NOT_AUTO_OPEN")
    void openExternalAccount_templateNotAutoOpen_shouldThrow() {
        AccountTemplatePO template = buildTemplate().setAutoOpen(false);
        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_TEMPLATE_NOT_AUTO_OPEN);
                });
    }

    @Test
    @DisplayName("外部开户: 科目非末级 → 抛出 SUBJECT_NOT_LEAF")
    void openExternalAccount_subjectNotLeaf_shouldThrow() {
        AccountTemplatePO template = buildTemplate();
        AccountSubjectPO subject = buildSubject().setLeaf(false);

        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001"))
                .thenReturn(false);
        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.SUBJECT_NOT_LEAF);
                });
    }

    @Test
    @DisplayName("外部开户: 科目不允许开户 → 抛出 SUBJECT_NOT_ALLOW_OPEN_ACCOUNT")
    void openExternalAccount_subjectNotAllowed_shouldThrow() {
        AccountTemplatePO template = buildTemplate();
        AccountSubjectPO subject = buildSubject().setAllowOpenAccount(false);

        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001"))
                .thenReturn(false);
        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.SUBJECT_NOT_ALLOW_OPEN_ACCOUNT);
                });
    }

    @Test
    @DisplayName("外部开户: 账户已存在 → 抛出 ACCOUNT_ALREADY_EXISTS")
    void openExternalAccount_accountAlreadyExists_shouldThrow() {
        AccountTemplatePO template = buildTemplate();

        when(subjectRepository.selectTemplateByBusinessKey("PAYMENT", 2, "1001"))
                .thenReturn(template);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001"))
                .thenReturn(true);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_ALREADY_EXISTS);
                });
    }

    @Test
    @DisplayName("外部开户: subjectCode=null 应匹配首个启用模板")
    void openExternalAccount_nullSubjectCode_shouldMatchFirstEnabledTemplate() {
        AccountTemplatePO template = buildTemplate();
        AccountSubjectPO subject = buildSubject();

        // subjectCode=null 走 selectFirstEnabledTemplate 分支
        when(subjectRepository.selectFirstEnabledTemplate("PAYMENT", 2))
                .thenReturn(template);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001"))
                .thenReturn(false);
        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        when(accountNoGenerator.generateExternalAccountNo())
                .thenReturn("0012026030100002");
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        AccountPO result = accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", CustomerTypeEnum.ENTERPRISE, null, "REQ002");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("0012026030100002");
        assertThat(result.getOwnerId()).isEqualTo("CUST001");
        assertThat(result.getSubjectCode()).isEqualTo("1001");

        verify(subjectRepository, never()).selectTemplateByBusinessKey(anyString(), anyInt(), anyString());
        verify(subjectRepository).selectFirstEnabledTemplate("PAYMENT", 2);
    }

    @Test
    @DisplayName("外部开户: 单模板配置多科目账户 — 一次性开出全部关联科目账户")
    void openExternalAccounts_multipleTemplates_shouldOpenAllAccounts() {
        AccountTemplatePO t1 = buildTemplate();
        AccountTemplatePO t2 = buildTemplate().setSubjectCode("1002").setAccountType("SETTLE");
        AccountSubjectPO s1 = buildSubject().setSubjectCode("1001").setSubjectName("库存现金");
        AccountSubjectPO s2 = buildSubject().setSubjectCode("1002").setSubjectName("结算账户");

        when(subjectRepository.selectEnabledTemplates("PAYMENT", 2))
                .thenReturn(List.of(t1, t2));
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1001")).thenReturn(false);
        when(accountRepository.existsByOwnerIdAndSubjectCode("CUST001", "1002")).thenReturn(false);
        when(subjectRepository.selectByCode("1001")).thenReturn(s1);
        when(subjectRepository.selectByCode("1002")).thenReturn(s2);
        when(accountNoGenerator.generateExternalAccountNo()).thenReturn("0012026030100001", "0012026030100002");
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        List<AccountPO> result = accountOpeningDomainService.openExternalAccounts(
                "PAYMENT", "CUST001", "张三", CustomerTypeEnum.ENTERPRISE, null, "REQ001");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSubjectCode()).isEqualTo("1001");
        assertThat(result.get(1).getSubjectCode()).isEqualTo("1002");
        verify(accountRepository, times(2)).insert(any(AccountPO.class));
        verify(subAccountRepository, times(4)).insert(any(SubAccountPO.class));
    }

    // ==================== openInternalAccount 测试 ====================

    @Test
    @DisplayName("内部开户: 正常流程 — 科目校验通过，创建主账户+2个子账户")
    void openInternalAccount_normalFlow_shouldCreateAccountWithSubAccounts() {
        AccountSubjectPO subject = buildSubject();
        AccountPO account = buildAccount();

        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        when(accountRepository.existsByOwnerIdAndSubjectCode("INNER", "1001"))
                .thenReturn(false);
        when(accountNoGenerator.generateInternalAccountNo("1001"))
                .thenReturn("INNER1001001");
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        AccountPO result = accountOpeningDomainService.openInternalAccount("1001");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("INNER1001001");
        assertThat(result.getOwnerId()).isEqualTo("INNER");
        assertThat(result.getSubjectCode()).isEqualTo("1001");
        assertThat(result.getAccountName()).isEqualTo("库存现金-内部账户");

        verify(accountRepository).insert(any(AccountPO.class));
        verify(subAccountRepository, times(2)).insert(any(SubAccountPO.class));
    }

    @Test
    @DisplayName("内部开户: 账户已存在 → 抛出 ACCOUNT_ALREADY_EXISTS")
    void openInternalAccount_accountAlreadyExists_shouldThrow() {
        AccountSubjectPO subject = buildSubject();

        when(subjectRepository.selectByCode("1001"))
                .thenReturn(subject);
        when(accountRepository.existsByOwnerIdAndSubjectCode("INNER", "1001"))
                .thenReturn(true);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> accountOpeningDomainService.openInternalAccount("1001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_ALREADY_EXISTS);
                });
    }

    // ==================== scanAndOpenInternalAccounts 测试 ====================

    @Test
    @DisplayName("批量扫描开户: 混合结果 — 部分新建、部分已存在、部分失败")
    void scanAndOpenInternalAccounts_mixedResults_shouldReturnCorrectStats() {
        AccountSubjectPO subject1 = buildSubject().setSubjectCode("1001");
        AccountSubjectPO subject2 = buildSubject().setSubjectCode("1002");
        AccountSubjectPO subject3 = buildSubject().setSubjectCode("1003");

        when(subjectRepository.selectAllowOpenAccountLeafSubjects())
                .thenReturn(List.of(subject1, subject2, subject3));

        // 全局 stub: subjectRepository.selectByCode 对所有科目返回有效 subject
        lenient().when(subjectRepository.selectByCode("1001")).thenReturn(subject1);
        lenient().when(subjectRepository.selectByCode("1002")).thenReturn(subject2);
        lenient().when(subjectRepository.selectByCode("1003")).thenReturn(subject3);

        // subject1: 正常创建 (不存在)
        lenient().when(accountRepository.existsByOwnerIdAndSubjectCode("INNER", "1001")).thenReturn(false);
        lenient().when(accountNoGenerator.generateInternalAccountNo("1001")).thenReturn("INNER1001001");

        // subject2: 已存在
        lenient().when(accountRepository.existsByOwnerIdAndSubjectCode("INNER", "1002")).thenReturn(true);

        // subject3: 科目不存在 (模拟失败场景 — 重写为 null)
        lenient().when(subjectRepository.selectByCode("1003")).thenReturn(null);

        // 全局 stub: distributedLockTemplate 和 transactionTemplate
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        lenient().doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());

        BatchOpenResult result = accountOpeningDomainService.scanAndOpenInternalAccounts();

        assertThat(result.getTotalCount()).isEqualTo(3);
        assertThat(result.getNewlyCreated()).isEqualTo(1);
        assertThat(result.getAlreadyExists()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getFailedReasons()).hasSize(1);
        assertThat(result.getFailedReasons().get(0)).contains("1003");
    }

    // ==================== 辅助方法 ====================

    private AccountTemplatePO buildTemplate() {
        AccountTemplatePO template = new AccountTemplatePO();
        template.setId(1L);
        template.setTemplateName("测试模板");
        template.setBusinessCode("PAYMENT");
        template.setCustomerType(CustomerTypeEnum.ENTERPRISE);
        template.setAutoOpen(true);
        template.setStatus(TemplateStatusEnum.ENABLED);
        template.setSubjectCode("1001");
        template.setAccountType("CASH");
        template.setCurrency("CNY");
        template.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        template.setAcctNoRule("RULE");
        template.setAcctNameRule("RULE");
        return template;
    }

    private AccountSubjectPO buildSubject() {
        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setId(1L);
        subject.setSubjectCode("1001");
        subject.setSubjectName("库存现金");
        subject.setSubjectLevel(3);
        subject.setParentSubjectId(0L);
        subject.setLeaf(true);
        subject.setAllowPost(true);
        subject.setAllowOpenAccount(true);
        return subject;
    }

    private AccountPO buildAccount() {
        AccountPO account = new AccountPO();
        account.setId(1L);
        account.setAccountNo("0012026030100001");
        account.setAccountName("CUST001");
        account.setOwnerId("CUST001");
        account.setSubjectCode("1001");
        account.setAccountType("CASH");
        account.setCurrency("CNY");
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setBalance(BigDecimal.ZERO);
        account.setOpenDate(LocalDate.now());
        account.setVersion(0L);
        return account;
    }
}
