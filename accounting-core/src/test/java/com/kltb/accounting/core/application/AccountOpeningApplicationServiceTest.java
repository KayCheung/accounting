package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.AccountOpenRequest;
import com.kltb.accounting.api.request.InternalAccountOpenRequest;
import com.kltb.accounting.api.response.AccountOpenResponse;
import com.kltb.accounting.api.response.BatchOpenResultResponse;
import com.kltb.accounting.core.application.assembler.AccountOpeningAssembler;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.OwnerTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.domain.model.BatchOpenResult;
import com.kltb.accounting.core.domain.service.AccountOpeningDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AccountOpeningApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AccountOpeningApplicationServiceTest {

    @Mock
    private AccountOpeningDomainService accountOpeningDomainService;

    @Mock
    private AccountOpeningAssembler accountOpeningAssembler;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountOpeningApplicationService accountOpeningApplicationService;

    // ==================== openExternalAccount 测试 ====================

    @Test
    @DisplayName("外部开户: 正常流程 — 返回 AccountOpenResponse 含 accountNo")
    void openExternalAccount_normalFlow_shouldReturnResponseWithAccountNo() {
        AccountOpenRequest request = new AccountOpenRequest()
                .setBusinessCode("PAYMENT")
                .setCustomerId("CUST001")
                .setCustomerType(2)
                .setSubjectCode("1001")
                .setRequestNo("REQ001");

        AccountPO account = buildAccount("0012026030100001", "CUST001");
        AccountOpenResponse response = new AccountOpenResponse()
                .setAccountNo("0012026030100001")
                .setAccountName("CUST001")
                .setSubjectCode("1001")
                .setOwnerId("CUST001")
                .setStatus(AccountStatusEnum.NORMAL.getCode())
                .setOpenDate(LocalDate.now());

        when(accountOpeningDomainService.openExternalAccount(
                "PAYMENT", "CUST001", null, CustomerTypeEnum.ENTERPRISE, "1001", "REQ001"))
                .thenReturn(account);
        when(accountOpeningAssembler.toResponse(account)).thenReturn(response);

        AccountOpenResponse result = accountOpeningApplicationService.openExternalAccount(request);

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("0012026030100001");
        assertThat(result.getOwnerId()).isEqualTo("CUST001");
        assertThat(result.getSubjectCode()).isEqualTo("1001");

        verify(accountOpeningDomainService).openExternalAccount(
                "PAYMENT", "CUST001", null, CustomerTypeEnum.ENTERPRISE, "1001", "REQ001");
        verify(accountOpeningAssembler).toResponse(account);
    }

    @Test
    @DisplayName("外部开户: 领域服务抛出 AccountException，向上透传")
    void openExternalAccount_domainServiceThrows_shouldPropagate() {
        AccountOpenRequest request = new AccountOpenRequest()
                .setBusinessCode("PAYMENT")
                .setCustomerId("CUST001")
                .setCustomerType(2)
                .setSubjectCode("1001")
                .setRequestNo("REQ001");

        when(accountOpeningDomainService.openExternalAccount(
                anyString(), anyString(), any(), any(), anyString(), anyString()))
                .thenThrow(new AccountException(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND, "开户模板不存在"));

        assertThatThrownBy(() -> accountOpeningApplicationService.openExternalAccount(request))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_TEMPLATE_NOT_FOUND);
                });
    }

    // ==================== openInternalAccount 测试 ====================

    @Test
    @DisplayName("内部开户: 正常流程 — 返回 AccountOpenResponse")
    void openInternalAccount_normalFlow_shouldReturnResponse() {
        InternalAccountOpenRequest request = new InternalAccountOpenRequest()
                .setSubjectCode("1001");

        AccountPO account = buildAccount("INNER1001001", "1001");
        AccountOpenResponse response = new AccountOpenResponse()
                .setAccountNo("INNER1001001")
                .setAccountName("1001")
                .setSubjectCode("1001")
                .setOwnerId("INNER")
                .setStatus(AccountStatusEnum.NORMAL.getCode())
                .setOpenDate(LocalDate.now());

        when(accountOpeningDomainService.openInternalAccount("1001"))
                .thenReturn(account);
        when(accountOpeningAssembler.toResponse(account)).thenReturn(response);

        AccountOpenResponse result = accountOpeningApplicationService.openInternalAccount(request);

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("INNER1001001");
        assertThat(result.getOwnerId()).isEqualTo("INNER");

        verify(accountOpeningDomainService).openInternalAccount("1001");
        verify(accountOpeningAssembler).toResponse(account);
    }

    // ==================== batchOpenInternalAccounts 测试 ====================

    @Test
    @DisplayName("批量扫描开户: 返回 BatchOpenResultResponse 含统计数据")
    void batchOpenInternalAccounts_shouldReturnBatchResponseWithStats() {
        BatchOpenResult result = new BatchOpenResult();
        result.setTotalCount(5);
        result.setNewlyCreated(3);
        result.setAlreadyExists(1);
        result.setFailed(1);
        result.addFailedReason("科目 1005: 科目不存在: 1005");

        BatchOpenResultResponse response = new BatchOpenResultResponse();
        response.setTotalCount(5);
        response.setNewlyCreated(3);
        response.setAlreadyExists(1);
        response.setFailed(1);
        response.setFailedReasons(result.getFailedReasons());

        when(accountOpeningDomainService.scanAndOpenInternalAccounts())
                .thenReturn(result);
        when(accountOpeningAssembler.toBatchResponse(result)).thenReturn(response);

        BatchOpenResultResponse actual = accountOpeningApplicationService.batchOpenInternalAccounts();

        assertThat(actual).isNotNull();
        assertThat(actual.getTotalCount()).isEqualTo(5);
        assertThat(actual.getNewlyCreated()).isEqualTo(3);
        assertThat(actual.getAlreadyExists()).isEqualTo(1);
        assertThat(actual.getFailed()).isEqualTo(1);
        assertThat(actual.getFailedReasons()).hasSize(1);

        verify(accountOpeningDomainService).scanAndOpenInternalAccounts();
        verify(accountOpeningAssembler).toBatchResponse(result);
    }

    // ==================== getAccountInfo 测试 ====================

    @Test
    @DisplayName("查询账户信息: 正常流程 — 返回 AccountOpenResponse")
    void getAccountInfo_normalFlow_shouldReturnResponse() {
        AccountPO account = buildAccount("0012026030100001", "CUST001");
        AccountOpenResponse response = new AccountOpenResponse()
                .setAccountNo("0012026030100001")
                .setAccountName("CUST001")
                .setSubjectCode("1001")
                .setOwnerId("CUST001")
                .setStatus(AccountStatusEnum.NORMAL.getCode())
                .setOpenDate(LocalDate.now());

        when(accountRepository.selectByAccountNo("0012026030100001"))
                .thenReturn(account);
        when(accountOpeningAssembler.toResponse(account)).thenReturn(response);

        AccountOpenResponse result = accountOpeningApplicationService.getAccountInfo("0012026030100001");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("0012026030100001");

        verify(accountRepository).selectByAccountNo("0012026030100001");
        verify(accountOpeningAssembler).toResponse(account);
    }

    @Test
    @DisplayName("查询账户信息: 账户不存在 → 抛出 ACCOUNT_NOT_FOUND")
    void getAccountInfo_accountNotFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("NOT_EXIST"))
                .thenReturn(null);

        assertThatThrownBy(() -> accountOpeningApplicationService.getAccountInfo("NOT_EXIST"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                    assertThat(e.getMessage()).contains("NOT_EXIST");
                });

        verify(accountRepository).selectByAccountNo("NOT_EXIST");
        verifyNoInteractions(accountOpeningAssembler);
    }

    // ==================== 辅助方法 ====================

    private AccountPO buildAccount(String accountNo, String ownerId) {
        AccountPO account = new AccountPO();
        account.setId(1L);
        account.setAccountNo(accountNo);
        account.setAccountName(ownerId);
        account.setOwnerId(ownerId);
        account.setSubjectCode("1001");
        account.setOwnerType(OwnerTypeEnum.ENTERPRISE);
        account.setAccountType("CASH");
        account.setCurrency("CNY");
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setBalance(BigDecimal.ZERO);
        account.setStatus(AccountStatusEnum.NORMAL);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setRequestNo("REQ001");
        account.setOpenDate(LocalDate.now());
        account.setVersion(0L);
        return account;
    }
}
