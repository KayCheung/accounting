package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.EodExecuteRequest;
import com.kltb.accounting.api.response.EodExecuteResponse;
import com.kltb.accounting.api.response.EodPreCheckResponse;
import com.kltb.accounting.core.application.assembler.EodAssembler;
import com.kltb.accounting.core.domain.enums.EodStatusEnum;
import com.kltb.accounting.core.domain.service.*;
import com.kltb.accounting.core.domain.service.EodCheckDomainService.EodPreCheckResult;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EodApplicationServiceTest {

    @Mock
    private EodCheckDomainService eodCheckDomainService;
    @Mock
    private EodDomainService eodDomainService;
    @Mock
    private TrialBalanceDomainService trialBalanceDomainService;
    @Mock
    private PeriodEndTransferDomainService periodEndTransferDomainService;
    @Mock
    private EodStatusDomainService eodStatusDomainService;
    @Mock
    private EodArchiveDomainService eodArchiveDomainService;
    @Mock
    private AccountingDateSwitchDomainService accountingDateSwitchDomainService;
    @Mock
    private AccountingDateCache accountingDateCache;
    @Mock
    private EodAssembler eodAssembler;

    @InjectMocks
    private EodApplicationService eodApplicationService;

    @Test
    @DisplayName("前置诊断：已归档日期(status=8)直接返回全部通过，避免受当前未决事务干扰")
    void testGetPreCheckResult_WhenArchived() {
        LocalDate date = LocalDate.of(2026, 10, 14);
        EodStatusPO archivedStatus = new EodStatusPO();
        archivedStatus.setAccountingDate(date);
        archivedStatus.setEodStatus(EodStatusEnum.COMPLETED);

        when(eodStatusDomainService.findByDate(date)).thenReturn(archivedStatus);

        EodPreCheckResponse response = eodApplicationService.getPreCheckResult(date);

        assertThat(response.isAllPassed()).isTrue();
        assertThat(response.getChecks()).hasSize(5);
        assertThat(response.getChecks()).allMatch(c -> c.isPassed() && c.getCount() == 0);

        verifyNoInteractions(eodCheckDomainService);
    }

    @Test
    @DisplayName("前置诊断：未归档日期正常调用检查逻辑")
    void testGetPreCheckResult_WhenNotArchived() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        EodStatusPO status = new EodStatusPO();
        status.setAccountingDate(date);
        status.setEodStatus(EodStatusEnum.NOT_STARTED);

        EodPreCheckResult preCheckResult = new EodPreCheckResult(date, true, 0, 0, 0, 0, 0);
        EodPreCheckResponse expectedResponse = EodPreCheckResponse.builder()
                .accountingDate(date)
                .allPassed(true)
                .checks(Collections.emptyList())
                .build();

        when(eodStatusDomainService.findByDate(date)).thenReturn(status);
        when(eodCheckDomainService.checkEodPreconditions(date)).thenReturn(preCheckResult);
        when(eodAssembler.toPreCheckResponse(preCheckResult)).thenReturn(expectedResponse);

        EodPreCheckResponse response = eodApplicationService.getPreCheckResult(date);

        assertThat(response).isEqualTo(expectedResponse);
        verify(eodCheckDomainService).checkEodPreconditions(date);
    }

    @Test
    @DisplayName("日切执行：归档完成后自动推进全局会计日期至T+1并刷新缓存")
    void testExecuteEod_Success_AdvancesAccountingDate() {
        LocalDate date = LocalDate.of(2026, 10, 15);
        LocalDate nextDate = date.plusDays(1);

        EodExecuteRequest request = new EodExecuteRequest();
        request.setAccountingDate(date);
        request.setSkipPreCheck(true);
        request.setExecuteTransfer(false);

        when(eodDomainService.calculateDailyBalances(date)).thenReturn(Collections.emptyList());
        when(eodDomainService.generateDailySnapshot(date)).thenReturn(5);

        TrialBalanceResult trialResult = new TrialBalanceResult(
                date, true, BigDecimal.valueOf(100), BigDecimal.valueOf(100),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList()
        );
        when(trialBalanceDomainService.executeTrialBalance(date)).thenReturn(trialResult);

        TrialBalanceDomainService.GlReconciliationResult glResult =
                new TrialBalanceDomainService.GlReconciliationResult(date, true, BigDecimal.ZERO, Collections.emptyList());
        when(trialBalanceDomainService.executeGlReconciliation(date)).thenReturn(glResult);

        TrialBalanceDomainService.BalanceReconciliationResult balResult =
                new TrialBalanceDomainService.BalanceReconciliationResult(date, true, BigDecimal.ZERO, Collections.emptyList());
        when(trialBalanceDomainService.executeBalanceReconciliation(date)).thenReturn(balResult);

        when(accountingDateCache.getCurrentDate()).thenReturn(date);

        EodExecuteResponse response = eodApplicationService.executeEod(request);

        assertThat(response.isBalanceCalculated()).isTrue();
        assertThat(response.isTrialBalancePassed()).isTrue();
        verify(eodArchiveDomainService).archive(eq(date), anyLong());
        verify(eodStatusDomainService).markCompleted(eq(date), anyLong());
        verify(accountingDateSwitchDomainService).switchDate(nextDate);
    }
}
