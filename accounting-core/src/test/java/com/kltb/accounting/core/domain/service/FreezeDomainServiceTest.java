package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.infrastructure.account.BusinessNoGenerator;
import com.kltb.accounting.core.infrastructure.account.FreezeIdGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubAccountRepository;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FreezeDomainServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private SubAccountRepository subAccountRepository;
    @Mock private FreezeDetailRepository freezeDetailRepository;
    @Mock private AccountDetailRepository accountDetailRepository;
    @Mock private SubAccountDetailRepository subAccountDetailRepository;
    @Mock private DistributedLockTemplate distributedLockTemplate;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private FreezeIdGenerator freezeIdGenerator;
    @Mock private BusinessNoGenerator businessNoGenerator;

    @InjectMocks private FreezeDomainService freezeDomainService;

    @Test
    @DisplayName("资金冻结: 账户不存在 -> 抛出 ACCOUNT_NOT_FOUND")
    void freezeFund_accountNotFound_shouldThrow() {
        when(accountRepository.selectByAccountNo("A001")).thenReturn(null);
        assertThatThrownBy(() -> freezeDomainService.freezeFund("A001", new BigDecimal("100"), null, "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("资金冻结: 账户状态非NORMAL -> 抛出 ACCOUNT_FROZEN_CANNOT_FREEZE")
    void freezeFund_accountNotNormal_shouldThrow() {
        AccountPO account = buildAccount("A001");
        account.setStatus(AccountStatusEnum.FROZEN);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        assertThatThrownBy(() -> freezeDomainService.freezeFund("A001", new BigDecimal("100"), null, "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.ACCOUNT_FROZEN_CANNOT_FREEZE);
                });
    }

    @Test
    @DisplayName("资金冻结: 可用余额不足 -> 抛出 FREEZE_AMOUNT_INVALID")
    void freezeFund_insufficientBalance_shouldThrow() {
        AccountPO account = buildAccount("A001");
        SubAccountPO availableSub = new SubAccountPO();
        availableSub.setBalance(new BigDecimal("50"));
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(availableSub);
        assertThatThrownBy(() -> freezeDomainService.freezeFund("A001", new BigDecimal("100"), null, "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.FREEZE_AMOUNT_INVALID);
                });
    }

    @Test
    @DisplayName("资金冻结: 金额<=0 -> 抛出 FREEZE_AMOUNT_INVALID")
    void freezeFund_invalidAmount_shouldThrow() {
        assertThatThrownBy(() -> freezeDomainService.freezeFund("A001", BigDecimal.ZERO, null, "test"))
                .isInstanceOf(AccountException.class);
        assertThatThrownBy(() -> freezeDomainService.freezeFund("A001", new BigDecimal("-100"), null, "test"))
                .isInstanceOf(AccountException.class);
    }

    @Test
    @DisplayName("资金冻结: 成功（未传业务线，默认使用 Constants.DEFAULT_BUSINESS_CODE）")
    void freezeFund_success_defaultBusinessCode() {
        AccountPO account = buildAccount("A001");
        SubAccountPO availableSub = new SubAccountPO().setBalance(new BigDecimal("500")).setVersion(1L);
        SubAccountPO frozenSub = new SubAccountPO().setBalance(new BigDecimal("200")).setVersion(1L);

        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(availableSub);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.FROZEN.getCode())).thenReturn(frozenSub);
        when(freezeIdGenerator.generate()).thenReturn("FRZ20261008000001");
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());

        AccountFreezeDetailPO result = freezeDomainService.freezeFund("A001", new BigDecimal("100"), null, "测试冻结");

        assertThat(result).isNotNull();
        assertThat(result.getBusinessCode()).isEqualTo(com.kltb.accounting.api.constant.Constants.DEFAULT_BUSINESS_CODE);
        assertThat(result.getTradingCode()).isEqualTo(com.kltb.accounting.api.constant.Constants.TRADING_CODE_FREEZE);
        assertThat(result.getFreezeAmount()).isEqualByComparingTo("100");
        verify(freezeDetailRepository).insert(any(AccountFreezeDetailPO.class));
    }

    @Test
    @DisplayName("资金冻结: 成功（传入自定义业务线）")
    void freezeFund_success_customBusinessCode() {
        AccountPO account = buildAccount("A001");
        SubAccountPO availableSub = new SubAccountPO().setBalance(new BigDecimal("500")).setVersion(1L);
        SubAccountPO frozenSub = new SubAccountPO().setBalance(new BigDecimal("200")).setVersion(1L);

        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(availableSub);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.FROZEN.getCode())).thenReturn(frozenSub);
        when(freezeIdGenerator.generate()).thenReturn("FRZ20261008000002");
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());

        AccountFreezeDetailPO result = freezeDomainService.freezeFund("A001", new BigDecimal("100"), "jiedianqian_cl", null, "测试自定义业务线冻结");

        assertThat(result).isNotNull();
        assertThat(result.getBusinessCode()).isEqualTo("jiedianqian_cl");
        assertThat(result.getTradingCode()).isEqualTo(com.kltb.accounting.api.constant.Constants.TRADING_CODE_FREEZE);
        assertThat(result.getFreezeAmount()).isEqualByComparingTo("100");
        verify(freezeDetailRepository).insert(any(AccountFreezeDetailPO.class));
    }

    @Test
    @DisplayName("资金解冻: 冻结记录不存在 -> 抛出 FREEZE_RECORD_NOT_FOUND")
    void unfreezeFund_recordNotFound_shouldThrow() {
        when(freezeDetailRepository.selectByVoucherNo("FRZ001")).thenReturn(null);
        assertThatThrownBy(() -> freezeDomainService.unfreezeFund("FRZ001", new BigDecimal("100"), "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.FREEZE_RECORD_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("资金解冻: 记录状态非冻结中 -> 抛出 FREEZE_STATUS_INVALID")
    void unfreezeFund_recordNotFrozen_shouldThrow() {
        AccountFreezeDetailPO record = new AccountFreezeDetailPO();
        record.setVoucherNo("FRZ001");
        record.setStatus(FreezeStatusEnum.UNFROZEN);
        record.setFreezeAmount(new BigDecimal("1000"));
        when(freezeDetailRepository.selectByVoucherNo("FRZ001")).thenReturn(record);
        assertThatThrownBy(() -> freezeDomainService.unfreezeFund("FRZ001", new BigDecimal("100"), "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.FREEZE_STATUS_INVALID);
                });
    }

    @Test
    @DisplayName("资金解冻: 解冻金额超过冻结金额 -> 抛出 FREEZE_AMOUNT_EXCEEDED")
    void unfreezeFund_amountExceeded_shouldThrow() {
        AccountFreezeDetailPO record = new AccountFreezeDetailPO();
        record.setVoucherNo("FRZ001");
        record.setStatus(FreezeStatusEnum.FROZEN);
        record.setFreezeAmount(new BigDecimal("500"));
        record.setAccountNo("A001");
        when(freezeDetailRepository.selectByVoucherNo("FRZ001")).thenReturn(record);
        assertThatThrownBy(() -> freezeDomainService.unfreezeFund("FRZ001", new BigDecimal("1000"), "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.FREEZE_AMOUNT_EXCEEDED);
                });
    }

    @Test
    @DisplayName("冻结扣款: 冻结记录不存在 -> 抛出 FREEZE_RECORD_NOT_FOUND")
    void deductFromFreeze_recordNotFound_shouldThrow() {
        when(freezeDetailRepository.selectByVoucherNo("FRZ001")).thenReturn(null);
        assertThatThrownBy(() -> freezeDomainService.deductFromFreeze("FRZ001", new BigDecimal("100"), "test"))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> {
                    AccountException e = (AccountException) ex;
                    assertThat(e.getResultCode()).isEqualTo(ResultCode.FREEZE_RECORD_NOT_FOUND);
                });
    }

    @Test
    @DisplayName("冻结扣款: 扣款金额>0 且<=冻结金额 金额合法")
    void deductFromFreeze_validAmount_shouldPassValidation() {
        AccountFreezeDetailPO record = new AccountFreezeDetailPO();
        record.setVoucherNo("FRZ001");
        record.setStatus(FreezeStatusEnum.FROZEN);
        record.setFreezeAmount(new BigDecimal("1000"));
        record.setAccountNo("A001");
        AccountPO account = buildAccount("A001");
        account.setBalance(new BigDecimal("5000"));
        SubAccountPO frozenSub = new SubAccountPO();
        frozenSub.setBalance(new BigDecimal("1000"));
        when(freezeDetailRepository.selectByVoucherNo("FRZ001")).thenReturn(record);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.FROZEN.getCode())).thenReturn(frozenSub);
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
        freezeDomainService.deductFromFreeze("FRZ001", new BigDecimal("500"), "test");
    }

    @Test
    @DisplayName("资金解冻: 支持部分解冻与第二次解冻剩余全部金额，不发生唯一键冲突")
    void unfreezeFund_partialAndFullSuccess_noDuplicateEntry() {
        // 初始冻结 100 元
        AccountFreezeDetailPO record = new AccountFreezeDetailPO();
        record.setVoucherNo("FRZ20261009000001");
        record.setStatus(FreezeStatusEnum.FROZEN);
        record.setFreezeAmount(new BigDecimal("100.00"));
        record.setAccountNo("A001");
        record.setVersion(0);

        AccountPO account = buildAccount("A001");
        SubAccountPO frozenSub = new SubAccountPO().setBalance(new BigDecimal("100.00")).setVersion(1L);
        SubAccountPO availableSub = new SubAccountPO().setBalance(new BigDecimal("500.00")).setVersion(1L);

        when(freezeDetailRepository.selectByVoucherNo("FRZ20261009000001")).thenReturn(record);
        when(accountRepository.selectByAccountNo("A001")).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.FROZEN.getCode())).thenReturn(frozenSub);
        when(subAccountRepository.selectByAccountNoAndType("A001", BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(availableSub);
        when(businessNoGenerator.generateEntryId("UFZ"))
                .thenReturn("UFZ202610090001")
                .thenReturn("UFZ202610090002")
                .thenReturn("UFZ202610090003")
                .thenReturn("UFZ202610090004");
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get())
                .when(distributedLockTemplate).execute(anyString(), anyLong(), anyLong(), any());
        doAnswer(invocation -> ((org.springframework.transaction.support.TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());

        // 第一次：部分解冻 40 元
        freezeDomainService.unfreezeFund("FRZ20261009000001", new BigDecimal("40.00"), "第一次部分解冻40");
        verify(freezeDetailRepository).updateAmountAndStatus(
                eq("FRZ20261009000001"),
                eq(new BigDecimal("60.00")),
                eq(FreezeStatusEnum.FROZEN),
                eq(0));

        // 模拟第一次解冻后数据库记录变化：剩余金额为 60.00，版本自增为 1
        record.setFreezeAmount(new BigDecimal("60.00"));
        record.setVersion(1);
        frozenSub.setBalance(new BigDecimal("60.00"));
        frozenSub.setVersion(2L);
        availableSub.setBalance(new BigDecimal("540.00"));
        availableSub.setVersion(2L);

        // 第二次：解冻剩余全部 60 元
        freezeDomainService.unfreezeFund("FRZ20261009000001", new BigDecimal("60.00"), "第二次解冻剩余60");
        verify(freezeDetailRepository).updateAmountAndStatus(
                eq("FRZ20261009000001"),
                eq(BigDecimal.ZERO),
                eq(FreezeStatusEnum.UNFROZEN),
                eq(1));

        // 验证写入了 4 条子账户明细（每次解冻 2 条），且由于 entryId 唯一绝不冲突
        verify(subAccountDetailRepository, times(4)).insert(any(SubAccountDetailPO.class));
    }

    @Test
    @DisplayName("查询冻结记录: 按账户查询返回列表")
    void queryFreezeRecords_byAccountNo_shouldReturnList() {
        when(freezeDetailRepository.selectByCondition(any())).thenReturn(List.of());
        List<AccountFreezeDetailPO> result = freezeDomainService.queryFreezeRecords("A001", null);
        assertThat(result).isEmpty();
    }

    // ==================== 辅助方法 ====================

    private AccountPO buildAccount(String accountNo) {
        AccountPO account = new AccountPO();
        account.setAccountNo(accountNo);
        account.setSubjectCode("1001");
        account.setStatus(AccountStatusEnum.NORMAL);
        account.setBalance(new BigDecimal("10000"));
        account.setVersion(0L);
        account.setCurrency("CNY");
        return account;
    }
}
