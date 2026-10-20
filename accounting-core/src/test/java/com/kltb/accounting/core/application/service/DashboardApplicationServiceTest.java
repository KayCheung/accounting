package com.kltb.accounting.core.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.api.response.DashboardOverviewResponse;
import com.kltb.accounting.core.domain.enums.EodStatusEnum;
import com.kltb.accounting.core.domain.enums.ManualVoucherApplyStatusEnum;
import com.kltb.accounting.core.domain.service.EodStatusDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService;
import com.kltb.accounting.core.domain.service.TrialBalanceDomainService.TrialBalanceResult;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.ManualVoucherApplyRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardApplicationServiceTest {

    @Mock
    private AccountingDateCache accountingDateCache;

    @Mock
    private EodStatusDomainService eodStatusDomainService;

    @Mock
    private TrialBalanceDomainService trialBalanceDomainService;

    @Mock
    private ManualVoucherApplyRepository manualVoucherApplyRepository;

    @Mock
    private ManualVoucherApplyMapper manualVoucherApplyMapper;

    @Mock
    private AccountingVoucherMapper accountingVoucherMapper;

    @Mock
    private BusinessRecordMapper businessRecordMapper;

    @Mock
    private BufferPostingDetailMapper bufferPostingDetailMapper;

    @Mock
    private AccountFreezeDetailMapper accountFreezeDetailMapper;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private DashboardApplicationService dashboardApplicationService;

    @Test
    @DisplayName("getDashboardOverview: 正常聚合返回系统会计日、日切状态、试算平衡及各角色待办指标")
    void getDashboardOverview_normal_shouldReturnAllAggregatedMetrics() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        when(accountingDateCache.getCurrentDate()).thenReturn(date);

        EodStatusPO po = new EodStatusPO();
        po.setEodStatus(EodStatusEnum.NOT_STARTED);
        when(eodStatusDomainService.findByDate(date)).thenReturn(po);

        TrialBalanceResult trialResult = new TrialBalanceResult(
                date, true, new BigDecimal("10000.00"), new BigDecimal("10000.00"),
                BigDecimal.ZERO, Collections.emptyList(), Collections.emptyList());
        when(trialBalanceDomainService.executeTrialBalance(date)).thenReturn(trialResult);

        // 手工凭证待办
        when(manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.DRAFT)).thenReturn(3L);
        when(manualVoucherApplyMapper.selectCount(any())).thenReturn(2L);
        when(manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_AUDIT)).thenReturn(5L);
        when(manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_REVIEW)).thenReturn(4L);
        when(manualVoucherApplyRepository.countByStatus(ManualVoucherApplyStatusEnum.PENDING_BOOKKEEPING)).thenReturn(6L);

        // 异常治理
        when(accountingVoucherMapper.selectCount(any())).thenReturn(1L);
        when(businessRecordMapper.selectCount(any())).thenReturn(0L);
        when(bufferPostingDetailMapper.selectCount(any())).thenReturn(8L);
        when(accountFreezeDetailMapper.selectCount(any())).thenReturn(10L);
        when(accountMapper.selectCount(any())).thenReturn(50L);

        DashboardOverviewResponse response = dashboardApplicationService.getDashboardOverview();

        assertThat(response).isNotNull();
        assertThat(response.getAccountingDate()).isEqualTo("2026-10-20");
        assertThat(response.getEodStatus()).isEqualTo(1);
        assertThat(response.getEodStatusDesc()).isEqualTo("正常营业");
        assertThat(response.getTrialBalanced()).isTrue();
        assertThat(response.getTrialBalanceDiff()).isEqualTo(BigDecimal.ZERO);
        assertThat(response.getMakerDraftCount()).isEqualTo(3L);
        assertThat(response.getMakerRejectedCount()).isEqualTo(2L);
        assertThat(response.getPendingAuditCount()).isEqualTo(5L);
        assertThat(response.getPendingReviewCount()).isEqualTo(4L);
        assertThat(response.getPendingBookkeepingCount()).isEqualTo(6L);
        assertThat(response.getPendingBufferCount()).isEqualTo(8L);
        assertThat(response.getActiveFreezeCount()).isEqualTo(10L);
        assertThat(response.getActiveAccountCount()).isEqualTo(50L);
    }

    @Test
    @DisplayName("getDashboardOverview: 异常降级兜底保护，不抛出异常阻断大盘展示")
    void getDashboardOverview_exception_shouldDegradeGracefully() {
        when(accountingDateCache.getCurrentDate()).thenReturn(null);
        when(eodStatusDomainService.findByDate(any())).thenThrow(new RuntimeException("DB Connection Timeout"));

        DashboardOverviewResponse response = dashboardApplicationService.getDashboardOverview();

        assertThat(response).isNotNull();
        assertThat(response.getAccountingDate()).isNotNull();
        assertThat(response.getEodStatus()).isEqualTo(1);
        assertThat(response.getEodStatusDesc()).isEqualTo("正常营业");
        assertThat(response.getTrialBalanced()).isTrue();
    }
}
