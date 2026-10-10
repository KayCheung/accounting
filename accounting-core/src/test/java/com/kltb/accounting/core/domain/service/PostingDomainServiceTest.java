package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PostingDomainServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SubAccountRepository subAccountRepository;
    @Mock private AccountDetailRepository accountDetailRepository;
    @Mock private SubAccountDetailRepository subAccountDetailRepository;
    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @Mock private com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository businessRecordRepository;
    @Mock private com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository freezeDetailRepository;
    @Mock private com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository accountingRuleRepository;
    @Mock private com.kltb.accounting.core.infrastructure.account.FreezeIdGenerator freezeIdGenerator;

    @InjectMocks private PostingDomainService postingDomainService;

    @Test
    @DisplayName("实时过账: 空列表直接返回")
    void executeRealTimePosting_emptyList_shouldReturn() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        postingDomainService.executeRealTimePosting(voucher, List.of());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("实时过账: 凭证为空 -> 抛出 PARAM_ERROR")
    void executeRealTimePosting_nullVoucher_shouldThrow() {
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(null, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("实时过账: 凭证txnNo缺失 -> 抛出 PARAM_ERROR")
    void executeRealTimePosting_missingTxnNo_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        voucher.setTxnNo("");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(voucher, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));
    }

    @Test
    @DisplayName("实时过账: 账户不存在 -> 抛出 ACCOUNT_NOT_FOUND")
    void executeRealTimePosting_accountNotFound_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(Collections.emptyList());
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(voucher, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("实时过账: 账户冻结 -> 抛出 ACCOUNT_FROZEN")
    void executeRealTimePosting_accountFrozen_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.FROZEN);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(buildSubAccount("A001")));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(voucher, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN);
                });
    }

    @Test
    @DisplayName("实时过账: 账户注销 -> 抛出 ACCOUNT_CANCELLED")
    void executeRealTimePosting_accountCancelled_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.CANCELLED);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(buildSubAccount("A001")));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(voucher, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_CANCELLED);
                });
    }

    @Test
    @DisplayName("实时过账: 正常过账 -> 更新余额+完整复制凭证元数据到明细+更新分录状态")
    void executeRealTimePosting_normalFlow_shouldUpdateBalanceAndDetails() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.NORMAL);
        SubAccountPO subAccount = buildSubAccount("A001");
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(subAccount));

        postingDomainService.executeRealTimePosting(voucher, List.of(entry));
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("6000"));
        assertThat(account.getVersion()).isEqualTo(0L); // 验证业务层未手动自增 version，交由乐观锁插件处理
        assertThat(subAccount.getBalance()).isEqualTo(new BigDecimal("6000"));
        assertThat(subAccount.getVersion()).isEqualTo(0L); // 验证业务层未手动自增 version，交由乐观锁插件处理
        verify(accountRepository).updateById(account);
        verify(subAccountRepository).updateById(subAccount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AccountDetailPO>> detailCaptor = ArgumentCaptor.forClass(List.class);
        verify(accountDetailRepository).batchInsert(detailCaptor.capture());
        List<AccountDetailPO> capturedDetails = detailCaptor.getValue();
        assertThat(capturedDetails).hasSize(1);
        AccountDetailPO detail = capturedDetails.get(0);
        assertThat(detail.getVoucherNo()).isEqualTo("VOU1");
        assertThat(detail.getEntryId()).isEqualTo("E1");
        assertThat(detail.getTxnNo()).isEqualTo(voucher.getTxnNo());
        assertThat(detail.getTraceNo()).isEqualTo(voucher.getTraceNo());
        assertThat(detail.getTraceSeq()).isEqualTo(voucher.getTraceSeq());
        assertThat(detail.getBusinessCode()).isEqualTo(voucher.getBusinessCode());
        assertThat(detail.getTradingCode()).isEqualTo(voucher.getTradingCode());
        assertThat(detail.getPayChannel()).isEqualTo(voucher.getPayChannel());
        assertThat(detail.getTradeType()).isEqualTo(voucher.getTradeType());
        assertThat(detail.getTradeTime()).isEqualTo(voucher.getTradeTime());
        assertThat(detail.getAccountingDate()).isEqualTo(voucher.getAccountingDate());
        assertThat(detail.getTenantId()).isEqualTo(voucher.getTenantId());
        assertThat(detail.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(detail.getPreBalance()).isEqualByComparingTo(new BigDecimal("5000"));
        assertThat(detail.getPostBalance()).isEqualByComparingTo(new BigDecimal("6000"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SubAccountDetailPO>> subDetailCaptor = ArgumentCaptor.forClass(List.class);
        verify(subAccountDetailRepository).batchInsert(subDetailCaptor.capture());
        List<SubAccountDetailPO> capturedSubDetails = subDetailCaptor.getValue();
        assertThat(capturedSubDetails).hasSize(1);
        SubAccountDetailPO subDetail = capturedSubDetails.get(0);
        assertThat(subDetail.getTxnNo()).isEqualTo(voucher.getTxnNo());
        assertThat(subDetail.getTraceNo()).isEqualTo(voucher.getTraceNo());
        assertThat(subDetail.getTraceSeq()).isEqualTo(voucher.getTraceSeq());
        assertThat(subDetail.getTradingCode()).isEqualTo(voucher.getTradingCode());
        assertThat(subDetail.getTradeType()).isEqualTo(voucher.getTradeType());
        assertThat(subDetail.getTradeTime()).isEqualTo(voucher.getTradeTime());
        assertThat(subDetail.getAccountingDate()).isEqualTo(voucher.getAccountingDate());
        assertThat(subDetail.getTenantId()).isEqualTo(voucher.getTenantId());

        assertThat(entry.getStatus()).isEqualTo(VoucherEntryStatusEnum.POSTED);
        verify(accountingVoucherRepository).updateEntryById(entry);
    }

    @Test
    @DisplayName("实时过账: 余额不足 -> 抛出 INSUFFICIENT_BALANCE")
    void executeRealTimePosting_insufficientBalance_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU1");
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU1", "A001", 2, new BigDecimal("10000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.NORMAL);
        SubAccountPO subAccount = buildSubAccount("A001");
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(subAccount));
        assertThatThrownBy(() -> postingDomainService.executeRealTimePosting(voucher, List.of(entry)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE);
                });
    }

    private AccountingVoucherPO buildVoucher(String voucherNo) {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setTxnNo("TXN20260624000001");
        voucher.setTraceNo("TRACE20260624000001");
        voucher.setTraceSeq(1);
        voucher.setBusinessCode("PAYMENT");
        voucher.setTradingCode("ONLINE_PAY");
        voucher.setPayChannel("ALIPAY");
        voucher.setTradeType(com.kltb.accounting.core.domain.enums.TradeTypeEnum.NORMAL);
        voucher.setTradeTime(LocalDateTime.of(2026, 6, 24, 10, 0, 0));
        voucher.setAccountingDate(LocalDate.of(2026, 6, 24));
        voucher.setSummary("test voucher");
        voucher.setTenantId(1001);
        return voucher;
    }

    private AccountingVoucherEntryPO buildEntry(String entryId, String voucherNo, String accountNo, int direction, BigDecimal amount) {
        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId(entryId);
        entry.setVoucherNo(voucherNo);
        entry.setAccountNo(accountNo);
        entry.setSubjectCode("1001");
        entry.setDebitCredit(direction == 1 ? DebitCreditEnum.DEBIT : DebitCreditEnum.CREDIT);
        entry.setAmount(amount);
        entry.setChangeDirection(direction == 1 ? ChangeDirectionEnum.INCREASE : ChangeDirectionEnum.DECREASE);
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

    @Test
    @DisplayName("实时过账: 账户包含AVAILABLE与FROZEN多子账户且FROZEN排在首位 -> 必须精准更新AVAILABLE子账户，绝不污染FROZEN")
    void executeRealTimePosting_withMultiSubAccounts_shouldOnlyUpdateAvailableSubAccount() {
        AccountingVoucherPO voucher = buildVoucher("VOU2");
        AccountingVoucherEntryPO entry = buildEntry("E2", "VOU2", "A001", 1, new BigDecimal("1000"));
        AccountPO account = buildAccount("A001", new BigDecimal("5000"), AccountStatusEnum.NORMAL);

        SubAccountPO frozenSub = new SubAccountPO();
        frozenSub.setAccountNo("A001");
        frozenSub.setBalanceType(BalanceTypeEnum.FROZEN);
        frozenSub.setBalance(new BigDecimal("2000"));
        frozenSub.setVersion(0L);

        SubAccountPO availSub = new SubAccountPO();
        availSub.setAccountNo("A001");
        availSub.setBalanceType(BalanceTypeEnum.AVAILABLE);
        availSub.setBalance(new BigDecimal("3000"));
        availSub.setVersion(0L);

        // 模拟数据库返回 FROZEN 在前，AVAILABLE 在后
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(frozenSub, availSub));

        postingDomainService.executeRealTimePosting(voucher, List.of(entry));

        // 验证主账户更新
        assertThat(account.getBalance()).isEqualTo(new BigDecimal("6000"));
        // 验证冻结子账户未被篡改
        assertThat(frozenSub.getBalance()).isEqualTo(new BigDecimal("2000"));
        verify(subAccountRepository, never()).updateById(frozenSub);
        // 验证可用子账户精准更新
        assertThat(availSub.getBalance()).isEqualTo(new BigDecimal("4000"));
        verify(subAccountRepository).updateById(availSub);
    }

    private SubAccountPO buildSubAccount(String accountNo) {
        SubAccountPO sub = new SubAccountPO();
        sub.setAccountNo(accountNo);
        sub.setBalanceType(BalanceTypeEnum.AVAILABLE);
        sub.setBalance(new BigDecimal("5000"));
        sub.setVersion(0L);
        return sub;
    }
}
