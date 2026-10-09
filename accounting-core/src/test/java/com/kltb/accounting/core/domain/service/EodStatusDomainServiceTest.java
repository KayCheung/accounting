package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.infrastructure.persistence.entity.EodStatusPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.EodStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EodStatusDomainServiceTest {

    @Mock
    private EodStatusRepository eodStatusRepository;

    @InjectMocks
    private EodStatusDomainService eodStatusDomainService;

    @Test
    @DisplayName("创建或重置日切状态成功")
    void createStatus_delegatesToRepository() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        EodStatusPO po = new EodStatusPO();
        po.setId(1L);
        po.setAccountingDate(date);
        po.setEodStatus(1);

        when(eodStatusRepository.createStatus(date)).thenReturn(po);

        EodStatusPO result = eodStatusDomainService.createStatus(date);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        verify(eodStatusRepository).createStatus(date);
    }

    @Test
    @DisplayName("更新阶段状态成功")
    void updateStage_success() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        eodStatusDomainService.updateStage(3, date);
        verify(eodStatusRepository).updateStatus(date, 3);
    }

    @Test
    @DisplayName("标记日切完成成功")
    void markCompleted_success() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        eodStatusDomainService.markCompleted(date, 500L);
        verify(eodStatusRepository).markCompleted(date, 500L);
    }

    @Test
    @DisplayName("标记日切失败时超长原因自动截断至255字符")
    void markFailed_truncatesLongReason() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        String longReason = "A".repeat(300);

        eodStatusDomainService.markFailed(date, "CLEANUP", longReason);

        verify(eodStatusRepository).markFailed(eq(date), eq("CLEANUP"), argThat(reason -> reason.length() == 255));
    }

    @Test
    @DisplayName("根据会计日期查询日切状态")
    void findByDate_success() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        EodStatusPO po = new EodStatusPO();
        po.setAccountingDate(date);
        when(eodStatusRepository.findByDate(date)).thenReturn(po);

        EodStatusPO result = eodStatusDomainService.findByDate(date);

        assertThat(result).isNotNull();
        assertThat(result.getAccountingDate()).isEqualTo(date);
    }

    @Test
    @DisplayName("查询最近已完成日切记录")
    void findLatestCompleted_success() {
        EodStatusPO po = new EodStatusPO();
        po.setEodStatus(8);
        when(eodStatusRepository.findLatestCompleted()).thenReturn(po);

        EodStatusPO result = eodStatusDomainService.findLatestCompleted();

        assertThat(result).isNotNull();
        assertThat(result.getEodStatus()).isEqualTo(8);
    }

    @Test
    @DisplayName("更新切日完成时间")
    void updateSwitchDateTime_success() {
        LocalDate date = LocalDate.of(2026, 10, 8);
        LocalDateTime now = LocalDateTime.now();
        eodStatusDomainService.updateSwitchDateTime(date, now);
        verify(eodStatusRepository).updateSwitchDateTime(date, now);
    }
}
