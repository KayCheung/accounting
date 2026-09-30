package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.VoucherFullDetailResponse;
import com.kltb.accounting.api.response.VoucherPageItemResponse;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class VoucheringAssemblerTest {

    @Mock
    private DictionaryComponent dictionaryComponent;

    @Test
    @DisplayName("toPageItem 与 toFullDetail: 当 postTime 为 1970 默认时间时转换为 null")
    void testSanitizePostTime_EpochConvertsToNull() {
        VoucheringAssembler assembler = new VoucheringAssembler(dictionaryComponent);

        AccountingVoucherPO po = new AccountingVoucherPO();
        po.setId(1L);
        po.setVoucherNo("VOU20260930000001");
        po.setAmount(new BigDecimal("100.00"));
        po.setStatus(VoucherStatusEnum.PENDING);
        po.setAccountingDate(LocalDate.of(2026, 9, 30));
        po.setPostTime(com.kltb.accounting.api.constant.Constants.EPOCH_DATE_TIME); // DDL 默认值

        VoucherPageItemResponse pageItem = assembler.toPageItem(po, Collections.emptyList(), null, null);
        assertThat(pageItem.getPostTime()).isNull();

        VoucherFullDetailResponse fullDetail = assembler.toFullDetail(po, Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), null, false, null, null);
        assertThat(fullDetail.getPostTime()).isNull();
    }

    @Test
    @DisplayName("toPageItem: 正常过账时间应正常保留")
    void testSanitizePostTime_NormalTimeRetained() {
        VoucheringAssembler assembler = new VoucheringAssembler(dictionaryComponent);

        LocalDateTime now = LocalDateTime.of(2026, 9, 30, 15, 30, 0);
        AccountingVoucherPO po = new AccountingVoucherPO();
        po.setId(2L);
        po.setVoucherNo("VOU20260930000002");
        po.setAmount(new BigDecimal("200.00"));
        po.setStatus(VoucherStatusEnum.POSTED);
        po.setAccountingDate(LocalDate.of(2026, 9, 30));
        po.setPostTime(now);

        VoucherPageItemResponse pageItem = assembler.toPageItem(po, Collections.emptyList(), null, null);
        assertThat(pageItem.getPostTime()).isEqualTo(now);
    }
}
