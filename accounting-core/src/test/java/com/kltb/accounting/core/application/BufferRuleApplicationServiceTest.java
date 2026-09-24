package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.BufferRuleCreateRequest;
import com.kltb.accounting.api.request.BufferRuleUpdateRequest;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingRuleRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * BufferRuleApplicationService 单元测试
 *
 * 测试覆盖：科目/账户非空校验、时间合法性校验、时间区间重叠校验
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BufferRuleApplicationServiceTest {

    @Mock
    private BufferPostingRuleRepository bufferPostingRuleRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BufferRuleApplicationService bufferRuleApplicationService;

    @Test
    @DisplayName("创建缓冲规则: 科目和账户都为空报 PARAM_ERROR")
    void create_bothEmpty_shouldThrow() {
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        BufferRuleCreateRequest request = new BufferRuleCreateRequest()
                .setRuleName("测试")
                .setBufferMode(1)
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setSubjectCode("")
                .setAccountNo("")
                .setDebitCredit(1)
                .setEffectiveTime(LocalDateTime.of(2026, 1, 1, 0, 0))
                .setExpirationTime(LocalDateTime.of(2099, 12, 31, 23, 59));

        assertThatThrownBy(() -> bufferRuleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("必须有一个不为空");
    }

    @Test
    @DisplayName("创建缓冲规则: 生效时间晚于失效时间报 PARAM_ERROR")
    void create_timeInvalid_shouldThrow() {
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        BufferRuleCreateRequest request = new BufferRuleCreateRequest()
                .setRuleName("测试")
                .setBufferMode(1)
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setSubjectCode("101001")
                .setAccountNo("")
                .setDebitCredit(1)
                .setEffectiveTime(LocalDateTime.of(2027, 1, 1, 0, 0))
                .setExpirationTime(LocalDateTime.of(2026, 1, 1, 0, 0));

        assertThatThrownBy(() -> bufferRuleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("生效时间不得晚于失效时间");
    }

    @Test
    @DisplayName("创建缓冲规则: 时间区间重叠报 OPERATION_NOT_ALLOWED")
    void create_timeOverlap_shouldThrow() {
        BufferPostingRulePO existing = new BufferPostingRulePO().setRuleName("已有规则");
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());
        when(bufferPostingRuleRepository.selectOverlappingRules(anyString(), anyString(), anyString(),
                any(), any(), isNull())).thenReturn(List.of(existing));

        BufferRuleCreateRequest request = new BufferRuleCreateRequest()
                .setRuleName("新规则")
                .setBufferMode(1)
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setSubjectCode("101001")
                .setAccountNo("")
                .setDebitCredit(1)
                .setEffectiveTime(LocalDateTime.of(2026, 6, 1, 0, 0))
                .setExpirationTime(LocalDateTime.of(2027, 6, 1, 0, 0));

        assertThatThrownBy(() -> bufferRuleApplicationService.create(request))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("时间区间重叠");
    }

    @Test
    @DisplayName("创建缓冲规则: 边界值——刚好相接不重叠")
    void create_adjacentTime_shouldSucceed() {
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());
        // 已有规则: 2026-01-01 ~ 2026-06-30
        // 新规则: 2026-06-30 ~ 2027-01-01 (刚好相接，不重叠)
        // 重叠判断: existing.effectiveTime < new.expirationTime AND existing.expirationTime > new.effectiveTime
        // 2026-01-01 < 2027-01-01 = true, 2026-06-30 > 2026-06-30 = false → 不重叠
        when(bufferPostingRuleRepository.selectOverlappingRules(
                eq("PAYMENT"), eq("CASH_PAY"), eq("CASH"),
                eq(LocalDateTime.of(2026, 6, 30, 0, 0)),
                eq(LocalDateTime.of(2027, 1, 1, 0, 0)),
                isNull())).thenReturn(List.of());

        BufferRuleCreateRequest request = new BufferRuleCreateRequest()
                .setRuleName("相接规则")
                .setBufferMode(1)
                .setBusinessCode("PAYMENT")
                .setTradingCode("CASH_PAY")
                .setPayChannel("CASH")
                .setSubjectCode("101001")
                .setAccountNo("")
                .setDebitCredit(1)
                .setEffectiveTime(LocalDateTime.of(2026, 6, 30, 0, 0))
                .setExpirationTime(LocalDateTime.of(2027, 1, 1, 0, 0));

        bufferRuleApplicationService.create(request);
        verify(bufferPostingRuleRepository).insert(any());
    }

    @Test
    @DisplayName("停用缓冲规则: 不存在报 DATA_NOT_FOUND")
    void disable_notFound_shouldThrow() {
        when(bufferPostingRuleRepository.selectById(999L)).thenReturn(null);
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> bufferRuleApplicationService.disable(999L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("缓冲规则不存在");
    }

    @Test
    @DisplayName("启用缓冲规则: 成功切换为 ENABLED 状态")
    void enable_success() {
        BufferPostingRulePO po = new BufferPostingRulePO();
        po.setId(101L);
        po.setRuleName("测试缓冲规则");
        po.setBusinessCode("PAYMENT");
        po.setTradingCode("CASH_PAY");
        po.setPayChannel("CASH");
        po.setStatus(RuleStatusEnum.DISABLED);
        po.setEffectiveTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        po.setExpirationTime(LocalDateTime.of(2026, 12, 31, 23, 59));

        when(bufferPostingRuleRepository.selectById(101L)).thenReturn(po);
        when(bufferPostingRuleRepository.selectOverlappingRules(any(), any(), any(), any(), any(), eq(101L)))
                .thenReturn(List.of());
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        bufferRuleApplicationService.enable(101L);

        verify(bufferPostingRuleRepository).updateById(argThat(p -> RuleStatusEnum.ENABLED.equals(p.getStatus())));
    }

    @Test
    @DisplayName("启用缓冲规则: 时间冲突报 OPERATION_NOT_ALLOWED")
    void enable_conflict_shouldThrow() {
        BufferPostingRulePO po = new BufferPostingRulePO();
        po.setId(102L);
        po.setRuleName("待启用规则");
        po.setBusinessCode("PAYMENT");
        po.setTradingCode("CASH_PAY");
        po.setPayChannel("CASH");
        po.setStatus(RuleStatusEnum.PENDING);
        po.setEffectiveTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        po.setExpirationTime(LocalDateTime.of(2026, 12, 31, 23, 59));

        BufferPostingRulePO existing = new BufferPostingRulePO();
        existing.setId(103L);
        existing.setRuleName("已存在的生效规则");

        when(bufferPostingRuleRepository.selectById(102L)).thenReturn(po);
        when(bufferPostingRuleRepository.selectOverlappingRules(any(), any(), any(), any(), any(), eq(102L)))
                .thenReturn(List.of(existing));
        doAnswer(invocation -> {
            ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null);
            return null;
        }).when(transactionTemplate).execute(any());

        assertThatThrownBy(() -> bufferRuleApplicationService.enable(102L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("存在时间区间重叠的缓冲规则");
    }
}
