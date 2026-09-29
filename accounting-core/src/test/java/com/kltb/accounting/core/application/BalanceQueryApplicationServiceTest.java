package com.kltb.accounting.core.application;

import com.kltb.accounting.api.request.AccountDetailQueryRequest;
import com.kltb.accounting.api.request.FreezeRecordQueryRequest;
import com.kltb.accounting.api.response.AccountDetailResponse;
import com.kltb.accounting.api.response.AggregateBalanceResponse;
import com.kltb.accounting.api.response.FreezeListResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.assembler.BalanceQueryAssembler;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService.AccountDetailPageResult;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService.AggregateBalanceDTO;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService.FreezeRecordPageResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceQueryApplicationServiceTest {

    @Mock
    private BalanceQueryDomainService balanceQueryDomainService;

    @Spy
    private BalanceQueryAssembler balanceQueryAssembler = new BalanceQueryAssembler();

    @InjectMocks
    private BalanceQueryApplicationService service;

    @Test
    @DisplayName("queryAggregateBalance: 查询聚合余额并正确组装")
    void queryAggregateBalance_shouldReturnAggregateResponse() {
        AggregateBalanceDTO dto = new AggregateBalanceDTO();
        dto.setAccountNo("0012026030100001");
        dto.setAccountName("张三-个人现金户");
        dto.setSubjectCode("100101");
        dto.setMainBalance(new BigDecimal("10000.00"));
        dto.setAvailableBalance(new BigDecimal("8000.00"));
        dto.setFrozenBalance(new BigDecimal("2000.00"));
        dto.setBufferEstimate(new BigDecimal("500.00"));
        dto.setTotalBalance(new BigDecimal("10000.00"));
        dto.setStatus(1);
        dto.setRiskStatus(1);
        dto.setBalanceDirection(1);
        dto.setCurrency("CNY");
        dto.setQueryTime(LocalDateTime.now());

        when(balanceQueryDomainService.queryAggregateBalance("0012026030100001")).thenReturn(dto);

        AggregateBalanceResponse res = service.queryAggregateBalance("0012026030100001");

        assertThat(res).isNotNull();
        assertThat(res.getAccountNo()).isEqualTo("0012026030100001");
        assertThat(res.getMainBalance()).isEqualByComparingTo("10000.00");
        assertThat(res.getAvailableBalance()).isEqualByComparingTo("8000.00");
        assertThat(res.getFrozenBalance()).isEqualByComparingTo("2000.00");
        assertThat(res.getBufferEstimate()).isEqualByComparingTo("500.00");
        assertThat(res.getTotalBalance()).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("queryAccountDetails: 查询账户变动明细并分页组装")
    void queryAccountDetails_shouldReturnPageResponse() {
        AccountDetailPO detail = new AccountDetailPO();
        detail.setId(1L);
        detail.setVoucherNo("VOU20260301001");
        detail.setEntryId("ENT20260301001");
        detail.setTxnNo("TXN20260301001");
        detail.setTraceNo("TRC20260301001");
        detail.setSubjectCode("100101");
        detail.setAccountNo("0012026030100001");
        detail.setTradeType(TradeTypeEnum.NORMAL);
        detail.setDebitCredit(DebitCreditEnum.DEBIT);
        detail.setChangeDirection(ChangeDirectionEnum.INCREASE);
        detail.setAmount(new BigDecimal("500.00"));
        detail.setPreBalance(new BigDecimal("1000.00"));
        detail.setPostBalance(new BigDecimal("1500.00"));
        detail.setAccountingDate(LocalDate.of(2026, 3, 1));
        detail.setTradeTime(LocalDateTime.of(2026, 3, 1, 10, 0, 0));
        detail.setSummary("存入现金");

        AccountDetailPageResult pageResult = new AccountDetailPageResult(1L, 1L, 1, List.of(detail));
        when(balanceQueryDomainService.queryAccountDetails(
                eq("0012026030100001"), any(), any(), any(), any(), eq(1), eq(10)
        )).thenReturn(pageResult);

        AccountDetailQueryRequest req = new AccountDetailQueryRequest();
        req.setAccountNo("0012026030100001");
        req.setPageNo(1);
        req.setPageSize(10);

        PageResponse<AccountDetailResponse> res = service.queryAccountDetails(req);

        assertThat(res).isNotNull();
        assertThat(res.getTotal()).isEqualTo(1L);
        assertThat(res.getList()).hasSize(1);
        AccountDetailResponse row = res.getList().get(0);
        assertThat(row.getVoucherNo()).isEqualTo("VOU20260301001");
        assertThat(row.getAmount()).isEqualByComparingTo("500.00");
        assertThat(row.getTradeTypeDesc()).isEqualTo("正常");
        assertThat(row.getDebitCreditDesc()).isEqualTo("借");
    }

    @Test
    @DisplayName("queryFreezeRecords: 查询资金冻结记录并分页组装")
    void queryFreezeRecords_shouldReturnFreezePageResponse() {
        AccountFreezeDetailPO freeze = new AccountFreezeDetailPO();
        freeze.setId(10L);
        freeze.setVoucherNo("FRZ20260301001");
        freeze.setAccountNo("0012026030100001");
        freeze.setFreezeAmount(new BigDecimal("2000.00"));
        freeze.setStatus(FreezeStatusEnum.FROZEN);
        freeze.setExpireTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        freeze.setTradeTime(LocalDateTime.of(2026, 3, 1, 10, 0, 0));
        freeze.setCreateTime(LocalDateTime.of(2026, 3, 1, 10, 0, 0));
        freeze.setSummary("司法协助冻结");

        FreezeRecordPageResult pageResult = new FreezeRecordPageResult(1L, 1L, 1, List.of(freeze));
        when(balanceQueryDomainService.queryFreezeRecords(
                eq("0012026030100001"), any(), eq(1), eq(10)
        )).thenReturn(pageResult);

        FreezeRecordQueryRequest req = new FreezeRecordQueryRequest();
        req.setAccountNo("0012026030100001");
        req.setPageNo(1);
        req.setPageSize(10);

        PageResponse<FreezeListResponse> res = service.queryFreezeRecords(req);

        assertThat(res).isNotNull();
        assertThat(res.getTotal()).isEqualTo(1L);
        assertThat(res.getList()).hasSize(1);
        FreezeListResponse row = res.getList().get(0);
        assertThat(row.getFreezeId()).isEqualTo("FRZ20260301001");
        assertThat(row.getFreezeAmount()).isEqualByComparingTo("2000.00");
        assertThat(row.getStatusDesc()).isEqualTo("冻结");
    }
}
