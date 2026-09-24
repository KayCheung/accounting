package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReversalDomainServiceTest {

    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @Mock private RedisSequenceGenerator seqGen;
    @Mock private DistributedLockTemplate distributedLockTemplate;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks private ReversalDomainService reversalDomainService;

    @Test
    @DisplayName("红冲: 原凭证不存在 -> 抛出 REVERSAL_ORIGINAL_NOT_FOUND")
    void executeReversal_origNotFound_shouldThrow() {
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(null);
        assertThatThrownBy(() -> reversalDomainService.executeReversal("VOU001", "SYSTEM", "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.REVERSAL_ORIGINAL_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("红冲: 原凭证未过账 -> 抛出 REVERSAL_ORIGINAL_NOT_POSTED")
    void executeReversal_origNotPosted_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectReversalByOrig("VOU001")).thenReturn(List.of());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        assertThatThrownBy(() -> reversalDomainService.executeReversal("VOU001", "SYSTEM", "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.REVERSAL_ORIGINAL_NOT_POSTED);
                });
    }

    @Test
    @DisplayName("红冲: 原凭证分录未全部过账 -> 抛出 REVERSAL_ENTRIES_NOT_ALL_POSTED")
    void executeReversal_entriesNotAllPosted_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.POSTED);
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU001", VoucherEntryStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectReversalByOrig("VOU001")).thenReturn(List.of());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        assertThatThrownBy(() -> reversalDomainService.executeReversal("VOU001", "SYSTEM", "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.REVERSAL_ENTRIES_NOT_ALL_POSTED);
                });
    }

    @Test
    @DisplayName("红冲: 已存在红冲记录 -> 抛出 REVERSAL_ALREADY_EXISTS")
    void executeReversal_alreadyExists_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.POSTED);
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU001", VoucherEntryStatusEnum.POSTED);
        AccountingVoucherPO reversal = buildVoucher("REVVOU001", VoucherStatusEnum.REVERSED);
        reversal.setOrigVoucherNo("VOU001");
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectReversalByOrig("VOU001")).thenReturn(List.of(reversal));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        assertThatThrownBy(() -> reversalDomainService.executeReversal("VOU001", "SYSTEM", "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.REVERSAL_ALREADY_EXISTS);
                });
    }

    @Test
    @DisplayName("红冲可行性检查: 已过账且无红冲记录 -> 可红冲")
    void isReversable_postedAndNoReversal_shouldReturnTrue() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.POSTED);
        AccountingVoucherEntryPO entry = buildEntry("E1", "VOU001", VoucherEntryStatusEnum.POSTED);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectReversalByOrig("VOU001")).thenReturn(List.of());
        assertThat(reversalDomainService.isReversable("VOU001")).isTrue();
    }

    @Test
    @DisplayName("红冲可行性检查: 未过账 -> 不可红冲")
    void isReversable_notPosted_shouldReturnFalse() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        assertThat(reversalDomainService.isReversable("VOU001")).isFalse();
    }

    @Test
    @DisplayName("红冲可行性检查: 凭证不存在 -> 不可红冲")
    void isReversable_voucherNotFound_shouldReturnFalse() {
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(null);
        assertThat(reversalDomainService.isReversable("VOU001")).isFalse();
    }

    @Test
    @DisplayName("查询红冲记录: 调用 selectReversalByOrig 返回列表")
    void queryReversalRecords_shouldReturnList() {
        AccountingVoucherPO reversal = buildVoucher("REV001", VoucherStatusEnum.REVERSED);
        reversal.setOrigVoucherNo("VOU001");
        when(accountingVoucherRepository.selectReversalByOrig("VOU001")).thenReturn(List.of(reversal));
        List<AccountingVoucherPO> result = reversalDomainService.queryReversalRecords("VOU001");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrigVoucherNo()).isEqualTo("VOU001");
    }

    // ==================== 辅助方法 ====================

    private AccountingVoucherPO buildVoucher(String voucherNo, VoucherStatusEnum status) {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(status);
        voucher.setTradeType(TradeTypeEnum.NORMAL);
        voucher.setBusinessCode("TEST");
        voucher.setTradingCode("TEST");
        voucher.setPayChannel("TEST");
        voucher.setAmount(new BigDecimal("1000"));
        voucher.setAccountingDate(LocalDate.now());
        voucher.setSummary("test");
        voucher.setTraceNo("TRC001");
        voucher.setTraceSeq(0);
        voucher.setBookkeeperName("SYSTEM");
        voucher.setPostingType(com.kltb.accounting.core.domain.enums.PostingTypeEnum.AUTOMATIC);
        return voucher;
    }

    private AccountingVoucherEntryPO buildEntry(String entryId, String voucherNo, VoucherEntryStatusEnum status) {
        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId(entryId);
        entry.setVoucherNo(voucherNo);
        entry.setRowNum(1);
        entry.setSubjectCode("1001");
        entry.setAccountNo("A001");
        entry.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT);
        entry.setAmount(new BigDecimal("1000"));
        entry.setCurrency("CNY");
        entry.setSummary("test");
        entry.setStatus(status);
        entry.setAccountingDate(LocalDate.now());
        return entry;
    }
}
