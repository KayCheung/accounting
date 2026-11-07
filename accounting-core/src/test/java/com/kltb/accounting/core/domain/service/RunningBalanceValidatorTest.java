package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * RunningBalanceValidator 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RunningBalanceValidatorTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubAccountRepository subAccountRepository;

    @Mock
    private BufferPostingDetailRepository bufferPostingDetailRepository;

    @Mock
    private AccountDetailMapper accountDetailMapper;

    @InjectMocks
    private RunningBalanceValidator runningBalanceValidator;

    private final String accountNo = "10010001";
    private final LocalDate accountingDate = LocalDate.of(2026, 10, 13);

    @Test
    @DisplayName("当日无已入账缓冲明细时跳过校验")
    void validateRunningBalance_noSuccessDetails_skip() {
        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(new AccountPO().setAccountNo(accountNo).setBalance(new BigDecimal("1000.00")));
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode()))
                .thenReturn(new SubAccountPO().setAccountNo(accountNo).setBalance(new BigDecimal("1000.00")));
        when(bufferPostingDetailRepository.selectSuccessByDate(eq(accountingDate), anyInt()))
                .thenReturn(List.of());

        RunningBalanceValidator.ValidationResult result = runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isAlert()).isFalse();
        assertThat(result.getMessage()).contains("跳过");
    }

    @Test
    @DisplayName("主子账户余额不一致时触发总分失衡告警")
    void validateRunningBalance_accountAndSubMismatch_alert() {
        // 主账户 1000，可用子账户 900 -> 总分失衡
        AccountPO account = new AccountPO().setAccountNo(accountNo).setBalance(new BigDecimal("1000.00"));
        SubAccountPO subAccount = new SubAccountPO().setAccountNo(accountNo).setBalanceType(BalanceTypeEnum.AVAILABLE).setBalance(new BigDecimal("900.00"));
        BufferPostingDetailPO detail = new BufferPostingDetailPO().setAccountNo(accountNo).setStatus(BufferStatusEnum.SUCCESS).setTradeTime(LocalDateTime.now());

        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(subAccount);
        when(bufferPostingDetailRepository.selectSuccessByDate(eq(accountingDate), anyInt())).thenReturn(List.of(detail));
        when(subAccountRepository.selectByAccountNo(accountNo)).thenReturn(List.of(subAccount));

        RunningBalanceValidator.ValidationResult result = runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isAlert()).isTrue();
        assertThat(result.getDiff()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("存在成功缓冲明细但缺少账户明细流水时告警")
    void validateRunningBalance_missingAccountDetail_alert() {
        AccountPO account = new AccountPO().setAccountNo(accountNo).setBalance(new BigDecimal("900.00"));
        SubAccountPO subAccount = new SubAccountPO().setAccountNo(accountNo).setBalanceType(BalanceTypeEnum.AVAILABLE).setBalance(new BigDecimal("900.00"));
        BufferPostingDetailPO detail = new BufferPostingDetailPO().setAccountNo(accountNo).setStatus(BufferStatusEnum.SUCCESS).setTradeTime(LocalDateTime.now());

        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(subAccount);
        when(bufferPostingDetailRepository.selectSuccessByDate(eq(accountingDate), anyInt())).thenReturn(List.of(detail));
        when(subAccountRepository.selectByAccountNo(accountNo)).thenReturn(List.of(subAccount));
        when(accountDetailMapper.selectLastPostBalance(accountNo, accountingDate)).thenReturn(null);

        RunningBalanceValidator.ValidationResult result = runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);

        assertThat(result.isPassed()).isFalse();
        assertThat(result.isAlert()).isTrue();
        assertThat(result.getDiff()).isEqualByComparingTo(new BigDecimal("900.00"));
    }

    @Test
    @DisplayName("实际余额与末条流水 postBalance 完全一致时通过校验（diff=0）")
    void validateRunningBalance_perfectMatch_pass() {
        AccountPO account = new AccountPO().setAccountNo(accountNo).setBalance(new BigDecimal("900.00"));
        SubAccountPO subAccount = new SubAccountPO().setAccountNo(accountNo).setBalanceType(BalanceTypeEnum.AVAILABLE).setBalance(new BigDecimal("900.00"));
        BufferPostingDetailPO detail = new BufferPostingDetailPO().setAccountNo(accountNo).setStatus(BufferStatusEnum.SUCCESS).setTradeTime(LocalDateTime.now());

        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(subAccount);
        when(bufferPostingDetailRepository.selectSuccessByDate(eq(accountingDate), anyInt())).thenReturn(List.of(detail));
        when(subAccountRepository.selectByAccountNo(accountNo)).thenReturn(List.of(subAccount));
        when(accountDetailMapper.selectLastPostBalance(accountNo, accountingDate)).thenReturn(new BigDecimal("900.00"));

        RunningBalanceValidator.ValidationResult result = runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.isAlert()).isFalse();
        assertThat(result.getDiff()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
