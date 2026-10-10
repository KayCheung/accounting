package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.messaging.PostingMessagePayload;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.MessageReceiptMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsyncPostingDomainServiceTest {

    @Mock private LocalMessageService localMessageService;
    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private SubAccountRepository subAccountRepository;
    @Mock private AccountDetailMapper accountDetailMapper;
    @Mock private SubAccountDetailMapper subAccountDetailMapper;
    @Mock private MessageReceiptMapper messageReceiptMapper;
    @Mock private TransactionRepository transactionRepository;
    @Mock private RollbackDomainService rollbackDomainService;
    @Mock private com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository businessRecordRepository;

    @InjectMocks private AsyncPostingDomainService asyncPostingDomainService;

    @Test
    @DisplayName("消费异步消息: payload 为 null 快速失败抛出 PARAM_ERROR")
    void consumeAsyncPostingMessage_nullPayload_shouldThrow() {
        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(null))
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));

        verifyNoInteractions(accountingVoucherRepository, accountRepository);
    }

    @Test
    @DisplayName("消费异步消息: 核心参数缺失抛出 PARAM_ERROR")
    void consumeAsyncPostingMessage_missingCoreFields_shouldThrow() {
        PostingMessagePayload payload = new PostingMessagePayload();
        payload.setVoucherNo("VOU001");
        payload.setEntryId(""); // 缺失 entryId

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));

        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("消费异步消息: 分录不存在抛出 DATA_NOT_FOUND")
    void consumeAsyncPostingMessage_entryNotFound_shouldThrow() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 1);

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(AccountException.class)
            .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.DATA_NOT_FOUND));

        verifyNoInteractions(accountRepository);
        verify(accountRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("消费异步消息: 分录已过账 (POSTED) 幂等直接返回，不触发后续逻辑")
    void consumeAsyncPostingMessage_alreadyPosted_shouldReturnEarly() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 1);

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.POSTED);

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));

        asyncPostingDomainService.consumeAsyncPostingMessage(payload);

        verify(accountingVoucherRepository, never()).selectByVoucherNoSimple(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("消费异步消息: 凭证不存在抛出 VOUCHER_NOT_FOUND，不加锁不更新账户")
    void consumeAsyncPostingMessage_voucherNotFound_shouldThrow() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 1);

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(null);

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.VOUCHER_NOT_FOUND));

        verify(accountRepository, never()).selectForUpdateBatch(any());
        verify(accountRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("消费异步消息: 凭证 txnNo 为空抛出 PARAM_ERROR，不加锁不更新账户")
    void consumeAsyncPostingMessage_voucherTxnNoBlank_shouldThrow() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 1);

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU001");
        voucher.setTxnNo("  "); // 空白 txnNo

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(ServiceException.class)
            .satisfies(ex -> assertThat(((ServiceException) ex).getResultCode()).isEqualTo(ResultCode.PARAM_ERROR));

        verify(accountRepository, never()).selectForUpdateBatch(any());
        verify(accountRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("消费异步消息: 账户不存在抛出 ACCOUNT_NOT_FOUND，不加锁不更新账户")
    void consumeAsyncPostingMessage_accountNotFound_shouldThrow() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 1);

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        AccountingVoucherPO voucher = buildVoucher("VOU001", "TXN001");

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(null);

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(AccountException.class)
            .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND));

        verify(accountRepository, never()).selectForUpdateBatch(any());
        verify(accountRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("消费异步消息: 账户冻结抛出 ACCOUNT_FROZEN，不发生余额扣减或更新")
    void consumeAsyncPostingMessage_accountFrozen_shouldThrow() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("100"), 2); // 减金额

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        AccountingVoucherPO voucher = buildVoucher("VOU001", "TXN001");
        AccountPO account = buildAccount("A001", AccountStatusEnum.FROZEN, new BigDecimal("1000"));

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(AccountException.class)
            .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN));

        verify(accountRepository, never()).selectForUpdateBatch(any());
        verify(accountRepository, never()).updateById(any());
    }

    @Test
    @DisplayName("消费异步消息: 账户余额不足抛出 INSUFFICIENT_BALANCE，更新前拦截不落库")
    void consumeAsyncPostingMessage_insufficientBalance_shouldThrowBeforeUpdate() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("2000"), 2); // 扣 2000

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        AccountingVoucherPO voucher = buildVoucher("VOU001", "TXN001");
        AccountPO account = buildAccount("A001", AccountStatusEnum.NORMAL, new BigDecimal("1000")); // 仅有 1000

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> asyncPostingDomainService.consumeAsyncPostingMessage(payload))
            .isInstanceOf(AccountException.class)
            .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.INSUFFICIENT_BALANCE));

        // 核心验证：数据库更新未被调用！
        verify(accountRepository, never()).updateById(any());
        verify(subAccountRepository, never()).updateById(any());
        verify(accountDetailMapper, never()).insert(any(AccountDetailPO.class));
        verify(accountingVoucherRepository, never()).updateEntryById(any());
    }

    @Test
    @DisplayName("消费异步消息: 正常过账成功，更新主子账户、明细、分录状态与消息回执")
    void consumeAsyncPostingMessage_success() {
        PostingMessagePayload payload = buildPayload("VOU001", "E001", "A001", new BigDecimal("500"), 1); // 加 500

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setBuffered(0);
        entry.setStatus(VoucherEntryStatusEnum.PENDING);

        AccountingVoucherPO voucher = buildVoucher("VOU001", "TXN001");
        AccountPO account = buildAccount("A001", AccountStatusEnum.NORMAL, new BigDecimal("1000"));
        SubAccountPO subAccount = new SubAccountPO();
        subAccount.setAccountNo("A001");
        subAccount.setBalanceType(BalanceTypeEnum.AVAILABLE);
        subAccount.setBalance(new BigDecimal("1000"));
        subAccount.setVersion(1L);

        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(accountRepository.selectForUpdateBatch(List.of("A001"))).thenReturn(List.of(account));
        when(subAccountRepository.selectForUpdate("A001")).thenReturn(List.of(subAccount));

        asyncPostingDomainService.consumeAsyncPostingMessage(payload);

        // 验证主账户更新
        assertThat(account.getBalance()).isEqualByComparingTo("1500");
        assertThat(account.getVersion()).isEqualTo(1L); // 验证业务层未手动自增 version
        verify(accountRepository).updateById(account);

        // 验证子账户更新
        assertThat(subAccount.getBalance()).isEqualByComparingTo("1500");
        assertThat(subAccount.getVersion()).isEqualTo(1L); // 验证业务层未手动自增 version
        verify(subAccountRepository).updateById(subAccount);

        // 验证明细插入
        ArgumentCaptor<AccountDetailPO> detailCaptor = ArgumentCaptor.forClass(AccountDetailPO.class);
        verify(accountDetailMapper).insert(detailCaptor.capture());
        AccountDetailPO detail = detailCaptor.getValue();
        assertThat(detail.getPreBalance()).isEqualByComparingTo("1000");
        assertThat(detail.getPostBalance()).isEqualByComparingTo("1500");
        assertThat(detail.getTxnNo()).isEqualTo("TXN001");
        assertThat(detail.getBusinessCode()).isEqualTo("BIZ01");

        // 验证子账户明细插入
        verify(subAccountDetailMapper).insert(any(SubAccountDetailPO.class));

        // 验证分录状态更新为 POSTED
        assertThat(entry.getStatus()).isEqualTo(VoucherEntryStatusEnum.POSTED);
        verify(accountingVoucherRepository).updateEntryById(entry);

        // 验证本地消息标记已发送与回执写入
        verify(localMessageService).markSent("E001");
        verify(messageReceiptMapper).insert(any(MessageReceiptPO.class));
    }

    private PostingMessagePayload buildPayload(String voucherNo, String entryId, String accountNo,
                                                BigDecimal amount, int changeDirection) {
        return new PostingMessagePayload(
            voucherNo,
            entryId,
            accountNo,
            "1001",
            1, // DEBIT
            changeDirection,
            amount,
            LocalDate.of(2026, 10, 8),
            "CNY",
            "测试异步过账"
        );
    }

    private AccountingVoucherPO buildVoucher(String voucherNo, String txnNo) {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setTxnNo(txnNo);
        voucher.setTraceNo("TRACE001");
        voucher.setTraceSeq(1);
        voucher.setBusinessCode("BIZ01");
        voucher.setTradingCode("TRADE01");
        voucher.setPayChannel("ALIPAY");
        voucher.setTradeType(TradeTypeEnum.NORMAL);
        voucher.setTradeTime(LocalDateTime.now());
        voucher.setAccountingDate(LocalDate.of(2026, 10, 8));
        voucher.setSummary("凭证摘要");
        voucher.setTenantId(1);
        return voucher;
    }

    private AccountPO buildAccount(String accountNo, AccountStatusEnum status, BigDecimal balance) {
        AccountPO account = new AccountPO();
        account.setAccountNo(accountNo);
        account.setStatus(status);
        account.setRiskStatus(RiskStatusEnum.NORMAL);
        account.setBalance(balance);
        account.setVersion(1L);
        return account;
    }
}
