package com.kltb.accounting.core.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kltb.accounting.api.request.JournalDetailRequest;
import com.kltb.accounting.api.request.JournalFreezeRequest;
import com.kltb.accounting.api.request.JournalSubmitRequest;
import com.kltb.accounting.api.request.JournalUnfreezeRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.service.JournalingApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class JournalingControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private JournalingApplicationService journalingApplicationService;

    @InjectMocks
    private JournalingController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private JournalDetailRequest buildDetail(BigDecimal amount) {
        JournalDetailRequest detail = new JournalDetailRequest();
        detail.setCustomerId("CUST_001");
        detail.setCustomerType(1);
        detail.setFundsType("PRINCIPAL");
        detail.setItemCode("ITEM_001");
        detail.setAmount(amount);
        return detail;
    }

    @Test
    @DisplayName("POST /accounting/journal/submit: 提交记账流水成功")
    void submitJournal_shouldSucceed() throws Exception {
        JournalSubmitRequest request = new JournalSubmitRequest();
        request.setTraceNo("TRACE_CTRL_001");
        request.setTraceSeq(0);
        request.setBusinessCode("LOAN");
        request.setTradingCode("DISBURSE");
        request.setPayChannel("BANK");
        request.setTradeType(1);
        request.setAmount(new BigDecimal("100.00"));
        request.setTradeTime(LocalDateTime.now());
        request.setSummary("放款流水提交");
        request.setDetails(List.of(buildDetail(new BigDecimal("100.00"))));

        JournalSubmitResponse resp = new JournalSubmitResponse();
        resp.setTraceNo("TRACE_CTRL_001");
        resp.setTxnNo("TXN_CTRL_001");
        resp.setAccountingDate(LocalDate.now());

        when(journalingApplicationService.submitJournal(any())).thenReturn(resp);

        mockMvc.perform(post("/accounting/journal/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.traceNo").value("TRACE_CTRL_001"))
                .andExpect(jsonPath("$.data.txnNo").value("TXN_CTRL_001"));

        verify(journalingApplicationService, times(1)).submitJournal(any());
    }

    @Test
    @DisplayName("POST /accounting/journal/freeze: 业务预冻结成功")
    void freezeJournal_shouldSucceed() throws Exception {
        JournalFreezeRequest request = new JournalFreezeRequest();
        request.setTraceNo("FRZ_CTRL_001");
        request.setTraceSeq(0);
        request.setBusinessCode("RETAIL");
        request.setTradingCode("AUTH_PAY");
        request.setPayChannel("ALIPAY");
        request.setAmount(new BigDecimal("200.00"));
        request.setTradeTime(LocalDateTime.now());
        request.setSummary("预授权资金冻结");
        request.setDetails(List.of(buildDetail(new BigDecimal("200.00"))));

        JournalFreezeResponse resp = new JournalFreezeResponse();
        resp.setTraceNo("FRZ_CTRL_001");
        resp.setFreezeId("FRZ_ID_001");
        resp.setAccountNo("ACCT_FRZ_001");
        resp.setFreezeAmount(new BigDecimal("200.00"));

        when(journalingApplicationService.freezeJournal(any())).thenReturn(resp);

        mockMvc.perform(post("/accounting/journal/freeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.traceNo").value("FRZ_CTRL_001"))
                .andExpect(jsonPath("$.data.freezeId").value("FRZ_ID_001"));

        verify(journalingApplicationService, times(1)).freezeJournal(any());
    }

    @Test
    @DisplayName("POST /accounting/journal/unfreeze: 业务预冻结解冻成功")
    void unfreezeJournal_shouldSucceed() throws Exception {
        JournalUnfreezeRequest request = new JournalUnfreezeRequest();
        request.setTraceNo("UFZ_CTRL_001");
        request.setTraceSeq(0);
        request.setOrigTraceNo("FRZ_CTRL_001");
        request.setReason("业务撤单解冻");

        JournalUnfreezeResponse resp = new JournalUnfreezeResponse();
        resp.setTraceNo("UFZ_CTRL_001");
        resp.setOrigTraceNo("FRZ_CTRL_001");
        resp.setFreezeId("FRZ_ID_001");
        resp.setUnfreezeAmount(new BigDecimal("200.00"));

        when(journalingApplicationService.unfreezeJournal(any())).thenReturn(resp);

        mockMvc.perform(post("/accounting/journal/unfreeze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.origTraceNo").value("FRZ_CTRL_001"))
                .andExpect(jsonPath("$.data.unfreezeAmount").value(200.00));

        verify(journalingApplicationService, times(1)).unfreezeJournal(any());
    }

    @Test
    @DisplayName("GET /accounting/journal/{traceNo}: 查询流水精简处理结果成功")
    void getJournal_shouldSucceed() throws Exception {
        JournalQueryResponse resp = new JournalQueryResponse();
        resp.setTraceNo("TRACE_VIEW_001");
        resp.setTxnNo("TXN_VIEW_001");
        resp.setStatus(2);
        resp.setStatusDesc("成功");

        when(journalingApplicationService.getJournal("TRACE_VIEW_001")).thenReturn(resp);

        mockMvc.perform(get("/accounting/journal/TRACE_VIEW_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.traceNo").value("TRACE_VIEW_001"))
                .andExpect(jsonPath("$.data.txnNo").value("TXN_VIEW_001"))
                .andExpect(jsonPath("$.data.status").value(2));

        verify(journalingApplicationService, times(1)).getJournal("TRACE_VIEW_001");
    }

    @Test
    @DisplayName("GET /accounting/journal/{traceNo}/overview: 查询流水全流程总览成功")
    void getJournalOverview_shouldSucceed() throws Exception {
        JournalOverviewResponse resp = new JournalOverviewResponse();
        JournalOverviewResponse.RecordInfo record = new JournalOverviewResponse.RecordInfo();
        record.setTraceNo("TRACE_VIEW_001");
        record.setStatus(2);
        record.setStatusDesc("成功");
        resp.setRecord(record);
        resp.setProcessStage("SUCCESS");
        resp.setProgressPercent(100);

        when(journalingApplicationService.getJournalOverview("TRACE_VIEW_001")).thenReturn(resp);

        mockMvc.perform(get("/accounting/journal/TRACE_VIEW_001/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.record.traceNo").value("TRACE_VIEW_001"))
                .andExpect(jsonPath("$.data.processStage").value("SUCCESS"))
                .andExpect(jsonPath("$.data.progressPercent").value(100));

        verify(journalingApplicationService, times(1)).getJournalOverview("TRACE_VIEW_001");
    }

    @Test
    @DisplayName("GET /accounting/journal/trace/{traceNo}/transaction: 查询关联事务状态成功")
    void getTransactionStatus_shouldSucceed() throws Exception {
        TransactionStatusResponse resp = new TransactionStatusResponse();
        resp.setTxnNo("TXN_VIEW_001");
        resp.setStatus(2);
        resp.setStatusDesc("成功");

        when(journalingApplicationService.getTransactionStatus("TRACE_VIEW_001")).thenReturn(resp);

        mockMvc.perform(get("/accounting/journal/trace/TRACE_VIEW_001/transaction"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.txnNo").value("TXN_VIEW_001"));

        verify(journalingApplicationService, times(1)).getTransactionStatus("TRACE_VIEW_001");
    }

    @Test
    @DisplayName("POST /accounting/journal/{traceNo}/retry: 重试记账成功")
    void retryJournal_shouldSucceed() throws Exception {
        JournalOverviewResponse resp = new JournalOverviewResponse();
        resp.setProcessStage("SUCCESS");
        when(journalingApplicationService.retryJournal("TRACE_FAIL_001")).thenReturn(resp);

        mockMvc.perform(post("/accounting/journal/TRACE_FAIL_001/retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processStage").value("SUCCESS"));

        verify(journalingApplicationService, times(1)).retryJournal("TRACE_FAIL_001");
    }

    @Test
    @DisplayName("POST /accounting/journal/{traceNo}/rollback: 回滚记账成功")
    void rollbackJournal_shouldSucceed() throws Exception {
        JournalOverviewResponse resp = new JournalOverviewResponse();
        resp.setProcessStage("FAILED");
        when(journalingApplicationService.rollbackJournal(eq("TRACE_FAIL_001"), anyString())).thenReturn(resp);

        mockMvc.perform(post("/accounting/journal/TRACE_FAIL_001/rollback?reason=MANUAL_TEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.processStage").value("FAILED"));

        verify(journalingApplicationService, times(1)).rollbackJournal(eq("TRACE_FAIL_001"), eq("MANUAL_TEST"));
    }

    @Test
    @DisplayName("GET /accounting/journal/page: 分页查询业务记账流水记录成功")
    void getJournalPage_shouldSucceed() throws Exception {
        JournalRecordItemResponse item = JournalRecordItemResponse.builder()
                .traceNo("TRC001")
                .businessCode("LOAN")
                .amount(new BigDecimal("100.00"))
                .status(2)
                .statusDesc("成功")
                .build();
        PageResponse<JournalRecordItemResponse> pageResp = PageResponse.<JournalRecordItemResponse>builder()
                .current(1L)
                .pages(1L)
                .total(1L)
                .list(List.of(item))
                .build();

        when(journalingApplicationService.getJournalPage(any())).thenReturn(pageResp);

        mockMvc.perform(get("/accounting/journal/page?pageNo=1&pageSize=20&traceNo=TRC001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].traceNo").value("TRC001"));

        verify(journalingApplicationService, times(1)).getJournalPage(any());
    }

    @Test
    @DisplayName("GET /accounting/journal/transaction/page: 分页查询账务事务记录成功")
    void getTransactionPage_shouldSucceed() throws Exception {
        TransactionRecordItemResponse item = TransactionRecordItemResponse.builder()
                .txnNo("TXN001")
                .traceNo("TRC001")
                .amount(new BigDecimal("100.00"))
                .status(2)
                .statusDesc("成功")
                .build();
        PageResponse<TransactionRecordItemResponse> pageResp = PageResponse.<TransactionRecordItemResponse>builder()
                .current(1L)
                .pages(1L)
                .total(1L)
                .list(List.of(item))
                .build();

        when(journalingApplicationService.getTransactionPage(any())).thenReturn(pageResp);

        mockMvc.perform(get("/accounting/journal/transaction/page?pageNo=1&pageSize=20&txnNo=TXN001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].txnNo").value("TXN001"));

        verify(journalingApplicationService, times(1)).getTransactionPage(any());
    }
}
