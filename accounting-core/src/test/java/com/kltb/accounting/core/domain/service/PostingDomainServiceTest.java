package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.infrastructure.account.AccountBalanceCalculator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingDomainServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SubAccountRepository subAccountRepository;
    @Mock private AccountDetailRepository accountDetailRepository;
    @Mock private SubAccountDetailRepository subAccountDetailRepository;
    @Mock private AccountingVoucherRepository accountingVoucherRepository;

    @InjectMocks private PostingDomainService postingDomainService;

    @Test
    @DisplayName("实时过账: 空列表直接返回")
    void executeRealTimePosting_emptyList_shouldReturn() {
        postingDomainService.executeRealTimePosting(List.of(), LocalDate.now());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("实时过账: 账户不存在 -> 抛出 ACCOUNT_NOT_FOUND")
    void executeRealTimePosting_accountNotFound_shouldThrow() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(List.of(entry), LocalDate.now()))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("实时过账: 账户冻结 -> 抛出 ACCOUNT_FROZEN")
    void executeRealTimePosting_accountFrozen_shouldThrow() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.FROZEN);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(buildSubAccount("A001")));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(List.of(entry), LocalDate.now()))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN);
                });
    }

    @Test
    @DisplayName("实时过账: 账户注销 -> 抛出 ACCOUNT_CANCELLED")
    void executeRealTimePosting_accountCancelled_shouldThrow() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.CANCELLED);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(buildSubAccount("A001")));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(List.of(entry), LocalDate.now()))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_CANCELLED);
                });
    }

    @Test
    @DisplayName("实时过账: 正常过账 -> 更新余额+写入明细+更新状态")
    void executeRealTimePosting_normalFlow_shouldUpdateBalanceAndDetails() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.NORMAL);
        SubAccountPO subAccount = buildSubAccount("A001");
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(subAccount));
        postingDomainService.executeRealTimePosting(List.of(entry), LocalDate.of(2026, 6, 24));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("6000"));
        assertThat(subAccount.getBalance()).isEqualTo(new BigDecimal("6000"));
        verify(accountRepository).updateById(account);
        verify(subAccountRepository).updateById(subAccount);
        verify(accountDetailRepository).batchInsert(anyList());
        verify(subAccountDetailRepository).batchInsert(anyList());
        assertThat(entry.getStatus()).isEqualTo(VoucherEntryStatusEnum.POSTED);
    }

    @Test
    @DisplayName("实时过账: 余额不足 -> 抛出 INSUFFICIENT_BALANCE")
    void executeRealTimePosting_insufficientBalance_shouldThrow() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 2, new BigDecimal("10000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.NORMAL);
        SubAccountPO subAccount = buildSubAccount("A001");
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(subAccount));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(List.of(entry), LocalDate.now()))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE);
                });
    }

    private AccountingVoucherEntryPO buildEntry(String entryId, String voucherNo, String accountNo, int direction, BigDecimal amount) {
        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId(entryId);
        entry.setVoucherNo(voucherNo);
        entry.setAccountNo(accountNo);
        entry.setSubjectCode("1001");
        entry.setDebitCredit(direction == 1 ? DebitCreditEnum.DEBIT : DebitCreditEnum.CREDIT);
        entry.setAmount(amount);
        entry.setChangeDirection(direction);
        entry.setCurrency("CNY");
        entry.setSummary("test");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);
        return entry;
    }

    private AccountPO buildAccount(String accountNo, BigDecimal balance, AccountStatusEnum status) {
        AccountPO account = new AccountPO();
        account.setAccountNo(accountNo);
        account.setSubjectCode("1001");
        account.setBalance(balance);
        account.setStatus(status);
        account.setVersion(0L);
        account.setCurrency("CNY");
        return account;
    }

    private SubAccountPO buildSubAccount(String accountNo) {
        SubAccountPO sub = new SubAccountPO();
        sub.setAccountNo(accountNo);
        sub.setBalance(new BigDecimal("5000"));
        sub.setVersion(0L);
        return sub;
    }
}
