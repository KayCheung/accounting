package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.infrastructure.redis.DistributedLockTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * BufferPostingEngineDomainService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class BufferPostingEngineDomainServiceTest {

    @Mock
    private BufferPostingDetailRepository bufferPostingDetailRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private SubAccountRepository subAccountRepository;

    @Mock
    private AccountDetailRepository accountDetailRepository;

    @Mock
    private SubAccountDetailRepository subAccountDetailRepository;

    @Mock
    private AccountingVoucherRepository accountingVoucherRepository;

    @Mock
    private DistributedLockTemplate distributedLockTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RunningBalanceValidator runningBalanceValidator;

    private BufferPostingEngineDomainService bufferPostingEngineDomainService;

    @BeforeEach
    void setUp() {
        bufferPostingEngineDomainService = new BufferPostingEngineDomainService(
                bufferPostingDetailRepository,
                accountRepository,
                subAccountRepository,
                accountDetailRepository,
                subAccountDetailRepository,
                accountingVoucherRepository,
                distributedLockTemplate,
                transactionTemplate,
                runningBalanceValidator
        );

        // 模拟分布式锁直接执行 supplier
        lenient().when(distributedLockTemplate.execute(anyString(), anyLong(), anyLong(), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> supplier = invocation.getArgument(3);
                    return supplier.get();
                });

        // 模拟 TransactionTemplate 直接执行 callback
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<?> callback = invocation.getArgument(0);
                    TransactionStatus status = new SimpleTransactionStatus();
                    return callback.doInTransaction(status);
                });
    }

    @Test
    @DisplayName("逐条缓冲过账成功：同时更新主账户与子账户余额，并写入 t_account_detail 流水")
    void executeSinglePosting_success() {
        String accountNo = "10010001";
        LocalDate accountingDate = LocalDate.of(2026, 10, 13);

        BufferPostingDetailPO detail = new BufferPostingDetailPO()
                .setVoucherNo("V202610130001")
                .setEntryId("E001")
                .setTxnNo("TXN001")
                .setTraceNo("TRC001")
                .setTraceSeq(0)
                .setAccountNo(accountNo)
                .setDebitCredit(DebitCreditEnum.DEBIT)
                .setAmount(new BigDecimal("100.00"))
                .setAccountingDate(accountingDate)
                .setStatus(BufferStatusEnum.PENDING)
                .setRetryCount(0);
        detail.setId(100L);

        AccountPO account = new AccountPO()
                .setAccountNo(accountNo)
                .setSubjectCode("1001")
                .setBalance(new BigDecimal("1000.00"));

        SubAccountPO subAccount = new SubAccountPO()
                .setAccountNo(accountNo)
                .setBalanceType(BalanceTypeEnum.AVAILABLE)
                .setBalanceDirection(BalanceDirectionEnum.DEBIT) // 借方账户，借方增加
                .setBalance(new BigDecimal("1000.00"));

        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(subAccount);

        AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
        entry.setEntryId("E001");
        entry.setStatus(VoucherEntryStatusEnum.PENDING);
        when(accountingVoucherRepository.selectEntryByVoucherNoAndEntryId("V202610130001", "E001")).thenReturn(entry);

        // 执行单笔明细过账
        bufferPostingEngineDomainService.processSingleDetail(detail);

        // 校验 1：主账户余额变更为 1100.00，并且调用了 updateById
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        verify(accountRepository).updateById(account);

        // 校验 2：子账户余额变更为 1100.00，并且调用了 updateById
        assertThat(subAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        verify(subAccountRepository).updateById(subAccount);

        // 校验 3：向 t_account_detail 插入明细流水
        ArgumentCaptor<AccountDetailPO> accountDetailCaptor = ArgumentCaptor.forClass(AccountDetailPO.class);
        verify(accountDetailRepository).insert(accountDetailCaptor.capture());
        AccountDetailPO capturedDetail = accountDetailCaptor.getValue();
        assertThat(capturedDetail.getAccountNo()).isEqualTo(accountNo);
        assertThat(capturedDetail.getPreBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(capturedDetail.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(capturedDetail.getPostBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));

        // 校验 4：向 t_sub_account_detail 插入记录
        verify(subAccountDetailRepository).insert(any(SubAccountDetailPO.class));

        // 校验 5：更新缓冲明细为 SUCCESS
        verify(bufferPostingDetailRepository).updateToSuccess(eq(100L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("按账户汇总批量过账：通过 Running Balance 逐笔推导，批量落库流水")
    void processAccountSummary_success() {
        String accountNo = "10010001";
        LocalDate accountingDate = LocalDate.of(2026, 10, 13);

        BufferPostingDetailPO d1 = new BufferPostingDetailPO()
                .setVoucherNo("V001")
                .setEntryId("E1")
                .setAccountNo(accountNo)
                .setDebitCredit(DebitCreditEnum.DEBIT)
                .setAmount(new BigDecimal("100.00"))
                .setTradeTime(LocalDateTime.of(2026, 10, 13, 10, 0, 0))
                .setAccountingDate(accountingDate)
                .setStatus(BufferStatusEnum.PENDING);
        d1.setId(101L);

        BufferPostingDetailPO d2 = new BufferPostingDetailPO()
                .setVoucherNo("V002")
                .setEntryId("E2")
                .setAccountNo(accountNo)
                .setDebitCredit(DebitCreditEnum.CREDIT) // 借方账户，贷方减少
                .setAmount(new BigDecimal("30.00"))
                .setTradeTime(LocalDateTime.of(2026, 10, 13, 10, 5, 0))
                .setAccountingDate(accountingDate)
                .setStatus(BufferStatusEnum.PENDING);
        d2.setId(102L);

        AccountPO account = new AccountPO()
                .setAccountNo(accountNo)
                .setSubjectCode("1001")
                .setBalance(new BigDecimal("1000.00"));

        SubAccountPO subAccount = new SubAccountPO()
                .setAccountNo(accountNo)
                .setBalanceType(BalanceTypeEnum.AVAILABLE)
                .setBalanceDirection(BalanceDirectionEnum.DEBIT)
                .setBalance(new BigDecimal("1000.00"));

        when(accountRepository.selectByAccountNo(accountNo)).thenReturn(account);
        when(subAccountRepository.selectByAccountNoAndType(accountNo, BalanceTypeEnum.AVAILABLE.getCode())).thenReturn(subAccount);

        bufferPostingEngineDomainService.processAccountSummary(accountNo, accountingDate, List.of(d1, d2));

        // 1000 + 100 - 30 = 1070
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("1070.00"));
        assertThat(subAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1070.00"));

        // 校验流水批量写入
        ArgumentCaptor<List<AccountDetailPO>> detailsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accountDetailRepository).batchInsert(detailsCaptor.capture());
        List<AccountDetailPO> capturedDetails = detailsCaptor.getValue();
        assertThat(capturedDetails).hasSize(2);

        // 第 1 笔：pre=1000, amount=100, post=1100
        assertThat(capturedDetails.get(0).getPreBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(capturedDetails.get(0).getPostBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));

        // 第 2 笔：pre=1100, amount=30, post=1070
        assertThat(capturedDetails.get(1).getPreBalance()).isEqualByComparingTo(new BigDecimal("1100.00"));
        assertThat(capturedDetails.get(1).getPostBalance()).isEqualByComparingTo(new BigDecimal("1070.00"));
    }
}
