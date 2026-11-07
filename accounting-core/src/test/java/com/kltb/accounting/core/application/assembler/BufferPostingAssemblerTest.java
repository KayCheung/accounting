package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.BufferDetailResponse;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BufferPostingAssembler 单元测试")
class BufferPostingAssemblerTest {

    private final BufferPostingAssembler assembler = new BufferPostingAssembler();

    @Test
    @DisplayName("toDetailResponse: 1970-01-01默认时间清洗为null")
    void toDetailResponse_epochDateTime_shouldSanitizeToNull() {
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(1L);
        po.setVoucherNo("VOU1001");
        po.setEntryId("E1001");
        po.setStatus(BufferStatusEnum.PENDING);
        po.setStartTime(LocalDateTime.of(1970, 1, 1, 0, 0, 0));
        po.setCompleteTime(LocalDateTime.of(1970, 1, 1, 0, 0, 0));
        po.setAmount(new BigDecimal("100.00"));

        BufferDetailResponse resp = assembler.toDetailResponse(po);
        assertThat(resp.getStartTime()).isNull();
        assertThat(resp.getCompleteTime()).isNull();
    }

    @Test
    @DisplayName("toDetailResponse: 真实时间正常保留")
    void toDetailResponse_validDateTime_shouldPreserve() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 10, 30, 0);
        BufferPostingDetailPO po = new BufferPostingDetailPO();
        po.setId(2L);
        po.setVoucherNo("VOU1002");
        po.setEntryId("E1002");
        po.setStatus(BufferStatusEnum.SUCCESS);
        po.setStartTime(now);
        po.setCompleteTime(now.plusSeconds(2));
        po.setAmount(new BigDecimal("200.00"));

        BufferDetailResponse resp = assembler.toDetailResponse(po);
        assertThat(resp.getStartTime()).isEqualTo(now);
        assertThat(resp.getCompleteTime()).isEqualTo(now.plusSeconds(2));
    }
}
