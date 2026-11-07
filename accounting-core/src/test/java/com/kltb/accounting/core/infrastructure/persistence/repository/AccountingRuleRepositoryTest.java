package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingRuleMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingDetailMapper;
import com.kltb.accounting.core.infrastructure.redis.AccountingRuleCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountingRuleRepositoryTest {

    @Mock
    private AccountingRuleMapper ruleMapper;

    @Mock
    private AccountingRuleDetailMapper ruleDetailMapper;

    @Mock
    private AccountingRuleAuxiliaryMapper ruleAuxiliaryMapper;

    @Mock
    private BufferPostingDetailMapper bufferPostingDetailMapper;

    @Mock
    private AccountingRuleCacheService accountingRuleCacheService;

    @InjectMocks
    private AccountingRuleRepository ruleRepository;

    @Test
    @DisplayName("selectByBusinessKey: 直接委托给 accountingRuleCacheService 二级缓存服务")
    void testSelectByBusinessKey() {
        AccountingRulePO expected = new AccountingRulePO();
        expected.setId(100L);
        expected.setBusinessCode("LOAN");
        expected.setTradingCode("DISBURSE");
        expected.setPayChannel("ALIPAY");

        when(accountingRuleCacheService.getByBusinessKey("LOAN", "DISBURSE", "ALIPAY")).thenReturn(expected);

        AccountingRulePO actual = ruleRepository.selectByBusinessKey("LOAN", "DISBURSE", "ALIPAY");
        assertThat(actual).isNotNull();
        assertThat(actual.getId()).isEqualTo(100L);

        verify(accountingRuleCacheService).getByBusinessKey("LOAN", "DISBURSE", "ALIPAY");
        verifyNoInteractions(ruleMapper);
    }

    @Test
    @DisplayName("insertRule: 插入规则并主动失效对应二级缓存")
    void testInsertRule() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setBusinessCode("LOAN");
        rule.setTradingCode("DISBURSE");
        rule.setPayChannel("ALIPAY");

        ruleRepository.insertRule(rule);

        verify(ruleMapper).insert(rule);
        verify(accountingRuleCacheService).evictCache("LOAN", "DISBURSE", "ALIPAY");
    }

    @Test
    @DisplayName("updateRuleById: 更新成功后主动失效对应二级缓存")
    void testUpdateRuleById() {
        AccountingRulePO rule = new AccountingRulePO();
        rule.setId(100L);
        rule.setBusinessCode("LOAN");
        rule.setTradingCode("DISBURSE");
        rule.setPayChannel("ALIPAY");

        when(ruleMapper.updateById(rule)).thenReturn(1);

        boolean success = ruleRepository.updateRuleById(rule);
        assertThat(success).isTrue();

        verify(ruleMapper).updateById(rule);
        verify(accountingRuleCacheService).evictCache("LOAN", "DISBURSE", "ALIPAY");
    }
}
