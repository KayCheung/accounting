package com.kltb.accounting.core.infrastructure.persistence.repository;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceTypeEnum;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.SubAccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceSnapshotMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountFreezeDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAttachmentMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.SubAccountMapper;
import com.kltb.accounting.core.shared.exception.AccountException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 仓储层持久化操作受影响行数与异常阻断机制测试
 * 验证核心财务原则：不管是 update、insert 还是 delete 必须是真正的成功（受影响行数 > 0），严禁静默吞错
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PersistenceAffectedRowsEnforcementTest {

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private SubAccountMapper subAccountMapper;

    @Mock
    private AccountDetailMapper accountDetailMapper;

    @Mock
    private AccountFreezeDetailMapper accountFreezeDetailMapper;

    @Mock
    private SubAccountDetailMapper subAccountDetailMapper;

    @Mock
    private AccountingVoucherMapper voucherMapper;

    @Mock
    private AccountingVoucherEntryMapper entryMapper;

    @Mock
    private AccountingVoucherAuxiliaryMapper auxiliaryMapper;

    @Mock
    private AccountingVoucherAttachmentMapper attachmentMapper;

    @Mock
    private AccountBalanceMapper accountBalanceMapper;

    @Mock
    private AccountBalanceSnapshotMapper accountBalanceSnapshotMapper;

    @InjectMocks
    private AccountRepository accountRepository;

    @InjectMocks
    private SubAccountRepository subAccountRepository;

    @InjectMocks
    private AccountDetailRepository accountDetailRepository;

    @InjectMocks
    private SubAccountDetailRepository subAccountDetailRepository;

    @InjectMocks
    private AccountingVoucherRepository accountingVoucherRepository;

    @InjectMocks
    private FreezeDetailRepository freezeDetailRepository;

    @InjectMocks
    private AccountBalanceRepository accountBalanceRepository;

    @InjectMocks
    private AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;

    // ==================== AccountRepository ====================

    @Test
    @DisplayName("AccountRepository.updateById: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void accountRepository_updateById_zeroAffected_throwsOptimisticLockFailed() {
        AccountPO account = new AccountPO().setAccountNo("ACC001").setBalance(new BigDecimal("100.00"));
        when(accountMapper.updateById(account)).thenReturn(0);

        assertThatThrownBy(() -> accountRepository.updateById(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("AccountRepository.updateById: 受影响行数大于0时成功返回true")
    void accountRepository_updateById_success() {
        AccountPO account = new AccountPO().setAccountNo("ACC001").setBalance(new BigDecimal("100.00"));
        when(accountMapper.updateById(account)).thenReturn(1);

        boolean result = accountRepository.updateById(account);
        assertThat(result).isTrue();
        verify(accountMapper).updateById(account);
    }

    @Test
    @DisplayName("AccountRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void accountRepository_insert_zeroAffected_throwsSystemError() {
        AccountPO account = new AccountPO().setAccountNo("ACC001");
        when(accountMapper.insert(account)).thenReturn(0);

        assertThatThrownBy(() -> accountRepository.insert(account))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    // ==================== SubAccountRepository ====================

    @Test
    @DisplayName("SubAccountRepository.updateById: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void subAccountRepository_updateById_zeroAffected_throwsOptimisticLockFailed() {
        SubAccountPO sub = new SubAccountPO().setAccountNo("ACC001").setBalanceType(BalanceTypeEnum.AVAILABLE);
        when(subAccountMapper.updateById(sub)).thenReturn(0);

        assertThatThrownBy(() -> subAccountRepository.updateById(sub))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("SubAccountRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void subAccountRepository_insert_zeroAffected_throwsSystemError() {
        SubAccountPO sub = new SubAccountPO().setAccountNo("ACC001").setBalanceType(BalanceTypeEnum.AVAILABLE);
        when(subAccountMapper.insert(sub)).thenReturn(0);

        assertThatThrownBy(() -> subAccountRepository.insert(sub))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    // ==================== AccountDetailRepository ====================

    @Test
    @DisplayName("AccountDetailRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void accountDetailRepository_insert_zeroAffected_throwsSystemError() {
        AccountDetailPO detail = new AccountDetailPO().setEntryId("ENTRY001");
        when(accountDetailMapper.insert(detail)).thenReturn(0);

        assertThatThrownBy(() -> accountDetailRepository.insert(detail))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    @Test
    @DisplayName("AccountDetailRepository.batchInsert: 批量写入中遇到受影响行数为0时抛出 SYSTEM_ERROR")
    void accountDetailRepository_batchInsert_zeroAffected_throwsSystemError() {
        AccountDetailPO d1 = new AccountDetailPO().setEntryId("E1");
        AccountDetailPO d2 = new AccountDetailPO().setEntryId("E2");
        when(accountDetailMapper.insert(d1)).thenReturn(1);
        when(accountDetailMapper.insert(d2)).thenReturn(0);

        assertThatThrownBy(() -> accountDetailRepository.batchInsert(List.of(d1, d2)))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    @Test
    @DisplayName("AccountDetailRepository.insert: 必填字段为null时防御性补全兜底默认值")
    void accountDetailRepository_insert_defensiveNullFilling() {
        AccountDetailPO detail = new AccountDetailPO().setVoucherNo("VOU123");
        when(accountDetailMapper.insert(any(AccountDetailPO.class))).thenReturn(1);

        accountDetailRepository.insert(detail);

        assertThat(detail.getTxnNo()).isEqualTo("");
        assertThat(detail.getTraceNo()).isEqualTo("VOU123");
        assertThat(detail.getTraceSeq()).isEqualTo(1);
        assertThat(detail.getTradingCode()).isEqualTo("");
        assertThat(detail.getBusinessCode()).isEqualTo("");
        assertThat(detail.getPayChannel()).isEqualTo("");
        verify(accountDetailMapper).insert(detail);
    }

    // ==================== SubAccountDetailRepository ====================

    @Test
    @DisplayName("SubAccountDetailRepository.insert: 必填字段为null时防御性补全兜底默认值")
    void subAccountDetailRepository_insert_defensiveNullFilling() {
        SubAccountDetailPO detail = new SubAccountDetailPO().setVoucherNo("VOU456");
        when(subAccountDetailMapper.insert(any(SubAccountDetailPO.class))).thenReturn(1);

        subAccountDetailRepository.insert(detail);

        assertThat(detail.getTxnNo()).isEqualTo("");
        assertThat(detail.getTraceNo()).isEqualTo("VOU456");
        assertThat(detail.getTraceSeq()).isEqualTo(1);
        assertThat(detail.getTradingCode()).isEqualTo("");
        verify(subAccountDetailMapper).insert(detail);
    }

    @Test
    @DisplayName("SubAccountDetailRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void subAccountDetailRepository_insert_zeroAffected_throwsSystemError() {
        SubAccountDetailPO detail = new SubAccountDetailPO().setEntryId("ENTRY001");
        when(subAccountDetailMapper.insert(detail)).thenReturn(0);

        assertThatThrownBy(() -> subAccountDetailRepository.insert(detail))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    // ==================== AccountingVoucherRepository ====================

    @Test
    @DisplayName("AccountingVoucherRepository.updateById: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void voucherRepository_updateById_zeroAffected_throwsOptimisticLockFailed() {
        AccountingVoucherPO voucher = new AccountingVoucherPO().setVoucherNo("VOU001");
        when(voucherMapper.updateById(voucher)).thenReturn(0);

        assertThatThrownBy(() -> accountingVoucherRepository.updateById(voucher))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("AccountingVoucherRepository.updateEntryById: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void voucherRepository_updateEntryById_zeroAffected_throwsOptimisticLockFailed() {
        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setId(1L);
        entry.setEntryId("E001");
        when(entryMapper.updateById(entry)).thenReturn(0);

        assertThatThrownBy(() -> accountingVoucherRepository.updateEntryById(entry))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("AccountingVoucherRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void voucherRepository_insert_zeroAffected_throwsSystemError() {
        AccountingVoucherPO voucher = new AccountingVoucherPO().setVoucherNo("VOU001");
        when(voucherMapper.insert(voucher)).thenReturn(0);

        assertThatThrownBy(() -> accountingVoucherRepository.insert(voucher))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    // ==================== FreezeDetailRepository ====================

    @Test
    @DisplayName("FreezeDetailRepository.insert: 必填字段为null时防御性补全兜底默认值")
    void freezeDetailRepository_insert_defensiveNullFilling() {
        AccountFreezeDetailPO detail = new AccountFreezeDetailPO().setVoucherNo("FRZ123");
        when(accountFreezeDetailMapper.insert(any(AccountFreezeDetailPO.class))).thenReturn(1);

        freezeDetailRepository.insert(detail);

        assertThat(detail.getTxnNo()).isEqualTo("");
        assertThat(detail.getBusinessCode()).isEqualTo(com.kltb.accounting.api.constant.Constants.DEFAULT_BUSINESS_CODE);
        assertThat(detail.getTradingCode()).isEqualTo(com.kltb.accounting.api.constant.Constants.TRADING_CODE_FREEZE);
        assertThat(detail.getTraceNo()).isEqualTo("FRZ123");
        assertThat(detail.getTraceSeq()).isEqualTo(1);
        verify(accountFreezeDetailMapper).insert(detail);
    }

    @Test
    @DisplayName("FreezeDetailRepository.insert: 受影响行数等于0时抛出 SYSTEM_ERROR")
    void freezeDetailRepository_insert_zeroAffected_throwsSystemError() {
        AccountFreezeDetailPO detail = new AccountFreezeDetailPO().setVoucherNo("FRZ123");
        when(accountFreezeDetailMapper.insert(detail)).thenReturn(0);

        assertThatThrownBy(() -> freezeDetailRepository.insert(detail))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.SYSTEM_ERROR));
    }

    @Test
    @DisplayName("FreezeDetailRepository.updateAmountAndStatus: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void freezeDetailRepository_updateAmountAndStatus_zeroAffected_throwsOptimisticLockFailed() {
        when(accountFreezeDetailMapper.updateAmountsAndStatus(anyString(), any(), any(), any(), anyInt(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> freezeDetailRepository.updateAmountAndStatus("FRZ123", java.math.BigDecimal.ZERO, FreezeStatusEnum.UNFROZEN, 1))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("FreezeDetailRepository.updateAmountsAndStatus: 受影响行数等于0时抛出 OPTIMISTIC_LOCK_FAILED")
    void freezeDetailRepository_updateAmountsAndStatus_zeroAffected_throwsOptimisticLockFailed() {
        when(accountFreezeDetailMapper.updateAmountsAndStatus(anyString(), any(), any(), any(), anyInt(), anyInt())).thenReturn(0);

        assertThatThrownBy(() -> freezeDetailRepository.updateAmountsAndStatus("FRZ123", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, FreezeStatusEnum.UNFROZEN, 1))
                .isInstanceOf(AccountException.class)
                .satisfies(ex -> assertThat(((AccountException) ex).getResultCode()).isEqualTo(ResultCode.OPTIMISTIC_LOCK_FAILED));
    }

    @Test
    @DisplayName("FreezeDetailRepository.updateAmountsAndStatus: 受影响行数大于0时正常执行")
    void freezeDetailRepository_updateAmountsAndStatus_success() {
        when(accountFreezeDetailMapper.updateAmountsAndStatus("FRZ123", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, FreezeStatusEnum.UNFROZEN.getCode(), 1)).thenReturn(1);

        freezeDetailRepository.updateAmountsAndStatus("FRZ123", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, FreezeStatusEnum.UNFROZEN, 1);
        verify(accountFreezeDetailMapper).updateAmountsAndStatus("FRZ123", java.math.BigDecimal.ZERO, java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, FreezeStatusEnum.UNFROZEN.getCode(), 1);
    }

    // ==================== AccountBalanceRepository & SnapshotRepository ====================

    @Test
    @DisplayName("AccountBalanceRepository.batchUpsert: 自动补齐为 null 的 tenantId 为当前上下文或默认值")
    void accountBalanceRepository_batchUpsert_populatesTenantId() {
        TenantContext.set(8888);
        try {
            AccountBalancePO b1 = new AccountBalancePO();
            b1.setAccountNo("ACC_01");
            AccountBalancePO b2 = new AccountBalancePO();
            b2.setAccountNo("ACC_02");
            b2.setTenantId(9999);

            accountBalanceRepository.batchUpsert(List.of(b1, b2));

            assertThat(b1.getTenantId()).isEqualTo(8888);
            assertThat(b2.getTenantId()).isEqualTo(9999);
            verify(accountBalanceMapper).batchUpsertBalance(List.of(b1, b2));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("AccountBalanceSnapshotRepository.batchInsert: 自动补齐为 null 的 tenantId 为当前上下文或默认值")
    void accountBalanceSnapshotRepository_batchInsert_populatesTenantId() {
        TenantContext.set(8888);
        try {
            AccountBalanceSnapshotPO s1 = new AccountBalanceSnapshotPO();
            s1.setAccountNo("ACC_01");

            accountBalanceSnapshotRepository.batchInsert(List.of(s1));

            assertThat(s1.getTenantId()).isEqualTo(8888);
            verify(accountBalanceSnapshotMapper).batchInsertSnapshot(List.of(s1));
        } finally {
            TenantContext.clear();
        }
    }
}
