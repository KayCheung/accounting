package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BufferPostingDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BufferPostingDomainServiceTest {

    @Mock
    private BufferPostingRuleMapper bufferPostingRuleMapper;

    @Mock
    private BufferPostingDetailMapper bufferPostingDetailMapper;

    @Mock
    private AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;

    @InjectMocks
    private BufferPostingDomainService bufferPostingDomainService;

    // ==================== matchBufferRule 测试 ====================

    @Test
    @DisplayName("匹配缓冲规则: 找到匹配规则 → 返回第一条")
    void matchBufferRule_found_shouldReturnFirstRule() {
        VoucherEntryData entryData = buildEntryData("ENT001", "1001", 1,
                new BigDecimal("100.00"));

        BufferPostingRulePO rule1 = buildBufferRule(1L, "RULE001");
        BufferPostingRulePO rule2 = buildBufferRule(2L, "RULE002");
        List<BufferPostingRulePO> rules = List.of(rule1, rule2);

        when(bufferPostingRuleMapper.selectMatchingRules(
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), any(LocalDate.class)))
                .thenReturn(rules);

        BufferPostingRulePO result = bufferPostingDomainService.matchBufferRule(
                entryData, "PAYMENT", "TRANSFER", "ALIPAY");

        assertThat(result).isNotNull();
        assertThat(result.getRuleName()).isEqualTo("RULE001");
        verify(bufferPostingRuleMapper).selectMatchingRules(
                eq("PAYMENT"), eq("TRANSFER"), eq("ALIPAY"),
                eq("1001"), anyString(), eq(1), any(LocalDate.class));
    }

    @Test
    @DisplayName("匹配缓冲规则: 无匹配规则 → 返回 null")
    void matchBufferRule_notFound_shouldReturnNull() {
        VoucherEntryData entryData = buildEntryData("ENT002", "1001", 1,
                new BigDecimal("100.00"));

        when(bufferPostingRuleMapper.selectMatchingRules(
                anyString(), anyString(), anyString(),
                anyString(), anyString(), anyInt(), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        BufferPostingRulePO result = bufferPostingDomainService.matchBufferRule(
                entryData, "PAYMENT", "TRANSFER", "ALIPAY");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("匹配缓冲规则: 按 subjectCode 匹配 → 正确传递参数")
    void matchBufferRule_matchBySubjectCode_shouldPassCorrectParams() {
        VoucherEntryData entryData = buildEntryData("ENT003", "2001", 2,
                new BigDecimal("500.00"));
        entryData.setAccountNo("ACC003");

        BufferPostingRulePO rule = buildBufferRule(3L, "SUBJECT_RULE");
        when(bufferPostingRuleMapper.selectMatchingRules(
                eq("PAYMENT"), eq("TRANSFER"), eq("ALIPAY"),
                eq("2001"), eq("ACC003"), eq(2), any(LocalDate.class)))
                .thenReturn(List.of(rule));

        BufferPostingRulePO result = bufferPostingDomainService.matchBufferRule(
                entryData, "PAYMENT", "TRANSFER", "ALIPAY");

        assertThat(result).isNotNull();
        assertThat(result.getRuleName()).isEqualTo("SUBJECT_RULE");
    }

    // ==================== calculateAuxiliaryAllocation 测试 ====================

    @Test
    @DisplayName("辅助核算分摊: 按比例分摊(2项) → 最后一条补差")
    void calculateAuxiliaryAllocation_proportional_lastOneCompensates() {
        VoucherEntryData entryData = buildEntryData("ENT001", "1001", 1,
                new BigDecimal("100.00"));

        // 60% + 40% = 100%
        AccountingRuleAuxiliaryPO aux1 = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("60"));
        AccountingRuleAuxiliaryPO aux2 = buildAuxiliaryConfig(2L, "DEPT002",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("40"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(aux1, aux2));

        assertThat(result).hasSize(2);

        // 第一条按 60% 计算
        BigDecimal aux1Amount = result.get(0).getAmount();
        // 第二条补差
        BigDecimal aux2Amount = result.get(1).getAmount();

        // 合计应等于分录金额
        assertThat(aux1Amount.add(aux2Amount)).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("辅助核算分摊: 按比例分摊(3项) → 前2条按比例、第3条补差")
    void calculateAuxiliaryAllocation_proportional_threeItems() {
        VoucherEntryData entryData = buildEntryData("ENT002", "1001", 1,
                new BigDecimal("300.00"));

        // 30% + 30% + 40% = 100%
        AccountingRuleAuxiliaryPO aux1 = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("30"));
        AccountingRuleAuxiliaryPO aux2 = buildAuxiliaryConfig(2L, "DEPT002",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("30"));
        AccountingRuleAuxiliaryPO aux3 = buildAuxiliaryConfig(3L, "DEPT003",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("40"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(aux1, aux2, aux3));

        assertThat(result).hasSize(3);

        // 前两条按比例
        assertThat(result.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
        assertThat(result.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("90.00"));

        // 合计应等于分录金额
        BigDecimal total = result.stream()
                .map(AuxiliaryItemData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(new BigDecimal("300.00"));
    }

    @Test
    @DisplayName("辅助核算分摊: 固定金额分摊 → 各分配固定值")
    void calculateAuxiliaryAllocation_fixedAmount() {
        VoucherEntryData entryData = buildEntryData("ENT003", "1001", 1,
                new BigDecimal("100.00"));

        AccountingRuleAuxiliaryPO aux1 = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("30.00"));
        AccountingRuleAuxiliaryPO aux2 = buildAuxiliaryConfig(2L, "DEPT002",
                AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("70.00"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(aux1, aux2));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(result.get(1).getAmount()).isEqualByComparingTo(new BigDecimal("70.00"));
    }

    @Test
    @DisplayName("辅助核算分摊: 固定金额超过分录金额 → 抛出 AUXILIARY_AMOUNT_MISMATCH")
    void calculateAuxiliaryAllocation_fixedAmountExceeds_shouldThrow() {
        VoucherEntryData entryData = buildEntryData("ENT004", "1001", 1,
                new BigDecimal("50.00"));

        AccountingRuleAuxiliaryPO aux = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("100.00"));

        assertThatThrownBy(() -> bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(aux)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.AUXILIARY_AMOUNT_MISMATCH);
                });
    }

    @Test
    @DisplayName("辅助核算分摊: 固定金额为零 → 跳过该项")
    void calculateAuxiliaryAllocation_fixedAmountZero_shouldSkip() {
        VoucherEntryData entryData = buildEntryData("ENT005", "1001", 1,
                new BigDecimal("100.00"));

        AccountingRuleAuxiliaryPO auxValid = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.FIXED_AMOUNT, BigDecimal.ZERO);
        AccountingRuleAuxiliaryPO auxNull = buildAuxiliaryConfig(2L, "DEPT002",
                AllocationMethodEnum.FIXED_AMOUNT, null);
        AccountingRuleAuxiliaryPO auxNeg = buildAuxiliaryConfig(3L, "DEPT003",
                AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("-10.00"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(auxValid, auxNull, auxNeg));

        // 所有固定金额配置都应被跳过
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("辅助核算分摊: 混合固定+比例 → 固定先扣、剩余按比例")
    void calculateAuxiliaryAllocation_mixedFixedAndProportional() {
        VoucherEntryData entryData = buildEntryData("ENT006", "1001", 1,
                new BigDecimal("200.00"));

        // 固定金额 50，剩余 150 按比例 100% 分摊
        AccountingRuleAuxiliaryPO auxFixed = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.FIXED_AMOUNT, new BigDecimal("50.00"));
        AccountingRuleAuxiliaryPO auxProp = buildAuxiliaryConfig(2L, "DEPT002",
                AllocationMethodEnum.PERCENTAGE, new BigDecimal("100"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(auxFixed, auxProp));

        assertThat(result).hasSize(2);

        BigDecimal total = result.stream()
                .map(AuxiliaryItemData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(new BigDecimal("200.00"));
    }

    @Test
    @DisplayName("辅助核算分摊: 不分摊 → 返回空列表")
    void calculateAuxiliaryAllocation_none_shouldReturnEmpty() {
        VoucherEntryData entryData = buildEntryData("ENT007", "1001", 1,
                new BigDecimal("100.00"));

        AccountingRuleAuxiliaryPO aux = buildAuxiliaryConfig(1L, "DEPT001",
                AllocationMethodEnum.NONE, BigDecimal.ZERO);

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, List.of(aux));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("辅助核算分摊: 配置列表为空 → 返回空列表")
    void calculateAuxiliaryAllocation_emptyConfig_shouldReturnEmpty() {
        VoucherEntryData entryData = buildEntryData("ENT008", "1001", 1,
                new BigDecimal("100.00"));

        List<AuxiliaryItemData> result = bufferPostingDomainService.calculateAuxiliaryAllocation(
                entryData, Collections.emptyList());

        assertThat(result).isEmpty();
    }

    // ==================== calculateSharding 测试 ====================

    @Test
    @DisplayName("计算分片值: 相同 accountNo → 返回相同哈希值")
    void calculateSharding_sameAccountNo_shouldReturnSameHash() {
        Long hash1 = bufferPostingDomainService.calculateSharding("ACC001");
        Long hash2 = bufferPostingDomainService.calculateSharding("ACC001");

        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).isNotNull();
        assertThat(hash1).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("计算分片值: 不同 accountNo → 通常返回不同哈希值")
    void calculateSharding_differentAccountNo_shouldReturnDifferentHash() {
        Long hash1 = bufferPostingDomainService.calculateSharding("ACC001");
        Long hash2 = bufferPostingDomainService.calculateSharding("ACC002");

        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    @DisplayName("计算分片值: null accountNo → 返回 0")
    void calculateSharding_nullAccountNo_shouldReturnZero() {
        Long result = bufferPostingDomainService.calculateSharding(null);
        assertThat(result).isEqualTo(0L);
    }

    @Test
    @DisplayName("计算分片值: 空字符串 accountNo → 返回 0")
    void calculateSharding_emptyAccountNo_shouldReturnZero() {
        Long result = bufferPostingDomainService.calculateSharding("");
        assertThat(result).isEqualTo(0L);
    }

    // ==================== persistAuxiliaryItems 测试 ====================

    @Test
    @DisplayName("持久化辅助核算项: null 列表 → 不抛异常、不调用 insert")
    void persistAuxiliaryItems_nullList_shouldDoNothing() {
        bufferPostingDomainService.persistAuxiliaryItems(null);
        verifyNoInteractions(voucherAuxiliaryMapper);
    }

    @Test
    @DisplayName("持久化辅助核算项: 空列表 → 不抛异常、不调用 insert")
    void persistAuxiliaryItems_emptyList_shouldDoNothing() {
        bufferPostingDomainService.persistAuxiliaryItems(Collections.emptyList());
        verifyNoInteractions(voucherAuxiliaryMapper);
    }

    @Test
    @DisplayName("持久化辅助核算项: 有数据 → 逐条 insert")
    void persistAuxiliaryItems_withItems_shouldInsertEach() {
        AuxiliaryItemData item1 = new AuxiliaryItemData(
                "ENT001", "VOU001", "1001", "DEPT", "DEPT001", "财务部",
                1, new BigDecimal("60.00"), LocalDate.now());
        AuxiliaryItemData item2 = new AuxiliaryItemData(
                "ENT001", "VOU001", "1001", "DEPT", "DEPT002", "人事部",
                1, new BigDecimal("40.00"), LocalDate.now());

        bufferPostingDomainService.persistAuxiliaryItems(List.of(item1, item2));

        verify(voucherAuxiliaryMapper, times(2)).insert(any(AccountingVoucherAuxiliaryPO.class));
    }

    // ==================== persistBufferPostingDetails 测试 ====================

    @Test
    @DisplayName("持久化缓冲记账明细: null 列表 → 不抛异常、不调用 insert")
    void persistBufferPostingDetails_nullList_shouldDoNothing() {
        bufferPostingDomainService.persistBufferPostingDetails(null);
        verifyNoInteractions(bufferPostingDetailMapper);
    }

    @Test
    @DisplayName("持久化缓冲记账明细: 空列表 → 不抛异常、不调用 insert")
    void persistBufferPostingDetails_emptyList_shouldDoNothing() {
        bufferPostingDomainService.persistBufferPostingDetails(Collections.emptyList());
        verifyNoInteractions(bufferPostingDetailMapper);
    }

    @Test
    @DisplayName("持久化缓冲记账明细: 有数据 → 逐条 insert")
    void persistBufferPostingDetails_withDetails_shouldInsertEach() {
        BufferPostingDetailData detail = new BufferPostingDetailData(
                1L, 1, "VOU001", "ENT001", "TXN001", "TR001", 1,
                "PAYMENT", "TRANSFER", "ALIPAY", 1, LocalDateTime.now(),
                "ACC001", 1, "CNY", new BigDecimal("100.00"),
                LocalDate.now(), "测试摘要", 12345L);

        bufferPostingDomainService.persistBufferPostingDetails(List.of(detail));

        verify(bufferPostingDetailMapper).insert(any(BufferPostingDetailPO.class));
    }

    // ==================== 辅助方法 ====================

    private VoucherEntryData buildEntryData(String entryId, String subjectCode, int debitCredit, BigDecimal amount) {
        return new VoucherEntryData(
                entryId, "VOU001", 1, subjectCode, "ACC001",
                debitCredit, amount, "CNY", "测试摘要",
                LocalDate.now(), false, false);
    }

    private BufferPostingRulePO buildBufferRule(Long id, String ruleName) {
        BufferPostingRulePO rule = new BufferPostingRulePO();
        rule.setId(id);
        rule.setRuleName(ruleName);
        rule.setBufferMode(BufferModeEnum.ASYNC_SINGLE);
        rule.setBusinessCode("PAYMENT");
        rule.setTradingCode("TRANSFER");
        rule.setPayChannel("ALIPAY");
        rule.setSubjectCode("1001");
        rule.setDebitCredit(DebitCreditEnum.DEBIT);
        rule.setStatus(RuleStatusEnum.ENABLED);
        return rule;
    }

    private AccountingRuleAuxiliaryPO buildAuxiliaryConfig(
            Long id, String auxCode, AllocationMethodEnum method, BigDecimal allocationValue) {
        AccountingRuleAuxiliaryPO config = new AccountingRuleAuxiliaryPO();
        config.setId(id);
        config.setRuleId(1L);
        config.setRuleDetailId(1L);
        config.setAuxType("DEPT");
        config.setAuxCode(auxCode);
        config.setAllocationMethod(method);
        config.setAllocationValue(allocationValue);
        return config;
    }
}
