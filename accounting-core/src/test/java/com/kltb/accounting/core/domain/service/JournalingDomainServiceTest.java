package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.EodStatusEnum;
import com.kltb.accounting.core.domain.model.JournalCreateCommand;
import com.kltb.accounting.core.infrastructure.account.TransactionNoGenerator;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JournalingDomainServiceTest {

    @Mock
    private BusinessRecordRepository businessRecordRepository;
    @Mock
    private BusinessDetailRepository businessDetailRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private TransactionNoGenerator transactionNoGenerator;
    @Mock
    private AccountingDateCache accountingDateCache;
    @Mock
    private EodStatusRepository eodStatusRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private JournalingDomainService journalingDomainService;

    @Test
    @DisplayName("确定会计日期：当前全局会计日若已归档(status=COMPLETED)，自动推进至下一会计日")
    void testDetermineAccountingDate_AdvancesWhenCurrentArchived() {
        LocalDate currentDate = LocalDate.of(2026, 10, 15);
        EodStatusPO status = new EodStatusPO();
        status.setAccountingDate(currentDate);
        status.setEodStatus(EodStatusEnum.COMPLETED);

        when(accountingDateCache.getCurrentDate()).thenReturn(currentDate);
        when(eodStatusRepository.findByDate(currentDate)).thenReturn(status);

        LocalDate resultDate = journalingDomainService.determineAccountingDate(LocalDateTime.now());

        assertThat(resultDate).isEqualTo(currentDate.plusDays(1));
        verify(accountingDateCache).setCurrentDate(currentDate.plusDays(1));
    }

    @Test
    @DisplayName("流水持久化：若指定会计日期已日切归档(status=COMPLETED)，严禁写入并抛出 EOD_ALREADY_EXECUTED")
    void testPersistJournal_BlocksArchivedDate() {
        LocalDate archivedDate = LocalDate.of(2026, 10, 15);
        EodStatusPO status = new EodStatusPO();
        status.setAccountingDate(archivedDate);
        status.setEodStatus(EodStatusEnum.COMPLETED);

        when(eodStatusRepository.findByDate(archivedDate)).thenReturn(status);

        JournalCreateCommand command = new JournalCreateCommand();
        command.setAccountingDate(archivedDate);
        command.setTraceNo("TRACE001");
        command.setTraceSeq(1);

        assertThatThrownBy(() -> journalingDomainService.persistJournal(command))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException ae = (AccountException) ex;
                    assertThat(ae.getResultCode()).isEqualTo(ResultCode.EOD_ALREADY_EXECUTED);
                    assertThat(ae.getMessage()).contains("已日切归档封闭");
                });

        verifyNoInteractions(transactionTemplate);
    }
}
