package com.kltb.accounting.core.interfaces;

import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.api.response.VoucherGenerateResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
import com.kltb.accounting.core.application.service.VoucheringApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoucheringControllerTest {

    private MockMvc mockMvc;

    @Mock
    private VoucheringApplicationService voucheringApplicationService;

    @InjectMocks
    private VoucheringController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /accounting/voucher/page: 路由精确命中分页接口，不被 /{voucherNo} 拦截")
    void queryVoucherPage_shouldHitPageEndpoint() throws Exception {
        PageResponse<VoucherPageItemResponse> pageResponse = PageResponse.<VoucherPageItemResponse>builder()
                .current(1L)
                .pages(1L)
                .total(0L)
                .list(Collections.emptyList())
                .build();

        when(voucheringApplicationService.queryVoucherPage(any())).thenReturn(pageResponse);

        mockMvc.perform(get("/accounting/voucher/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(0));

        // 验证 queryVoucherPage 被调用，而 getVoucherByNo 绝不能被调用
        verify(voucheringApplicationService, times(1)).queryVoucherPage(any());
        verify(voucheringApplicationService, never()).getVoucherByNo(any());
    }

    @Test
    @DisplayName("GET /accounting/voucher/{voucherNo}: 正常凭证号命中凭证查询")
    void getVoucher_validVoucherNo_shouldHitGetEndpoint() throws Exception {
        VoucherGenerateResponse res = new VoucherGenerateResponse(
                "VOU202609200001", "TRC001", "TXN001", "GENERAL",
                new BigDecimal("100.00"), LocalDate.now(), 3, null, "test",
                Collections.emptyList(), 0, 0
        );

        when(voucheringApplicationService.getVoucherByNo("VOU202609200001")).thenReturn(res);

        mockMvc.perform(get("/accounting/voucher/VOU202609200001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.voucherNo").value("VOU202609200001"));

        verify(voucheringApplicationService, times(1)).getVoucherByNo("VOU202609200001");
        verify(voucheringApplicationService, never()).queryVoucherPage(any());
    }
}
