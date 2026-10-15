package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.EodStatusEnum;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingDateSwitchDomainServiceTest {

    @Mock
    private AccountingDateCache accountingDateCache;

    @Mock
    private EodStatusRepository eodStatusRepository;

    @Mock
    private EodStatusDomainService eodStatusDomainService;

    @InjectMocks
    private AccountingDateSwitchDomainService dateSwitchDomainService;

    @Test
    @DisplayName("切日成功：更新旧日期切日时间，初始化新日期状态为1，更新缓存")
    void testSwitchDate_Success() {
        LocalDate currentDate = LocalDate.of(2026, 10, 15);
        LocalDate targetDate = LocalDate.of(2026, 10, 16);

        when(accountingDateCache.getCurrentDate()).thenReturn(currentDate);
        when(eodStatusRepository.findByDate(targetDate)).thenReturn(null);

        AccountingDateSwitchDomainService.DateSwitchResult result =
                dateSwitchDomainService.switchDate(targetDate);

        assertThat(result.isAlreadySwitched()).isFalse();
        assertThat(result.getPreviousDate()).isEqualTo(currentDate);
        assertThat(result.getNewDate()).isEqualTo(targetDate);

        verify(eodStatusDomainService).updateSwitchDateTime(eq(currentDate), any(LocalDateTime.class));
        verify(eodStatusDomainService).createStatus(targetDate);
        verify(accountingDateCache).setCurrentDate(targetDate);
    }

    @Test
    @DisplayName("切日幂等：当前全局日期已等于或大于目标日期时跳过")
    void testSwitchDate_AlreadySwitched() {
        LocalDate currentDate = LocalDate.of(2026, 10, 16);
        LocalDate targetDate = LocalDate.of(2026, 10, 16);

        when(accountingDateCache.getCurrentDate()).thenReturn(currentDate);

        AccountingDateSwitchDomainService.DateSwitchResult result =
                dateSwitchDomainService.switchDate(targetDate);

        assertThat(result.isAlreadySwitched()).isTrue();
        assertThat(result.getNewDate()).isEqualTo(targetDate);

        verifyNoInteractions(eodStatusDomainService);
        verify(accountingDateCache, never()).setCurrentDate(any());
    }

    @Test
    @DisplayName("切日修正：若新日期曾被错误置为8(归档完成)，应重置为1(未开始/营业中)")
    void testSwitchDate_ResetMistakenArchivedStatus() {
        LocalDate currentDate = LocalDate.of(2026, 10, 15);
        LocalDate targetDate = LocalDate.of(2026, 10, 16);

        EodStatusPO existing = new EodStatusPO();
        existing.setAccountingDate(targetDate);
        existing.setEodStatus(EodStatusEnum.COMPLETED);

        when(accountingDateCache.getCurrentDate()).thenReturn(currentDate);
        when(eodStatusRepository.findByDate(targetDate)).thenReturn(existing);

        AccountingDateSwitchDomainService.DateSwitchResult result =
                dateSwitchDomainService.switchDate(targetDate);

        assertThat(result.isAlreadySwitched()).isFalse();
        verify(eodStatusRepository).resetStatus(targetDate);
        verify(accountingDateCache).setCurrentDate(targetDate);
    }
}
