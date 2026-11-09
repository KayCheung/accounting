package com.kltb.accounting.core.interfaces;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kltb.accounting.api.request.LocalMessagePageQueryRequest;
import com.kltb.accounting.api.request.LocalMessageReplayRequest;
import com.kltb.accounting.api.request.LocalMessageResetRequest;
import com.kltb.accounting.api.response.LocalMessageBatchOpResponse;
import com.kltb.accounting.api.response.LocalMessageResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.service.LocalMessageApplicationService;
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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * LocalMessageOpsController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class LocalMessageOpsControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private LocalMessageApplicationService localMessageApplicationService;

    @InjectMocks
    private LocalMessageOpsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("POST /accounting/ops/local-messages/page: 成功分页查询")
    void pageQuery_success() throws Exception {
        PageResponse<LocalMessageResponse> pageResponse = PageResponse.<LocalMessageResponse>builder()
                .current(1L)
                .pages(1L)
                .total(1L)
                .list(List.of(LocalMessageResponse.builder().messageId("MSG-001").status(3).build()))
                .build();

        when(localMessageApplicationService.pageQuery(any())).thenReturn(pageResponse);

        LocalMessagePageQueryRequest request = new LocalMessagePageQueryRequest();
        request.setStatus(3);

        mockMvc.perform(post("/accounting/ops/local-messages/page")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].messageId").value("MSG-001"));

        verify(localMessageApplicationService).pageQuery(any());
    }

    @Test
    @DisplayName("POST /accounting/ops/local-messages/replay: 成功触发手动重放")
    void replay_success() throws Exception {
        LocalMessageBatchOpResponse opResponse = LocalMessageBatchOpResponse.builder()
                .totalCount(2)
                .successCount(2)
                .failedCount(0)
                .failedMessageIds(Collections.emptyList())
                .build();

        when(localMessageApplicationService.replay(any())).thenReturn(opResponse);

        LocalMessageReplayRequest request = new LocalMessageReplayRequest();
        request.setMessageIds(List.of("MSG-001", "MSG-002"));

        mockMvc.perform(post("/accounting/ops/local-messages/replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(2));

        verify(localMessageApplicationService).replay(any());
    }

    @Test
    @DisplayName("POST /accounting/ops/local-messages/reset: 成功触发批量重置")
    void reset_success() throws Exception {
        LocalMessageBatchOpResponse opResponse = LocalMessageBatchOpResponse.builder()
                .totalCount(5)
                .successCount(5)
                .failedCount(0)
                .failedMessageIds(Collections.emptyList())
                .build();

        when(localMessageApplicationService.reset(any())).thenReturn(opResponse);

        LocalMessageResetRequest request = new LocalMessageResetRequest();
        request.setMessageIds(List.of("MSG-001"));

        mockMvc.perform(post("/accounting/ops/local-messages/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.totalCount").value(5));

        verify(localMessageApplicationService).reset(any());
    }
}
