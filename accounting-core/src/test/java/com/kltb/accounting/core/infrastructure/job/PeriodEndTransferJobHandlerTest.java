package com.kltb.accounting.core.infrastructure.job;

import com.kltb.accounting.core.domain.enums.TransferRecordStatusEnum;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService;
import com.kltb.accounting.core.domain.service.PeriodEndTransferDomainService.TransferRuleResult;
import com.kltb.accounting.core.infrastructure.cache.AccountingDateCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * PeriodEndTransferJobHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class PeriodEndTransferJobHandlerTest {

    @Mock
    private PeriodEndTransferDomainService transferDomainService;

    @Mock
    private AccountingDateCache accountingDateCache;

    @Test
    @DisplayName("未传参数时：优先使用 AccountingDateCache 全局权威会计日期执行自动结转")
    void execute_defaultDateFromAccountingDateCache() {
        LocalDate cachedDate = LocalDate.of(2026, 10, 31);
        when(accountingDateCache.getCurrentDate()).thenReturn(cachedDate);

        TransferRuleResult successResult = new TransferRuleResult(
                "TR_REV_GUARANTEE", "担保费收入结转", "TRN001", "PET001",
                new BigDecimal("100.00"), TransferRecordStatusEnum.SUCCESS, null);
        when(transferDomainService.executeAutoTransfer(cachedDate)).thenReturn(List.of(successResult));

        PeriodEndTransferJobHandler handler = new PeriodEndTransferJobHandler(transferDomainService, accountingDateCache);
        handler.execute();

        verify(transferDomainService).executeAutoTransfer(cachedDate);
    }

    @Test
    @DisplayName("传入有效日期参数时：优先使用参数指定的会计日期")
    void execute_customParamOverridesDefaultDate() {
        LocalDate paramDate = LocalDate.of(2026, 9, 30);
        when(accountingDateCache.getCurrentDate()).thenReturn(LocalDate.of(2026, 10, 31));

        TransferRuleResult successResult = new TransferRuleResult(
                "TR_REV_INTEREST", "利息收入结转", "TRN002", "PET002",
                new BigDecimal("200.00"), TransferRecordStatusEnum.SUCCESS, null);
        when(transferDomainService.executeAutoTransfer(paramDate)).thenReturn(List.of(successResult));

        PeriodEndTransferJobHandler handler = new PeriodEndTransferJobHandler(transferDomainService, accountingDateCache) {
            @Override
            protected LocalDate parseAccountingDate(String param, LocalDate defaultDate) {
                return paramDate;
            }
        };

        handler.execute();

        verify(transferDomainService).executeAutoTransfer(paramDate);
    }

    @Test
    @DisplayName("AccountingDateCache 异常时：防御性降级回退到系统当前日期，不中断调度")
    void execute_cacheExceptionFallbackToLocalDateNow() {
        when(accountingDateCache.getCurrentDate()).thenThrow(new RuntimeException("Redis connection refused"));

        when(transferDomainService.executeAutoTransfer(any())).thenReturn(List.of());

        PeriodEndTransferJobHandler handler = new PeriodEndTransferJobHandler(transferDomainService, accountingDateCache);
        handler.execute();

        // 验证即使缓存挂了，调度依然使用系统当天日期正常执行
        verify(transferDomainService).executeAutoTransfer(LocalDate.now());
    }
}
