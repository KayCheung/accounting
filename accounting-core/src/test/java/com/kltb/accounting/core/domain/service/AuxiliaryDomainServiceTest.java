package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.spel.JournalSpelContext;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuxiliaryDomainServiceTest {

    @Mock
    private AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;

    @Mock
    private RuleScriptExecutor ruleScriptExecutor;

    @InjectMocks
    private AuxiliaryDomainService auxiliaryDomainService;

    @Test
    @DisplayName("calculateAuxiliaryAllocation: 空配置返回空列表")
    void calculateAuxiliaryAllocation_emptyConfig_shouldReturnEmpty() {
        VoucherEntryData entry = buildEntryData("ENT001", "1001", new BigDecimal("100.00"));
        List<AuxiliaryItemData> result = auxiliaryDomainService.calculateAuxiliaryAllocation(entry, Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("calculateAuxiliaryAllocation: 固定金额分摊成功")
    void calculateAuxiliaryAllocation_fixedAmount_shouldSucceed() {
        VoucherEntryData entry = buildEntryData("ENT001", "1001", new BigDecimal("100.00"));
        AccountingRuleAuxiliaryPO aux1 = buildAuxConfig("PROJECT", "PRJ001", AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("40.00"));
        AccountingRuleAuxiliaryPO aux2 = buildAuxConfig("PROJECT", "PRJ002", AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("60.00"));

        List<AuxiliaryItemData> result = auxiliaryDomainService.calculateAuxiliaryAllocation(entry, List.of(aux1, aux2));
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("40.00");
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    @DisplayName("calculateAuxiliaryAllocation: 比例分摊并补齐尾差")
    void calculateAuxiliaryAllocation_proportional_shouldCompensateTail() {
        VoucherEntryData entry = buildEntryData("ENT001", "1001", new BigDecimal("100.00"));
        AccountingRuleAuxiliaryPO aux1 = buildAuxConfig("DEPT", "D01", AllocationMethodEnum.PERCENTAGE, new BigDecimal("33.333333"));
        AccountingRuleAuxiliaryPO aux2 = buildAuxConfig("DEPT", "D02", AllocationMethodEnum.PERCENTAGE, new BigDecimal("33.333333"));
        AccountingRuleAuxiliaryPO aux3 = buildAuxConfig("DEPT", "D03", AllocationMethodEnum.PERCENTAGE, new BigDecimal("33.333334"));

        List<AuxiliaryItemData> result = auxiliaryDomainService.calculateAuxiliaryAllocation(entry, List.of(aux1, aux2, aux3));
        assertThat(result).hasSize(3);
        BigDecimal total = result.stream().map(AuxiliaryItemData::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("calculateAuxiliaryAllocation: SpEL 动态分摊与动态编码解析")
    void calculateAuxiliaryAllocation_spelScript_shouldResolveDynamically() {
        VoucherEntryData entry = buildEntryData("ENT001", "1001", new BigDecimal("100.00"));
        AccountingRuleAuxiliaryPO auxSpel = buildAuxConfig("PARTNER", "#{ #extra['partnerCode'] }", AllocationMethodEnum.SPEL_SCRIPT, null);
        auxSpel.setExtendScript("#root.amount * 0.5");

        AccountingRuleAuxiliaryPO auxRest = buildAuxConfig("PARTNER", "INNER", AllocationMethodEnum.PERCENTAGE, new BigDecimal("100"));

        BusinessRecordPO journal = new BusinessRecordPO();
        journal.setExtraAttrs("{\"partnerCode\":\"PINGAN\"}");

        when(ruleScriptExecutor.execute(eq("#root.amount * 0.5"), any(JournalSpelContext.class)))
                .thenReturn(new BigDecimal("50.00"));
        when(ruleScriptExecutor.executeTemplate(eq("#{ #extra['partnerCode'] }"), any(JournalSpelContext.class)))
                .thenReturn("PINGAN");

        List<AuxiliaryItemData> result = auxiliaryDomainService.calculateAuxiliaryAllocation(
                entry, List.of(auxSpel, auxRest), null, journal);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAuxCode()).isEqualTo("PINGAN");
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("50.00");
        assertThat(result.get(1).getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("persistAuxiliaryItems: 成功持久化辅助核算项")
    void persistAuxiliaryItems_shouldInsertAll() {
        AuxiliaryItemData item = new AuxiliaryItemData(
                "ENT001", "VOU001", "1001", "DEPT", "D001", "技术部", 1, new BigDecimal("100.00"), LocalDate.now());

        auxiliaryDomainService.persistAuxiliaryItems(List.of(item));

        ArgumentCaptor<AccountingVoucherAuxiliaryPO> captor = ArgumentCaptor.forClass(AccountingVoucherAuxiliaryPO.class);
        verify(voucherAuxiliaryMapper).insert(captor.capture());
        assertThat(captor.getValue().getAuxCode()).isEqualTo("D001");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
    }

    private VoucherEntryData buildEntryData(String entryId, String subjectCode, BigDecimal amount) {
        return new VoucherEntryData(
                entryId, "VOU001", 1, subjectCode, "ACC001", 1, amount, "CNY", "测试分录", LocalDate.now(), false, false);
    }

    private AccountingRuleAuxiliaryPO buildAuxConfig(String auxType, String auxCode, AllocationMethodEnum method, BigDecimal value) {
        AccountingRuleAuxiliaryPO po = new AccountingRuleAuxiliaryPO();
        po.setId(10L);
        po.setRuleDetailId(100L);
        po.setAuxType(auxType);
        po.setAuxCode(auxCode);
        po.setAllocationMethod(method);
        po.setAllocationValue(value);
        return po;
    }
}
