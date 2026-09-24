package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostingEngineDomainServiceTest {

    @Mock private com.kltb.accounting.core.application.service.PostingApplicationService postingApplicationService;
    @Mock private AccountingVoucherRepository accountingVoucherRepository;
    @InjectMocks private PostingEngineDomainService postingEngineDomainService;

    @Test
    @DisplayName("批量过账: 查询到待过账凭证并逐笔执行")
    void executeBatchPosting_shouldProcessAllPendingVouchers() {
        AccountingVoucherPO v1 = buildVoucher("VOU001", VoucherStatusEnum.PENDING);
        AccountingVoucherPO v2 = buildVoucher("VOU002", VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByStatusAndDateRange(eq(VoucherStatusEnum.PENDING.getCode()), any(), any(), any(), anyInt()))
                .thenReturn(List.of(v1, v2));
        com.kltb.accounting.api.response.PostingExecuteResponse resp1 = new com.kltb.accounting.api.response.PostingExecuteResponse();
        resp1.setVoucherNo("VOU001"); resp1.setVoucherStatus(3); resp1.setVoucherStatusDesc("POSTED");
        com.kltb.accounting.api.response.PostingExecuteResponse resp2 = new com.kltb.accounting.api.response.PostingExecuteResponse();
        resp2.setVoucherNo("VOU002"); resp2.setVoucherStatus(3); resp2.setVoucherStatusDesc("POSTED");
        when(postingApplicationService.executePosting(any())).thenReturn(resp1).thenReturn(resp2);
        var result = postingEngineDomainService.executeBatchPosting(LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24), null, 50);
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("批量过账: 单笔失败不中断")
    void executeBatchPosting_singleFailure_shouldNotInterrupt() {
        AccountingVoucherPO v1 = buildVoucher("VOU001", VoucherStatusEnum.PENDING);
        AccountingVoucherPO v2 = buildVoucher("VOU002", VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByStatusAndDateRange(eq(VoucherStatusEnum.PENDING.getCode()), any(), any(), any(), anyInt()))
                .thenReturn(List.of(v1, v2));
        com.kltb.accounting.api.response.PostingExecuteResponse resp2 = new com.kltb.accounting.api.response.PostingExecuteResponse();
        resp2.setVoucherNo("VOU002"); resp2.setVoucherStatus(3); resp2.setVoucherStatusDesc("POSTED");
        when(postingApplicationService.executePosting(any()))
                .thenThrow(new RuntimeException("balance insufficient"))
                .thenReturn(resp2);
        var result = postingEngineDomainService.executeBatchPosting(LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24), null, 50);
        assertThat(result.getTotalCount()).isEqualTo(2);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getFailedList()).hasSize(1);
        assertThat(result.getFailedList().get(0).getVoucherNo()).isEqualTo("VOU001");
    }

    @Test
    @DisplayName("单笔凭证过账: 委托给 PostingApplicationService")
    void postSingleVoucher_shouldDelegateToPostingApplicationService() {
        com.kltb.accounting.api.response.PostingExecuteResponse resp = new com.kltb.accounting.api.response.PostingExecuteResponse();
        resp.setVoucherNo("VOU001"); resp.setVoucherStatus(3); resp.setVoucherStatusDesc("POSTED");
        when(postingApplicationService.executePosting(any())).thenReturn(resp);
        var result = postingEngineDomainService.postSingleVoucher("VOU001");
        assertThat(result.getVoucherNo()).isEqualTo("VOU001");
        verify(postingApplicationService).executePosting(any());
    }

    @Test
    @DisplayName("查询待过账凭证: 按状态+日期范围查询")
    void selectPendingVouchers_shouldQueryByStatusAndDate() {
        AccountingVoucherPO v1 = buildVoucher("VOU001", VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByStatusAndDateRange(eq(VoucherStatusEnum.PENDING.getCode()), any(), any(), any(), anyInt()))
                .thenReturn(List.of(v1));
        var result = postingEngineDomainService.selectPendingVouchers(LocalDate.of(2026, 6, 24), LocalDate.of(2026, 6, 24), null, 50);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getVoucherNo()).isEqualTo("VOU001");
    }

    private AccountingVoucherPO buildVoucher(String voucherNo, VoucherStatusEnum status) {
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setStatus(status);
        voucher.setAccountingDate(LocalDate.of(2026, 6, 24));
        voucher.setAmount(new BigDecimal("1000"));
        voucher.setBusinessCode("TEST");
        voucher.setFailReason(null);
        return voucher;
    }
}