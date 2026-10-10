package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingRuleMapper;
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

    @Mock
    private com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor ruleScriptExecutor;

    @InjectMocks
    private AuxiliaryDomainService auxiliaryDomainService;

    private BufferPostingDomainService bufferPostingDomainService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        bufferPostingDomainService = new BufferPostingDomainService(
                bufferPostingRuleMapper, bufferPostingDetailMapper, auxiliaryDomainService);
    }


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
