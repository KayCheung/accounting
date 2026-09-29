package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kltb.accounting.api.request.FreezeDeductRequest;
import com.kltb.accounting.api.request.FreezePageQueryRequest;
import com.kltb.accounting.api.request.FundFreezeRequest;
import com.kltb.accounting.api.request.FundUnfreezeRequest;
import com.kltb.accounting.api.response.FreezeDetailResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.assembler.FreezeAssembler;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.domain.service.FreezeDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FreezeApplicationServiceTest {

    @Mock
    private FreezeDomainService freezeDomainService;

    @Mock
    private FreezeDetailRepository freezeDetailRepository;

    @Spy
    private FreezeAssembler freezeAssembler = new FreezeAssembler();

    @InjectMocks
    private FreezeApplicationService service;

    @Test
    @DisplayName("freezeFund: 资金冻结成功并返回 DTO")
    void freezeFund_shouldReturnFreezeDetail() {
        FundFreezeRequest req = new FundFreezeRequest();
        req.setAccountNo("0012026030100001");
        req.setFreezeAmount(new BigDecimal("1000.00"));
        req.setExpireTime(LocalDateTime.of(2026, 12, 31, 23, 59, 59));
        req.setReason("风控临时冻结");

        AccountFreezeDetailPO po = new AccountFreezeDetailPO();
        po.setId(1L);
        po.setVoucherNo("FRZ20260301000001");
        po.setAccountNo("0012026030100001");
        po.setFreezeAmount(new BigDecimal("1000.00"));
        po.setStatus(FreezeStatusEnum.FROZEN);
        po.setExpireTime(req.getExpireTime());
        po.setSummary(req.getReason());
        po.setCreateTime(LocalDateTime.now());

        when(freezeDomainService.freezeFund(eq("0012026030100001"), eq(new BigDecimal("1000.00")), any(), eq("风控临时冻结")))
                .thenReturn(po);

        FreezeDetailResponse res = service.freezeFund(req);

        assertThat(res).isNotNull();
        assertThat(res.getFreezeId()).isEqualTo("FRZ20260301000001");
        assertThat(res.getAccountNo()).isEqualTo("0012026030100001");
        assertThat(res.getFreezeAmount()).isEqualByComparingTo("1000.00");
        assertThat(res.getStatusDesc()).isEqualTo("冻结");
    }

    @Test
    @DisplayName("unfreezeFund: 委托调用领域服务执行资金解冻")
    void unfreezeFund_shouldCallDomainService() {
        FundUnfreezeRequest req = new FundUnfreezeRequest();
        req.setFreezeId("FRZ20260301000001");
        req.setUnfreezeAmount(new BigDecimal("500.00"));
        req.setReason("部分解除");

        doNothing().when(freezeDomainService).unfreezeFund("FRZ20260301000001", new BigDecimal("500.00"), "部分解除");

        service.unfreezeFund(req);

        verify(freezeDomainService, times(1)).unfreezeFund("FRZ20260301000001", new BigDecimal("500.00"), "部分解除");
    }

    @Test
    @DisplayName("deductFromFreeze: 委托调用领域服务执行冻结扣款")
    void deductFromFreeze_shouldCallDomainService() {
        FreezeDeductRequest req = new FreezeDeductRequest();
        req.setFreezeId("FRZ20260301000001");
        req.setDeductAmount(new BigDecimal("300.00"));
        req.setReason("司法执行扣划");

        doNothing().when(freezeDomainService).deductFromFreeze("FRZ20260301000001", new BigDecimal("300.00"), "司法执行扣划");

        service.deductFromFreeze(req);

        verify(freezeDomainService, times(1)).deductFromFreeze("FRZ20260301000001", new BigDecimal("300.00"), "司法执行扣划");
    }

    @Test
    @DisplayName("queryFreezePage: 分页查询冻结明细列表")
    void queryFreezePage_shouldReturnPageResponse() {
        FreezePageQueryRequest req = new FreezePageQueryRequest();
        req.setAccountNo("0012026030100001");
        req.setPageNo(1);
        req.setPageSize(10);

        AccountFreezeDetailPO po = new AccountFreezeDetailPO();
        po.setId(2L);
        po.setVoucherNo("FRZ20260301000002");
        po.setAccountNo("0012026030100001");
        po.setFreezeAmount(new BigDecimal("200.00"));
        po.setStatus(FreezeStatusEnum.UNFROZEN);
        po.setCreateTime(LocalDateTime.now());
        po.setSummary("已解冻记录");

        Page<AccountFreezeDetailPO> repoPage = new Page<>(1, 10);
        repoPage.setRecords(List.of(po));
        repoPage.setTotal(1L);

        when(freezeDetailRepository.selectPage(any(), eq(1), eq(10))).thenReturn(repoPage);

        PageResponse<FreezeDetailResponse> res = service.queryFreezePage(req);

        assertThat(res).isNotNull();
        assertThat(res.getTotal()).isEqualTo(1L);
        assertThat(res.getList()).hasSize(1);
        FreezeDetailResponse item = res.getList().get(0);
        assertThat(item.getFreezeId()).isEqualTo("FRZ20260301000002");
        assertThat(item.getStatusDesc()).isEqualTo("已解冻");
    }
}
