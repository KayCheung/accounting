package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.request.BalanceSheetQueryRequest;
import com.kltb.accounting.api.request.GeneralLedgerQueryRequest;
import com.kltb.accounting.api.request.IncomeStatementQueryRequest;
import com.kltb.accounting.api.request.SubsidiaryLedgerQueryRequest;
import com.kltb.accounting.api.response.BalanceSheetResponse;
import com.kltb.accounting.api.response.GeneralLedgerResponse;
import com.kltb.accounting.api.response.IncomeStatementResponse;
import com.kltb.accounting.api.response.SubsidiaryLedgerResponse;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialReportDomainServiceTest {

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Mock
    private AccountingVoucherRepository voucherRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @InjectMocks
    private FinancialReportDomainService domainService;

    @Test
    @DisplayName("资产负债表生成与平衡校验测试")
    void testGenerateBalanceSheet() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        BalanceSheetQueryRequest req = new BalanceSheetQueryRequest();
        req.setAccountingDate(date);

        // 模拟科目
        AccountSubjectPO cashSub = new AccountSubjectPO();
        cashSub.setSubjectCode("1002");
        cashSub.setSubjectName("银行存款");

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(cashSub));

        // 资产端：货币资金 10000.00
        AccountBalancePO b1 = new AccountBalancePO();
        b1.setSubjectCode("100201");
        b1.setEndBalance(new BigDecimal("10000.00"));
        b1.setBeginBalance(new BigDecimal("8000.00"));

        // 权益端：实收资本 10000.00
        AccountBalancePO b2 = new AccountBalancePO();
        b2.setSubjectCode("4001");
        b2.setEndBalance(new BigDecimal("10000.00"));
        b2.setBeginBalance(new BigDecimal("8000.00"));

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(b1, b2));
        when(accountBalanceRepository.selectByDate(LocalDate.of(2026, 1, 1))).thenReturn(List.of(b1, b2));

        BalanceSheetResponse resp = domainService.generateBalanceSheet(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getBalanced()).isTrue();
        assertThat(resp.getTotalAssetEnd()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(resp.getTotalLiabilityAndEquityEnd()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(resp.getDiffAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("利润表生成与毛利率/净利润测试")
    void testGenerateIncomeStatement() {
        IncomeStatementQueryRequest req = new IncomeStatementQueryRequest();
        req.setYear(2026);
        req.setMonth(3);
        req.setCompareType(1);

        // 收入：6001 主营业务收入 100000.00 (贷方)
        AccountBalancePO r1 = new AccountBalancePO();
        r1.setSubjectCode("6001");
        r1.setCreditAmount(new BigDecimal("100000.00"));
        r1.setDebitAmount(BigDecimal.ZERO);

        // 成本：6401 主营业务成本 60000.00 (借方)
        AccountBalancePO c1 = new AccountBalancePO();
        c1.setSubjectCode("6401");
        c1.setDebitAmount(new BigDecimal("60000.00"));
        c1.setCreditAmount(BigDecimal.ZERO);

        // 管理费用：6602 管理费用 10000.00 (借方)
        AccountBalancePO e1 = new AccountBalancePO();
        e1.setSubjectCode("6602");
        e1.setDebitAmount(new BigDecimal("10000.00"));
        e1.setCreditAmount(BigDecimal.ZERO);

        when(accountBalanceRepository.selectByDateRange(any(), any())).thenReturn(List.of(r1, c1, e1));

        IncomeStatementResponse resp = domainService.generateIncomeStatement(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getKpi()).isNotNull();
        assertThat(resp.getKpi().getRevenueMonth()).isEqualByComparingTo(new BigDecimal("100000.00"));
        // 营业利润 = 100000 - 60000 - 10000 = 30000
        assertThat(resp.getKpi().getOperatingProfitMonth()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(resp.getKpi().getNetProfitMonth()).isEqualByComparingTo(new BigDecimal("30000.00"));
        // 毛利率 = (100000 - 60000) / 100000 = 40.0%
        assertThat(resp.getKpi().getGrossMarginRate()).isEqualByComparingTo(new BigDecimal("40.0"));
    }

    @Test
    @DisplayName("科目总账生成与借贷平衡测试")
    void testGenerateGeneralLedger() {
        GeneralLedgerQueryRequest req = new GeneralLedgerQueryRequest();
        req.setStartDate(LocalDate.of(2026, 3, 1));
        req.setEndDate(LocalDate.of(2026, 3, 31));

        AccountSubjectPO sub1 = new AccountSubjectPO();
        sub1.setSubjectCode("1002");
        sub1.setSubjectName("银行存款");
        sub1.setSubjectLevel(1);
        sub1.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT);

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(sub1));

        AccountBalancePO b = new AccountBalancePO();
        b.setSubjectCode("1002");
        b.setAccountingDate(LocalDate.of(2026, 3, 15));
        b.setBeginBalance(new BigDecimal("5000.00"));
        b.setDebitAmount(new BigDecimal("2000.00"));
        b.setCreditAmount(new BigDecimal("1000.00"));
        b.setEndBalance(new BigDecimal("6000.00"));

        when(accountBalanceRepository.selectByDateRange(any(), any())).thenReturn(List.of(b));

        GeneralLedgerResponse resp = domainService.generateGeneralLedger(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getItems()).hasSize(1);
        assertThat(resp.getItems().get(0).getBeginBalance()).isEqualByComparingTo(new BigDecimal("5000.00"));
        assertThat(resp.getItems().get(0).getEndBalance()).isEqualByComparingTo(new BigDecimal("6000.00"));
    }

    @Test
    @DisplayName("科目明细账生成与动态余额轧差测试")
    void testGenerateSubsidiaryLedger() {
        SubsidiaryLedgerQueryRequest req = new SubsidiaryLedgerQueryRequest();
        req.setSubjectCode("1002");
        req.setStartDate(LocalDate.of(2026, 3, 1));
        req.setEndDate(LocalDate.of(2026, 3, 31));

        AccountSubjectPO sub = new AccountSubjectPO();
        sub.setSubjectCode("1002");
        sub.setSubjectName("银行存款");
        sub.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT);
        when(subjectRepository.selectByCode("1002")).thenReturn(sub);

        // 期初日余额
        AccountBalancePO beginBal = new AccountBalancePO();
        beginBal.setSubjectCode("1002");
        beginBal.setBeginBalance(new BigDecimal("1000.00"));
        when(accountBalanceRepository.selectBySubjectAndDateRange(eq("1002"), any(), any()))
                .thenReturn(List.of(beginBal));

        // 分录流水：借方 500，贷方 200
        AccountingVoucherEntryPO e1 = new AccountingVoucherEntryPO();
        e1.setVoucherNo("VC001");
        e1.setEntryId("ENT001");
        e1.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT);
        e1.setAmount(new BigDecimal("500.00"));
        e1.setAccountingDate(LocalDate.of(2026, 3, 10));
        e1.setSummary("收到客户转账");

        AccountingVoucherEntryPO e2 = new AccountingVoucherEntryPO();
        e2.setVoucherNo("VC002");
        e2.setEntryId("ENT002");
        e2.setDebitCredit(com.kltb.accounting.core.domain.enums.DebitCreditEnum.CREDIT);
        e2.setAmount(new BigDecimal("200.00"));
        e2.setAccountingDate(LocalDate.of(2026, 3, 20));
        e2.setSummary("支付供应商货款");

        when(voucherRepository.selectEntriesBySubjectAndDateRange(eq("1002"), any(), any(), any()))
                .thenReturn(List.of(e1, e2));

        SubsidiaryLedgerResponse resp = domainService.generateSubsidiaryLedger(req);

        assertThat(resp).isNotNull();
        // 包含期初行(1) + 2笔流水 + 本期合计行(1) = 4行
        assertThat(resp.getItems()).hasSize(4);
        assertThat(resp.getBeginBalance()).isEqualByComparingTo(new BigDecimal("1000.00"));
        // 动态余额：1000 + 500 - 200 = 1300
        assertThat(resp.getEndBalance()).isEqualByComparingTo(new BigDecimal("1300.00"));
        assertThat(resp.getTotalDebitAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
        assertThat(resp.getTotalCreditAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
    }
}
