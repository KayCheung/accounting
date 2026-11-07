package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SnapshotTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceSnapshotRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 日切日余额计算与快照领域服务单元测试
 */
@ExtendWith(MockitoExtension.class)
class EodDomainServiceTest {

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private AccountBalanceSnapshotRepository snapshotRepository;

    @Mock
    private AccountingVoucherEntryMapper voucherEntryMapper;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private EodDomainService eodDomainService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        lenient().doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback != null ? callback.doInTransaction(null) : null;
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("无交易会计日全量账户日余额自然滚动结转（解决总分核对失败）")
    void calculateDailyBalances_noTransactions_rollsForwardAllActiveAccounts() {
        LocalDate accountingDate = LocalDate.of(2026, 10, 10);

        // 当日无任何交易凭证分录
        when(voucherEntryMapper.sumEntriesByAccount(accountingDate)).thenReturn(List.of());

        // 系统中存在两个活跃账户（均有余额）
        AccountPO acc1 = new AccountPO();
        acc1.setAccountNo("ACC1002001");
        acc1.setSubjectCode("1002");
        acc1.setBalance(new BigDecimal("1000.00"));
        acc1.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        acc1.setStatus(AccountStatusEnum.NORMAL);
        acc1.setTenantId(1);

        AccountPO acc2 = new AccountPO();
        acc2.setAccountNo("ACC2001001");
        acc2.setSubjectCode("2001");
        acc2.setBalance(new BigDecimal("1000.00"));
        acc2.setBalanceDirection(BalanceDirectionEnum.CREDIT);
        acc2.setStatus(AccountStatusEnum.NORMAL);
        acc2.setTenantId(1);

        when(accountRepository.selectAllActiveAccounts()).thenReturn(List.of(acc1, acc2));

        // 前一日（2026-10-09）存在日余额记录
        AccountBalancePO prev1 = new AccountBalancePO();
        prev1.setAccountNo("ACC1002001");
        prev1.setSubjectCode("1002");
        prev1.setEndBalance(new BigDecimal("1000.00"));
        prev1.setBalanceDirection(BalanceDirectionEnum.DEBIT);

        AccountBalancePO prev2 = new AccountBalancePO();
        prev2.setAccountNo("ACC2001001");
        prev2.setSubjectCode("2001");
        prev2.setEndBalance(new BigDecimal("1000.00"));
        prev2.setBalanceDirection(BalanceDirectionEnum.CREDIT);

        when(accountBalanceRepository.selectByDate(accountingDate.minusDays(1)))
                .thenReturn(List.of(prev1, prev2));

        List<AccountBalancePO> balances = eodDomainService.calculateDailyBalances(accountingDate);

        assertThat(balances).hasSize(2);

        AccountBalancePO b1 = balances.stream()
                .filter(b -> "ACC1002001".equals(b.getAccountNo()))
                .findFirst().orElseThrow();
        assertThat(b1.getSubjectCode()).isEqualTo("1002");
        assertThat(b1.getBeginBalance()).isEqualByComparingTo("1000.00");
        assertThat(b1.getDebitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b1.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b1.getEndBalance()).isEqualByComparingTo("1000.00");
        assertThat(b1.getBalanceDirection()).isEqualTo(BalanceDirectionEnum.DEBIT);

        AccountBalancePO b2 = balances.stream()
                .filter(b -> "ACC2001001".equals(b.getAccountNo()))
                .findFirst().orElseThrow();
        assertThat(b2.getSubjectCode()).isEqualTo("2001");
        assertThat(b2.getBeginBalance()).isEqualByComparingTo("1000.00");
        assertThat(b2.getDebitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b2.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b2.getEndBalance()).isEqualByComparingTo("1000.00");
        assertThat(b2.getBalanceDirection()).isEqualTo(BalanceDirectionEnum.CREDIT);
    }

    @Test
    @DisplayName("混合场景：部分账户有交易发生，部分账户无交易发生，日余额均准确结转")
    void calculateDailyBalances_mixedTransactions_bothActiveAccountsCalculated() {
        LocalDate accountingDate = LocalDate.of(2026, 10, 10);

        // 仅 ACC1002001 当日有借方发生额 500
        Map<String, Object> entryRow = new HashMap<>();
        entryRow.put("account_no", "ACC1002001");
        entryRow.put("subject_code", "1002");
        entryRow.put("total_debit", new BigDecimal("500.00"));
        entryRow.put("total_credit", BigDecimal.ZERO);
        when(voucherEntryMapper.sumEntriesByAccount(accountingDate)).thenReturn(List.of(entryRow));

        AccountPO acc1 = new AccountPO();
        acc1.setAccountNo("ACC1002001");
        acc1.setSubjectCode("1002");
        acc1.setBalance(new BigDecimal("1500.00"));
        acc1.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        acc1.setStatus(AccountStatusEnum.NORMAL);

        AccountPO acc2 = new AccountPO();
        acc2.setAccountNo("ACC2001001");
        acc2.setSubjectCode("2001");
        acc2.setBalance(new BigDecimal("2000.00"));
        acc2.setBalanceDirection(BalanceDirectionEnum.CREDIT);
        acc2.setStatus(AccountStatusEnum.NORMAL);

        when(accountRepository.selectAllActiveAccounts()).thenReturn(List.of(acc1, acc2));

        AccountBalancePO prev1 = new AccountBalancePO();
        prev1.setAccountNo("ACC1002001");
        prev1.setSubjectCode("1002");
        prev1.setEndBalance(new BigDecimal("1000.00"));
        prev1.setBalanceDirection(BalanceDirectionEnum.DEBIT);

        AccountBalancePO prev2 = new AccountBalancePO();
        prev2.setAccountNo("ACC2001001");
        prev2.setSubjectCode("2001");
        prev2.setEndBalance(new BigDecimal("2000.00"));
        prev2.setBalanceDirection(BalanceDirectionEnum.CREDIT);

        when(accountBalanceRepository.selectByDate(accountingDate.minusDays(1)))
                .thenReturn(List.of(prev1, prev2));

        List<AccountBalancePO> balances = eodDomainService.calculateDailyBalances(accountingDate);

        assertThat(balances).hasSize(2);

        AccountBalancePO b1 = balances.stream()
                .filter(b -> "ACC1002001".equals(b.getAccountNo()))
                .findFirst().orElseThrow();
        assertThat(b1.getBeginBalance()).isEqualByComparingTo("1000.00");
        assertThat(b1.getDebitAmount()).isEqualByComparingTo("500.00");
        assertThat(b1.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b1.getEndBalance()).isEqualByComparingTo("1500.00");

        AccountBalancePO b2 = balances.stream()
                .filter(b -> "ACC2001001".equals(b.getAccountNo()))
                .findFirst().orElseThrow();
        assertThat(b2.getBeginBalance()).isEqualByComparingTo("2000.00");
        assertThat(b2.getDebitAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b2.getCreditAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(b2.getEndBalance()).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("批量保存日余额通过 TransactionTemplate 提交")
    void upsertDailyBalances_usesTransactionTemplate() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        AccountBalancePO b = new AccountBalancePO();
        b.setAccountNo("ACC001");
        List<AccountBalancePO> list = List.of(b);

        eodDomainService.upsertDailyBalances(date, list);

        verify(accountBalanceRepository).batchUpsert(list);
    }

    @Test
    @DisplayName("日余额生成快照成功")
    void generateDailySnapshot_success() {
        LocalDate date = LocalDate.of(2026, 10, 10);
        AccountBalancePO b = new AccountBalancePO();
        b.setAccountingDate(date);
        b.setSubjectCode("1002");
        b.setAccountNo("ACC001");
        b.setCurrency(Constants.DEFAULT_CURRENCY);
        b.setBalanceDirection(BalanceDirectionEnum.DEBIT);
        b.setDebitAmount(BigDecimal.ZERO);
        b.setCreditAmount(BigDecimal.ZERO);
        b.setEndBalance(new BigDecimal("100.00"));

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(b));

        int count = eodDomainService.generateDailySnapshot(date);

        assertThat(count).isEqualTo(1);
        verify(snapshotRepository).batchInsert(anyList());
    }
}
