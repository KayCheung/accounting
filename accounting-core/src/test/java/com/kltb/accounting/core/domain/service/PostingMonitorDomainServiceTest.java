package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.infrastructure.persistence.mapper.TransactionMapper;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingMonitorDomainServiceTest {

    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private TransactionMapper transactionMapper;
    @Mock private PostingEngineDomainService postingEngineDomainService;
    @InjectMocks private PostingMonitorDomainService postingMonitorDomainService;

    @Test
    @DisplayName("凭证过账进度: 全部分录已过账")
    void getVoucherProgress_allPosted_shouldBe100Percent() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.POSTED);
        AccountingVoucherEntryPO e1 = buildEntry("E1", "VOU001", VoucherEntryStatusEnum.POSTED, 1, 0, 0);
        AccountingVoucherEntryPO e2 = buildEntry("E2", "VOU001", VoucherEntryStatusEnum.POSTED, 1, 0, 0);
        when(accountingVoucherRepository.selectByVoucherNo("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(e1, e2));
        var result = postingMonitorDomainService.getVoucherProgress("VOU001");
        assertThat(result.getProgressPercent().compareTo(new BigDecimal("100.00")) == 0).isTrue();
    }

    @Test
    @DisplayName("凭证过账进度: 部分分录过账")
    void getVoucherProgress_halfPosted_shouldBe50Percent() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.POSTING);
        AccountingVoucherEntryPO e1 = buildEntry("E1", "VOU001", VoucherEntryStatusEnum.POSTED, 1, 0, 0);
        AccountingVoucherEntryPO e2 = buildEntry("E2", "VOU001", VoucherEntryStatusEnum.PENDING, 1, 0, 0);
        when(accountingVoucherRepository.selectByVoucherNo("VOU001")).thenReturn(voucher);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU001")).thenReturn(List.of(e1, e2));
        var result = postingMonitorDomainService.getVoucherProgress("VOU001");
        assertThat(result.getProgressPercent().compareTo(new BigDecimal("50.00")) == 0).isTrue();
    }

    @Test
    @DisplayName("事务过账进度: 关联凭证存在")
    void getTransactionProgress_existingTxn_shouldReturnProgress() {
        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN001");
        txn.setTraceNo("TRC001");
        txn.setAccountingDate(LocalDate.now());
        when(transactionRepository.selectByTxnNo("TXN001")).thenReturn(txn);
        when(accountingVoucherRepository.selectByTraceNo("TRC001")).thenReturn(List.of());
        var result = postingMonitorDomainService.getTransactionProgress("TXN001");
        assertThat(result.getTxnNo()).isEqualTo("TXN001");
    }

    @Test
    @DisplayName("异常凭证列表: 查询失败凭证")
    void getAbnormalVouchers_queryFailed_shouldReturnList() {
        when(accountingVoucherRepository.selectByStatusAndDateRange(VoucherStatusEnum.FAILED.getCode(), LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24), null, 100))
                .thenReturn(List.of(buildVoucher("VOU001", VoucherStatusEnum.FAILED)));
        var result = postingMonitorDomainService.getAbnormalVouchers(VoucherStatusEnum.FAILED.getCode(), LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24), 100);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("凭证重试: 正常委托执行")
    void retryAbnormalVoucher_validVoucher_shouldDelegate() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.FAILED);
        voucher.setRetryCount(0);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        PostingEngineDomainService.PostingExecuteResult postResult = new PostingEngineDomainService.PostingExecuteResult("VOU001", 3, "POSTED");
        when(postingEngineDomainService.postSingleVoucher("VOU001")).thenReturn(postResult);
        var result = postingMonitorDomainService.retryAbnormalVoucher("VOU001", "admin", "retry");
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("凭证重试: 重试次数超限")
    void retryAbnormalVoucher_retryExhausted_shouldThrow() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.FAILED);
        voucher.setRetryCount(5);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        assertThatThrownBy(() -> postingMonitorDomainService.retryAbnormalVoucher("VOU001", "admin", "retry"))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("凭证跳过: 设置skip_flag")
    void skipAbnormalVoucher_shouldSetSkipFlag() {
        AccountingVoucherPO voucher = buildVoucher("VOU001", VoucherStatusEnum.FAILED);
        when(accountingVoucherRepository.selectByVoucherNoSimple("VOU001")).thenReturn(voucher);
        postingMonitorDomainService.skipAbnormalVoucher("VOU001", "admin", "skip reason");
        verify(accountingVoucherRepository).updateById(argThat(v ->
                v.getSkipFlag() != null && v.getSkipFlag() == 1
                        && v.getFailReason() != null && v.getFailReason().contains("admin")
        ));
    }

    private AccountingVoucherPO buildVoucher(String voucherNo, VoucherStatusEnum status) {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(status);
        voucher.setAccountingDate(LocalDate.now());
        voucher.setFailReason(null);
        voucher.setRetryCount(0);
        voucher.setSkipFlag(0);
        voucher.setAmount(new BigDecimal("1000"));
        voucher.setBusinessCode("TEST");
        voucher.setUpdateTime(LocalDateTime.now().minusHours(2));
        return voucher;
    }

    private AccountingVoucherEntryPO buildEntry(String entryId, String voucherNo, VoucherEntryStatusEnum status, int unilateral, int buffered, int changeDir) {
        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId(entryId);
        entry.setVoucherNo(voucherNo);
        entry.setRowNum(1);
        entry.setSubjectCode("1001");
        entry.setAccountNo("A001");
        entry.setStatus(status);
        entry.setAmount(new BigDecimal("1000"));
        entry.setAccountingDate(LocalDate.now());
        entry.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT);
        entry.setCurrency("CNY");
        entry.setSummary("test");
        entry.setUnilateral(unilateral);
        entry.setBuffered(buffered);
        entry.setChangeDirection(changeDir);
        return entry;
    }
}