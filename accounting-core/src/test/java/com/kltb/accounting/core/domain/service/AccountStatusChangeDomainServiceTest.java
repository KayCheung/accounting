package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AccountStatusChangeDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class AccountStatusChangeDomainServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubAccountRepository subAccountRepository;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AccountStatusChangeDomainService accountStatusChangeDomainService;

    // ==================== freezeAccount 测试 ====================

    @Test
    @DisplayName("冻结账户: 正常流程 NORMAL → FROZEN")
    void freezeAccount_normal_shouldSucceed() {
        AccountPO account = buildAccount(AccountStatusEnum.NORMAL);
        AccountPO frozenAccount = buildAccount(AccountStatusEnum.FROZEN);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> {
            // Execute the transaction callback
            doNothing().when(accountRepository).updateStatus(eq("ACC001"), eq(AccountStatusEnum.FROZEN), eq(0L));
            return frozenAccount;
        }).when(transactionTemplate).execute(any());

        AccountPO result = accountStatusChangeDomainService.freezeAccount("ACC001", "风控拦截");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.FROZEN);
        assertThat(result.getAccountNo()).isEqualTo("ACC001");
        verify(accountRepository).updateStatus("ACC001", AccountStatusEnum.FROZEN, 0L);
    }

    @Test
    @DisplayName("冻结账户: 已处于冻结状态 → 幂等返回")
    void freezeAccount_alreadyFrozen_shouldReturnIdempotent() {
        AccountPO account = buildAccount(AccountStatusEnum.FROZEN);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        AccountPO result = accountStatusChangeDomainService.freezeAccount("ACC001", "风控拦截");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.FROZEN);
        verify(distributedLockTemplate, never()).execute(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("冻结账户: 账户不存在 → 抛出 ACCOUNT_NOT_FOUND")
    void freezeAccount_notFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(null);

        assertThatThrownBy(() -> accountStatusChangeDomainService.freezeAccount("ACC001", "风控拦截"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("冻结账户: 已注销账户不允许冻结 → 抛出 ACCOUNT_STATUS_TRANSITION_INVALID")
    void freezeAccount_cancelled_shouldThrow() {
        AccountPO account = buildAccount(AccountStatusEnum.CANCELLED);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        assertThatThrownBy(() -> accountStatusChangeDomainService.freezeAccount("ACC001", "风控拦截"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID);
                });
    }

    @Test
    @DisplayName("冻结账户: 并发场景获取分布式锁失败 → 抛出 IDEMPOTENT_CONFLICT")
    void freezeAccount_concurrentLockFailure_shouldThrowServiceException() {
        AccountPO account = buildAccount(AccountStatusEnum.NORMAL);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        doThrow(new ServiceException(ResultCode.IDEMPOTENT_CONFLICT, "获取分布式锁失败"))
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());

        assertThatThrownBy(() -> accountStatusChangeDomainService.freezeAccount("ACC001", "风控拦截"))
                .isInstanceOf(ServiceException.class)
                .satisfies(ex -> {
                    ServiceException e = (ServiceException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.IDEMPOTENT_CONFLICT);
                });
    }

    // ==================== unfreezeAccount 测试 ====================

    @Test
    @DisplayName("解冻账户: 正常流程 FROZEN → NORMAL")
    void unfreezeAccount_normal_shouldSucceed() {
        AccountPO account = buildAccount(AccountStatusEnum.FROZEN);
        AccountPO normalAccount = buildAccount(AccountStatusEnum.NORMAL);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> {
            doNothing().when(accountRepository).updateStatus(eq("ACC001"), eq(AccountStatusEnum.NORMAL), eq(0L));
            return normalAccount;
        }).when(transactionTemplate).execute(any());

        AccountPO result = accountStatusChangeDomainService.unfreezeAccount("ACC001");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.NORMAL);
        verify(accountRepository).updateStatus("ACC001", AccountStatusEnum.NORMAL, 0L);
    }

    @Test
    @DisplayName("解冻账户: 已处于正常状态 → 幂等返回")
    void unfreezeAccount_alreadyNormal_shouldReturnIdempotent() {
        AccountPO account = buildAccount(AccountStatusEnum.NORMAL);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        AccountPO result = accountStatusChangeDomainService.unfreezeAccount("ACC001");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.NORMAL);
        verify(distributedLockTemplate, never()).execute(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("解冻账户: 已注销账户不允许解冻 → 抛出 ACCOUNT_STATUS_TRANSITION_INVALID")
    void unfreezeAccount_cancelled_shouldThrow() {
        AccountPO account = buildAccount(AccountStatusEnum.CANCELLED);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        assertThatThrownBy(() -> accountStatusChangeDomainService.unfreezeAccount("ACC001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID);
                });
    }

    // ==================== cancelAccount 测试 ====================

    @Test
    @DisplayName("注销账户: 正常流程 NORMAL → CANCELLED (余额为零)")
    void cancelAccount_normal_zeroBalance_shouldSucceed() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.NORMAL, BigDecimal.ZERO);
        AccountPO cancelledAccount = buildCancelledAccount();

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNo("ACC001")).thenReturn(Collections.emptyList());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> {
            when(accountRepository.updateById(any(AccountPO.class))).thenReturn(true);
            return cancelledAccount;
        }).when(transactionTemplate).execute(any());

        AccountPO result = accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.CANCELLED);
        assertThat(result.getInactiveDate()).isNotNull();
    }

    @Test
    @DisplayName("注销账户: 冻结账户可注销 FROZEN → CANCELLED")
    void cancelAccount_frozen_zeroBalance_shouldSucceed() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.FROZEN, BigDecimal.ZERO);
        AccountPO cancelledAccount = buildCancelledAccount();

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNo("ACC001")).thenReturn(Collections.emptyList());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> {
            when(accountRepository.updateById(any(AccountPO.class))).thenReturn(true);
            return cancelledAccount;
        }).when(transactionTemplate).execute(any());

        AccountPO result = accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.CANCELLED);
    }

    @Test
    @DisplayName("注销账户: 已注销账户 → 幂等返回")
    void cancelAccount_alreadyCancelled_shouldReturnIdempotent() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.CANCELLED, BigDecimal.ZERO);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        AccountPO result = accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户");

        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.CANCELLED);
        verify(distributedLockTemplate, never()).execute(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("注销账户: 主账户余额不为零 → 抛出 ACCOUNT_BALANCE_NOT_ZERO")
    void cancelAccount_nonZeroBalance_shouldThrow() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.NORMAL, new BigDecimal("100.00"));

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        assertThatThrownBy(() -> accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_BALANCE_NOT_ZERO);
                });
    }

    @Test
    @DisplayName("注销账户: 子账户可用余额不为零 → 抛出 ACCOUNT_BALANCE_NOT_ZERO")
    void cancelAccount_subAccountNonZeroBalance_shouldThrow() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.NORMAL, BigDecimal.ZERO);
        SubAccountPO subAccount = buildSubAccount(BalanceTypeEnum.AVAILABLE, new BigDecimal("50.00"));

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNo("ACC001")).thenReturn(List.of(subAccount));

        assertThatThrownBy(() -> accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_BALANCE_NOT_ZERO);
                });
    }

    @Test
    @DisplayName("注销账户: 子账户冻结余额不为零 → 抛出 ACCOUNT_BALANCE_NOT_ZERO")
    void cancelAccount_subAccountFrozenNonZeroBalance_shouldThrow() {
        AccountPO account = buildAccountWithBalance(AccountStatusEnum.NORMAL, BigDecimal.ZERO);
        SubAccountPO subAccount = buildSubAccount(BalanceTypeEnum.FROZEN, new BigDecimal("25.00"));

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNo("ACC001")).thenReturn(List.of(subAccount));

        assertThatThrownBy(() -> accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_BALANCE_NOT_ZERO);
                });
    }

    @Test
    @DisplayName("注销账户: 账户不存在 → 抛出 ACCOUNT_NOT_FOUND")
    void cancelAccount_notFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(null);

        assertThatThrownBy(() -> accountStatusChangeDomainService.cancelAccount("ACC001", "客户申请销户"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    // ==================== changeRiskStatus 测试 ====================

    @Test
    @DisplayName("变更风控状态: 正常流程")
    void changeRiskStatus_normal_shouldSucceed() {
        AccountPO account = buildAccount(AccountStatusEnum.NORMAL);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        AccountPO updatedAccount = buildAccount(AccountStatusEnum.NORMAL);
        updatedAccount.setRiskStatus(RiskStatusEnum.NO_IN);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);
        doAnswer(invocation -> {
            doNothing().when(accountRepository).updateRiskStatus(eq("ACC001"), eq(RiskStatusEnum.NO_IN), eq(0L));
            return updatedAccount;
        }).when(transactionTemplate).execute(any());

        AccountPO result = accountStatusChangeDomainService.changeRiskStatus("ACC001", RiskStatusEnum.NO_IN);

        assertThat(result.getRiskStatus()).isEqualTo(RiskStatusEnum.NO_IN);
        verify(accountRepository).updateRiskStatus("ACC001", RiskStatusEnum.NO_IN, 0L);
    }

    @Test
    @DisplayName("变更风控状态: 账户不存在 → 抛出 ACCOUNT_NOT_FOUND")
    void changeRiskStatus_notFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(null);

        assertThatThrownBy(() -> accountStatusChangeDomainService.changeRiskStatus("ACC001", RiskStatusEnum.NO_IN))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    // ==================== queryAccountStatus 测试 ====================

    @Test
    @DisplayName("查询账户状态: 正常流程")
    void queryAccountStatus_normal_shouldReturnAccount() {
        AccountPO account = buildAccount(AccountStatusEnum.NORMAL);

        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        AccountPO result = accountStatusChangeDomainService.queryAccountStatus("ACC001");

        assertThat(result).isNotNull();
        assertThat(result.getAccountNo()).isEqualTo("ACC001");
        assertThat(result.getStatus()).isEqualTo(AccountStatusEnum.NORMAL);
    }

    @Test
    @DisplayName("查询账户状态: 账户不存在 → 抛出 ACCOUNT_NOT_FOUND")
    void queryAccountStatus_notFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(null);

        assertThatThrownBy(() -> accountStatusChangeDomainService.queryAccountStatus("ACC001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    // ==================== 状态机校验测试 ====================

    @Test
    @DisplayName("状态机: CANCELLED → NORMAL 非法转换")
    void validateTransition_cancelledToNormal_shouldThrow() {
        AccountPO account = buildAccount(AccountStatusEnum.CANCELLED);
        when(accountRepository.selectByAccountNo("ACC001")).thenReturn(account);

        assertThatThrownBy(() -> accountStatusChangeDomainService.unfreezeAccount("ACC001"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_STATUS_TRANSITION_INVALID);
                });
    }

    // ==================== 辅助方法 ====================

    private AccountPO buildAccount(AccountStatusEnum status) {
        AccountPO account = new AccountPO();
        account.setId(1L);
        account.setAccountNo("ACC001");
        account.setAccountName("测试账户");
        account.setSubjectCode("1001");
        account.setOwnerId("CUST001");
        account.setStatus(status);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setBalance(BigDecimal.ZERO);
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setOpenDate(LocalDate.now());
        account.setInactiveDate(LocalDate.of(1970, 1, 1));
        account.setVersion(0L);
        return account;
    }

    private AccountPO buildAccountWithBalance(AccountStatusEnum status, BigDecimal balance) {
        AccountPO account = new AccountPO();
        account.setId(1L);
        account.setAccountNo("ACC001");
        account.setAccountName("测试账户");
        account.setSubjectCode("1001");
        account.setOwnerId("CUST001");
        account.setStatus(status);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setBalance(balance);
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setOpenDate(LocalDate.now());
        account.setInactiveDate(LocalDate.of(1970, 1, 1));
        account.setVersion(0L);
        return account;
    }

    private AccountPO buildCancelledAccount() {
        AccountPO account = new AccountPO();
        account.setId(1L);
        account.setAccountNo("ACC001");
        account.setAccountName("测试账户");
        account.setSubjectCode("1001");
        account.setOwnerId("CUST001");
        account.setStatus(AccountStatusEnum.CANCELLED);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setBalance(BigDecimal.ZERO);
        account.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        account.setOpeningBalance(BigDecimal.ZERO);
        account.setOpenDate(LocalDate.now());
        account.setInactiveDate(LocalDate.now());
        account.setVersion(1L);
        return account;
    }

    private SubAccountPO buildSubAccount(BalanceTypeEnum balanceType, BigDecimal balance) {
        SubAccountPO sub = new SubAccountPO();
        sub.setId(1L);
        sub.setAccountNo("ACC001");
        sub.setBalanceType(balanceType);
        sub.setBalance(balance);
        sub.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        sub.setVersion(0L);
        return sub;
    }
}
