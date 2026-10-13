package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.messaging.LocalMessageService;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import com.kltb.accounting.core.shared.exception.AsyncRetryException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RollbackDomainServiceTest {

    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;
    @Mock
    private BusinessRecordRepository businessRecordRepository;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private SubAccountRepository subAccountRepository;
    @Mock
    private AccountDetailRepository accountDetailRepository;
    @Mock
    private SubAccountDetailRepository subAccountDetailRepository;
    @Mock
    private FreezeDetailRepository freezeDetailRepository;
    @Mock
    private FreezeDomainService freezeDomainService;
    @Mock
    private LocalMessageService localMessageService;
    @Mock
    private BufferPostingDetailRepository bufferPostingDetailRepository;
    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @InjectMocks
    private RollbackDomainService rollbackDomainService;

    @BeforeEach
    void setUp() {
        lenient().when(distributedLockTemplate.execute(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });

        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    org.springframework.transaction.support.TransactionCallback<?> action = invocation.getArgument(0);
                    return action.doInTransaction(null);
                });
    }

    @Test
    @DisplayName("executeFullJournalRollback: 成功流水禁止技术回滚")
    void executeFullJournalRollback_shouldThrowWhenSuccess() {
        String traceNo = "TRC_SUCCESS_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setStatus(BusinessRecordStatusEnum.SUCCESS);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        assertThatThrownBy(() -> rollbackDomainService.executeFullJournalRollback(traceNo, "用户申请"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("记账成功的流水禁止执行技术回滚");
    }

    @Test
    @DisplayName("executeFullJournalRollback: 正常回滚中途异常流水并释放预冻结和作废本地消息")
    void executeFullJournalRollback_shouldCleanUpAndMarkFailed() {
        String traceNo = "TRC_FAIL_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(1);
        record.setStatus(BusinessRecordStatusEnum.PROCESSING);
        record.setOrigFreezeNo("FRZ_001");
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_001");
        txn.setTraceNo(traceNo);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_001");
        voucher.setTxnNo("TXN_001");
        voucher.setTraceNo(traceNo);
        voucher.setStatus(VoucherStatusEnum.PENDING);
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        AccountingVoucherEntryPO entry1 = new AccountingVoucherEntryPO();
        entry1.setEntryId("ENTRY_001");
        entry1.setStatus(VoucherEntryStatusEnum.PENDING);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU_001")).thenReturn(List.of(entry1));

        // 模拟关联冻结记录
        AccountFreezeDetailPO freezePO = new AccountFreezeDetailPO();
        freezePO.setVoucherNo("FRZ_001");
        freezePO.setStatus(FreezeStatusEnum.FROZEN);
        freezePO.setFreezeAmount(new BigDecimal("100.00"));
        when(freezeDetailRepository.selectByVoucherNo("FRZ_001")).thenReturn(freezePO);

        rollbackDomainService.executeFullJournalRollback(traceNo, "余额校验失败");

        // 验证凭证分录状态变更为 FAILED
        assertThat(entry1.getStatus()).isEqualTo(VoucherEntryStatusEnum.FAILED);
        assertThat(voucher.getStatus()).isEqualTo(VoucherStatusEnum.FAILED);

        // 验证作废本地消息与缓冲
        verify(localMessageService).markFailedByBusinessKeys(eq(List.of("ENTRY_001")), anyString());
        verify(bufferPostingDetailRepository).markFailedByTraceNo(eq(traceNo), anyString());

        // 验证释放预冻结资金
        verify(freezeDomainService).unfreezeFund(eq("FRZ_001"), eq(new BigDecimal("100.00")), anyString());

        // 验证逻辑删除凭证关联的辅助核算明细
        verify(accountingVoucherRepository).deleteAuxiliaryByVoucherNos(eq(List.of("VOU_001")));

        // 验证事务与流水终态置为 FAILED
        verify(transactionRepository).updateStatusByTxnNo(eq("TXN_001"), eq(TransactionStatusEnum.FAILED), anyString(), any());
        verify(businessRecordRepository).updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
    }

    @Test
    @DisplayName("executeFullJournalRollback: 已完全回滚的流水再次执行回滚应被拦截")
    void executeFullJournalRollback_alreadyRolledBack_shouldThrowException() {
        String traceNo = "TRC_ALREADY_ROLLED_BACK";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setStatus(BusinessRecordStatusEnum.FAILED);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_999");
        txn.setStatus(TransactionStatusEnum.FAILED);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_999");
        voucher.setStatus(VoucherStatusEnum.FAILED);
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setStatus(VoucherEntryStatusEnum.FAILED);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU_999")).thenReturn(List.of(entry));
        when(accountingVoucherRepository.selectAuxiliaryByVoucherNo("VOU_999")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> rollbackDomainService.executeFullJournalRollback(traceNo, "重复操作测试"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已完成全额回滚清理，无需重复回滚");
    }

    @Test
    @DisplayName("executeFullJournalRollback: 回滚含部分已过账分录并反向冲回余额")
    void executeFullJournalRollback_withPostedEntries_shouldReverseBalances() {
        String traceNo = "TRC_PARTIAL_001";
        BusinessRecordPO record = new BusinessRecordPO();
        record.setTraceNo(traceNo);
        record.setTraceSeq(1);
        record.setStatus(BusinessRecordStatusEnum.PROCESSING);
        when(businessRecordRepository.selectByTraceNo(traceNo)).thenReturn(record);

        TransactionPO txn = new TransactionPO();
        txn.setTxnNo("TXN_002");
        txn.setTraceNo(traceNo);
        when(transactionRepository.selectByTraceNo(traceNo)).thenReturn(txn);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo("VOU_002");
        voucher.setTxnNo("TXN_002");
        voucher.setTraceNo(traceNo);
        voucher.setAmount(new BigDecimal("50.00"));
        voucher.setAccountingDate(LocalDate.now());
        when(accountingVoucherRepository.selectByTraceNo(traceNo)).thenReturn(List.of(voucher));

        // 已过账分录
        AccountingVoucherEntryPO postedEntry = new AccountingVoucherEntryPO();
        postedEntry.setEntryId("ENTRY_POSTED_1");
        postedEntry.setAccountNo("ACC_001");
        postedEntry.setAmount(new BigDecimal("50.00"));
        postedEntry.setChangeDirection(ChangeDirectionEnum.INCREASE);
        postedEntry.setDebitCredit(DebitCreditEnum.DEBIT);
        postedEntry.setStatus(VoucherEntryStatusEnum.POSTED);
        when(accountingVoucherRepository.selectEntriesByVoucherNo("VOU_002")).thenReturn(List.of(postedEntry));

        AccountPO account = new AccountPO();
        account.setAccountNo("ACC_001");
        account.setBalance(new BigDecimal("150.00"));
        when(accountRepository.selectForUpdate("ACC_001")).thenReturn(account);

        rollbackDomainService.executeFullJournalRollback(traceNo, "异步分录超限失败回滚");

        // 验证主账户余额反向扣减 (150 - 50 = 100)
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
        verify(accountRepository).updateById(account);

        // 验证插入了反向凭证与明细
        verify(accountingVoucherRepository).insert(argThat(v -> v.getVoucherNo().startsWith("REVVOU_002")));
        verify(accountDetailRepository).insert(argThat(d -> d.getVoucherNo().startsWith("REVVOU_002")));

        // 验证流水状态置为 FAILED
        verify(businessRecordRepository).updateStatusByTraceNo(traceNo, BusinessRecordStatusEnum.FAILED);
    }

    @Test
    @DisplayName("isRetryable: 各种可重试异常与不可重试异常断言")
    void testIsRetryable() {
        // null -> false
        assertThat(rollbackDomainService.isRetryable(null)).isFalse();

        // 1. AsyncRetryException 及其包装场景
        assertThat(rollbackDomainService.isRetryable(new AsyncRetryException(ResultCode.SYSTEM_ERROR))).isTrue();
        assertThat(rollbackDomainService.isRetryable(new RuntimeException("外层包装", new AsyncRetryException(ResultCode.SYSTEM_ERROR)))).isTrue();

        // 2. 数据库悲观锁冲突
        assertThat(rollbackDomainService.isRetryable(new PessimisticLockingFailureException("锁等待超时"))).isTrue();
        assertThat(rollbackDomainService.isRetryable(new IllegalStateException("事务异常", new PessimisticLockingFailureException("锁冲突")))).isTrue();

        // 3. 超时异常
        assertThat(rollbackDomainService.isRetryable(new TimeoutException("请求超时"))).isTrue();

        // 4. 临时数据资源异常
        assertThat(rollbackDomainService.isRetryable(new TransientDataAccessResourceException("连接池抖动"))).isTrue();

        // 5. 不可重试异常
        assertThat(rollbackDomainService.isRetryable(new ServiceException(ResultCode.PARAM_ERROR, "参数错误"))).isFalse();
        assertThat(rollbackDomainService.isRetryable(new IllegalArgumentException("非法参数"))).isFalse();
        assertThat(rollbackDomainService.isRetryable(new RuntimeException("普通业务异常"))).isFalse();
    }
}
