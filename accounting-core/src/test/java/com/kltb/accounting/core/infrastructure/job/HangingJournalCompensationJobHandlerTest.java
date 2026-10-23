package com.kltb.accounting.core.infrastructure.job;

import com.kltb.accounting.core.application.service.JournalingApplicationService;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HangingJournalCompensationJobHandlerTest {

    @Mock
    private BusinessRecordRepository businessRecordRepository;

    @Mock
    private JournalingApplicationService journalingApplicationService;

    @Mock
    private com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private com.kltb.accounting.core.domain.service.AsyncPostingDomainService asyncPostingDomainService;

    @InjectMocks
    private HangingJournalCompensationJobHandler jobHandler;

    @Test
    @DisplayName("execute: 无超时挂起流水直接返回")
    void execute_noHangingRecords_shouldReturnImmediately() {
        when(businessRecordRepository.selectTimeoutProcessingRecords(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.emptyList());

        jobHandler.execute();

        verify(journalingApplicationService, never()).reconcileHangingJournal(anyString());
    }

    @Test
    @DisplayName("execute: 发现超时挂起流水并调用对账推进")
    void execute_withHangingRecords_shouldReconcileEach() {
        BusinessRecordPO record1 = new BusinessRecordPO();
        record1.setTraceNo("TRC_HANG_01");

        BusinessRecordPO record2 = new BusinessRecordPO();
        record2.setTraceNo("TRC_HANG_02");

        when(businessRecordRepository.selectTimeoutProcessingRecords(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(record1, record2));

        jobHandler.execute();

        verify(journalingApplicationService).reconcileHangingJournal("TRC_HANG_01");
        verify(journalingApplicationService).reconcileHangingJournal("TRC_HANG_02");
    }

    @Test
    @DisplayName("execute: 发现超时过账中凭证并调用终态对齐")
    void execute_withHangingVouchers_shouldSyncEach() {
        when(businessRecordRepository.selectTimeoutProcessingRecords(any(LocalDateTime.class), anyInt()))
                .thenReturn(Collections.emptyList());

        com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO v1 = new com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO();
        v1.setVoucherNo("VOU_HANG_01");

        when(accountingVoucherRepository.selectTimeoutPostingVouchers(any(LocalDateTime.class), anyInt()))
                .thenReturn(List.of(v1));

        jobHandler.execute();

        verify(asyncPostingDomainService).syncVoucherStatusIfAllPosted("VOU_HANG_01");
    }
}
