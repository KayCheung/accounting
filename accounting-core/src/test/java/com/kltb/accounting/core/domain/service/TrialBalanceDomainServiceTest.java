package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialBalanceDomainServiceTest {

    @Mock
    private AccountingVoucherEntryMapper voucherEntryMapper;
    @Mock
    private SubjectRepository subjectRepository;
    @Mock
    private AccountBalanceMapper accountBalanceMapper;
    @Mock
    private AccountMapper accountMapper;
    @Mock
    private AccountDetailMapper accountDetailMapper;
    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @InjectMocks
    private TrialBalanceDomainService trialBalanceDomainService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("executeTrialBalance: 借贷平衡测试")
    void executeTrialBalance_balanced_shouldPass() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("subject_code", "1001");
        row1.put("total_debit", new BigDecimal("1000.00"));
        row1.put("total_credit", "0.00"); // 模拟 String 输入

        Map<String, Object> row2 = new HashMap<>();
        row2.put("subject_code", "2001");
        row2.put("total_debit", 0L); // 模拟 Long 输入
        row2.put("total_credit", new BigDecimal("1000.00"));

        when(voucherEntryMapper.sumEntriesBySubject(testDate)).thenReturn(List.of(row1, row2));
        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1001");
        sub1.setSubjectName("库存现金");
        when(subjectRepository.selectByCode("1001")).thenReturn(sub1);

        TrialBalanceDomainService.TrialBalanceResult result = trialBalanceDomainService.executeTrialBalance(testDate);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getTotalDebit()).isEqualByComparingTo("1000.00");
        assertThat(result.getTotalCredit()).isEqualByComparingTo("1000.00");
        assertThat(result.getDiff()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("executeGlReconciliation: 总分核对测试")
    void executeGlReconciliation_matched_shouldPass() {
        Map<String, Object> glRow = new HashMap<>();
        glRow.put("subject_code", "1001");
        glRow.put("total_balance", "5000.00"); // 字符串格式

        Map<String, Object> acctRow = new HashMap<>();
        acctRow.put("subject_code", "1001");
        acctRow.put("total_balance", 5000L); // 数值格式

        when(accountBalanceMapper.sumBalancesBySubject(testDate)).thenReturn(List.of(glRow));
        when(accountMapper.sumBalancesBySubject()).thenReturn(List.of(acctRow));

        TrialBalanceDomainService.GlReconciliationResult result = trialBalanceDomainService.executeGlReconciliation(testDate);

        assertThat(result.isPassed()).isTrue();
        assertThat(result.getTotalDiff()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("executeGlReconciliation: 历史会计日核对时回滚后续凭证发生额消除切片差异")
    void executeGlReconciliation_withFutureVouchers_shouldRollbackAndPass() {
        LocalDate histDate = LocalDate.of(2026, 11, 3);

        // 历史日 2026-11-03 总账日余额为 512,865.00
        Map<String, Object> glRow = new HashMap<>();
        glRow.put("subject_code", "12010101");
        glRow.put("total_balance", new BigDecimal("512865.00"));
        when(accountBalanceMapper.sumBalancesBySubject(histDate)).thenReturn(List.of(glRow));

        // 分户实时余额表（包含 2026-11-09 的后续 61,117.85 发生额，总计 573,982.85）
        Map<String, Object> acctRow = new HashMap<>();
        acctRow.put("subject_code", "12010101");
        acctRow.put("total_balance", new BigDecimal("573982.85"));
        acctRow.put("account_count", 81);
        when(accountMapper.sumBalancesBySubject()).thenReturn(List.of(acctRow));

        // 2026-11-03 之后存在后续凭证发生额（借方 61,117.85）
        Map<String, Object> futureRow = new HashMap<>();
        futureRow.put("subject_code", "12010101");
        futureRow.put("total_debit", new BigDecimal("61117.85"));
        futureRow.put("total_credit", BigDecimal.ZERO);
        when(voucherEntryMapper.sumEntriesAfterDateGroupSubject(histDate)).thenReturn(List.of(futureRow));

        AccountSubjectPO subject = new AccountSubjectPO();
        subject.setSubjectCode("12010101");
        subject.setDebitCredit(com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.DEBIT);
        when(subjectRepository.selectByCode("12010101")).thenReturn(subject);

        TrialBalanceDomainService.GlReconciliationResult result = trialBalanceDomainService.executeGlReconciliation(histDate);

        // 经后续发生额回滚（573982.85 - 61117.85 = 512865.00），总分完美平衡！
        assertThat(result.isPassed()).isTrue();
        assertThat(result.getTotalDiff()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
