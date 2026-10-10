package com.kltb.accounting.core.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kltb.accounting.api.request.BalanceSheetQueryRequest;
import com.kltb.accounting.api.request.IncomeStatementQueryRequest;
import com.kltb.accounting.api.response.BalanceSheetResponse;
import com.kltb.accounting.api.response.IncomeStatementKpiResponse;
import com.kltb.accounting.api.response.IncomeStatementResponse;
import com.kltb.accounting.core.domain.service.FinancialReportDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FinancialReportSnapshotRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * FinancialReportApplicationService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class FinancialReportApplicationServiceTest {

    @Mock
    private FinancialReportDomainService reportDomainService;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private FinancialReportSnapshotRepository snapshotRepository;

    private ObjectMapper objectMapper;

    private FinancialReportApplicationService applicationService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        applicationService = new FinancialReportApplicationService(
                reportDomainService,
                subjectRepository,
                snapshotRepository,
                objectMapper
        );
    }

    @Test
    @DisplayName("generateAndArchiveReports: 生成并持久化资产负债表与利润表快照")
    void generateAndArchiveReports_shouldPersistSnapshots() {
        LocalDate testDate = LocalDate.of(2026, 10, 10);

        BalanceSheetResponse mockBs = new BalanceSheetResponse();
        mockBs.setAccountingDate(testDate);
        mockBs.setBalanced(true);
        mockBs.setTotalAssetEnd(new BigDecimal("100000.00"));
        mockBs.setTotalLiabilityAndEquityEnd(new BigDecimal("100000.00"));

        IncomeStatementResponse mockIs = new IncomeStatementResponse();
        mockIs.setPeriodDesc("2026年10月");
        IncomeStatementKpiResponse kpi = new IncomeStatementKpiResponse();
        kpi.setRevenueMonth(new BigDecimal("50000.00"));
        kpi.setNetProfitMonth(new BigDecimal("12000.00"));
        mockIs.setKpi(kpi);

        when(reportDomainService.generateBalanceSheet(any(BalanceSheetQueryRequest.class))).thenReturn(mockBs);
        when(reportDomainService.generateIncomeStatement(any(IncomeStatementQueryRequest.class))).thenReturn(mockIs);

        applicationService.generateAndArchiveReports(testDate);

        ArgumentCaptor<FinancialReportSnapshotPO> captor = ArgumentCaptor.forClass(FinancialReportSnapshotPO.class);
        verify(snapshotRepository, times(2)).saveOrUpdate(captor.capture());

        List<FinancialReportSnapshotPO> captured = captor.getAllValues();
        assertThat(captured).hasSize(2);

        FinancialReportSnapshotPO bsSnapshot = captured.get(0);
        assertThat(bsSnapshot.getReportType()).isEqualTo("BALANCE_SHEET");
        assertThat(bsSnapshot.getAccountingDate()).isEqualTo(testDate);
        assertThat(bsSnapshot.getTotalAsset()).isEqualByComparingTo("100000.00");
        assertThat(bsSnapshot.getReportContent()).contains("100000.00");

        FinancialReportSnapshotPO isSnapshot = captured.get(1);
        assertThat(isSnapshot.getReportType()).isEqualTo("INCOME_STATEMENT");
        assertThat(isSnapshot.getAccountingDate()).isEqualTo(testDate);
        assertThat(isSnapshot.getTotalAsset()).isEqualByComparingTo("50000.00");
        assertThat(isSnapshot.getReportContent()).contains("2026年10月");
    }
}
