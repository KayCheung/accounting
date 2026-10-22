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
import com.kltb.accounting.core.infrastructure.persistence.repository.FinancialReportItemMappingRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.constant.ReportItemConstants;
import com.kltb.accounting.core.infrastructure.persistence.entity.FinancialReportItemMappingPO;
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

    @Mock
    private FinancialReportItemMappingRepository mappingRepository;

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
    @DisplayName("表结法测试：存在未结转损益246.68元时动态轧平资产负债表")
    void testGenerateBalanceSheet_ReportClosing_WithUnclosedProfit() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        BalanceSheetQueryRequest req = new BalanceSheetQueryRequest();
        req.setAccountingDate(date);
        req.setClosingMethod("REPORT");

        AccountSubjectPO cashSub = new AccountSubjectPO();
        cashSub.setSubjectCode("1002");
        cashSub.setSubjectName("银行存款");

        AccountSubjectPO capSub = new AccountSubjectPO();
        capSub.setSubjectCode("4001");
        capSub.setSubjectName("实收资本");

        AccountSubjectPO revSub = new AccountSubjectPO();
        revSub.setSubjectCode("6011");
        revSub.setSubjectName("利息收入");
        revSub.setSubjectCategory(com.kltb.accounting.core.domain.enums.SubjectCategoryEnum.PROFIT_LOSS);
        revSub.setDebitCredit(com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.CREDIT);

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(cashSub, capSub, revSub));

        // 资产端：货币资金 300000246.68
        AccountBalancePO b1 = new AccountBalancePO();
        b1.setSubjectCode("100201");
        b1.setEndBalance(new BigDecimal("300000246.68"));
        b1.setBeginBalance(new BigDecimal("300000000.00"));

        // 权益端：实收资本 300000000.00
        AccountBalancePO b2 = new AccountBalancePO();
        b2.setSubjectCode("4001");
        b2.setEndBalance(new BigDecimal("300000000.00"));
        b2.setBeginBalance(new BigDecimal("300000000.00"));

        // 损益端：利息收入 246.68 (贷方余额，尚未结转)
        AccountBalancePO b3 = new AccountBalancePO();
        b3.setSubjectCode("6011");
        b3.setEndBalance(new BigDecimal("246.68"));
        b3.setBalanceDirection(com.kltb.accounting.core.domain.enums.BalanceDirectionEnum.CREDIT);
        b3.setBeginBalance(BigDecimal.ZERO);

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(b1, b2, b3));
        when(accountBalanceRepository.selectByDate(LocalDate.of(2026, 1, 1))).thenReturn(List.of(b1, b2));

        BalanceSheetResponse resp = domainService.generateBalanceSheet(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getClosingMethod()).isEqualTo("REPORT");
        assertThat(resp.getUnclosedProfitEnd()).isEqualByComparingTo(new BigDecimal("246.68"));
        assertThat(resp.getTotalAssetEnd()).isEqualByComparingTo(new BigDecimal("300000246.68"));
        assertThat(resp.getTotalLiabilityAndEquityEnd()).isEqualByComparingTo(new BigDecimal("300000246.68"));
        assertThat(resp.getBalanced()).isTrue();
        assertThat(resp.getDiffAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // 验证未分配利润行包含未结转损益
        var undistItem = resp.getLiabilityAndEquityItems().stream()
                .filter(i -> i.getRowNo() == 74)
                .findFirst().orElseThrow();
        assertThat(undistItem.getEndAmount()).isEqualByComparingTo(new BigDecimal("246.68"));
        assertThat(undistItem.getDetailSubjects()).isNotEmpty();
        assertThat(undistItem.getDetailSubjects().get(0).getSubjectCode()).isEqualTo("999999");
    }

    @Test
    @DisplayName("账结法测试：存在未结转损益246.68元时按账面如实反映差额")
    void testGenerateBalanceSheet_AccountClosing_WithUnclosedProfit() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        BalanceSheetQueryRequest req = new BalanceSheetQueryRequest();
        req.setAccountingDate(date);
        req.setClosingMethod("ACCOUNT");

        AccountSubjectPO cashSub = new AccountSubjectPO();
        cashSub.setSubjectCode("1002");
        cashSub.setSubjectName("银行存款");

        AccountSubjectPO capSub = new AccountSubjectPO();
        capSub.setSubjectCode("4001");
        capSub.setSubjectName("实收资本");

        AccountSubjectPO revSub = new AccountSubjectPO();
        revSub.setSubjectCode("6011");
        revSub.setSubjectName("利息收入");
        revSub.setSubjectCategory(com.kltb.accounting.core.domain.enums.SubjectCategoryEnum.PROFIT_LOSS);
        revSub.setDebitCredit(com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.CREDIT);

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(cashSub, capSub, revSub));

        AccountBalancePO b1 = new AccountBalancePO();
        b1.setSubjectCode("100201");
        b1.setEndBalance(new BigDecimal("300000246.68"));
        b1.setBeginBalance(new BigDecimal("300000000.00"));

        AccountBalancePO b2 = new AccountBalancePO();
        b2.setSubjectCode("4001");
        b2.setEndBalance(new BigDecimal("300000000.00"));
        b2.setBeginBalance(new BigDecimal("300000000.00"));

        AccountBalancePO b3 = new AccountBalancePO();
        b3.setSubjectCode("6011");
        b3.setEndBalance(new BigDecimal("246.68"));
        b3.setBalanceDirection(com.kltb.accounting.core.domain.enums.BalanceDirectionEnum.CREDIT);
        b3.setBeginBalance(BigDecimal.ZERO);

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(b1, b2, b3));
        when(accountBalanceRepository.selectByDate(LocalDate.of(2026, 1, 1))).thenReturn(List.of(b1, b2));

        BalanceSheetResponse resp = domainService.generateBalanceSheet(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getClosingMethod()).isEqualTo("ACCOUNT");
        assertThat(resp.getUnclosedProfitEnd()).isEqualByComparingTo(new BigDecimal("246.68"));
        assertThat(resp.getTotalAssetEnd()).isEqualByComparingTo(new BigDecimal("300000246.68"));
        // 账结法下，未结转前所有者权益未包含损益，故为 300,000,000.00
        assertThat(resp.getTotalLiabilityAndEquityEnd()).isEqualByComparingTo(new BigDecimal("300000000.00"));
        assertThat(resp.getBalanced()).isFalse();
        assertThat(resp.getDiffAmount()).isEqualByComparingTo(new BigDecimal("246.68"));

        var undistItem = resp.getLiabilityAndEquityItems().stream()
                .filter(i -> i.getRowNo() == 74)
                .findFirst().orElseThrow();
        assertThat(undistItem.getEndAmount()).isEqualByComparingTo(BigDecimal.ZERO);
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
        sub1.setDebitCredit(com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.DEBIT);

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
        sub.setDebitCredit(com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.DEBIT);
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

    @Test
    @DisplayName("数据库行项目配置驱动资产负债表生成与平衡校验测试")
    void testGenerateBalanceSheetByDatabaseMappings() {
        LocalDate date = LocalDate.of(2026, 3, 31);
        BalanceSheetQueryRequest req = new BalanceSheetQueryRequest();
        req.setAccountingDate(date);

        AccountSubjectPO cashSub = new AccountSubjectPO();
        cashSub.setSubjectCode("1002");
        cashSub.setSubjectName("银行存款");

        AccountSubjectPO capitalSub = new AccountSubjectPO();
        capitalSub.setSubjectCode("4001");
        capitalSub.setSubjectName("实收资本");

        when(subjectRepository.selectAllSubjects()).thenReturn(List.of(cashSub, capitalSub));

        AccountBalancePO b1 = new AccountBalancePO();
        b1.setSubjectCode("1002");
        b1.setEndBalance(new BigDecimal("8000.00"));

        AccountBalancePO b2 = new AccountBalancePO();
        b2.setSubjectCode("4001");
        b2.setEndBalance(new BigDecimal("8000.00"));

        when(accountBalanceRepository.selectByDate(date)).thenReturn(List.of(b1, b2));
        when(accountBalanceRepository.selectByDate(LocalDate.of(2026, 1, 1))).thenReturn(List.of());

        // 模拟数据库配置项
        FinancialReportItemMappingPO m1 = new FinancialReportItemMappingPO();
        m1.setItemCode("ASSET_CURRENT_HEADER");
        m1.setItemName("流动资产：");
        m1.setItemCategory(ReportCategoryEnum.CURRENT_ASSET);
        m1.setLineNo(1);
        m1.setDisplayLevel(ReportDisplayLevelEnum.HEADER);

        FinancialReportItemMappingPO m2 = new FinancialReportItemMappingPO();
        m2.setItemCode("ASSET_CASH");
        m2.setItemName("货币资金");
        m2.setItemCategory(ReportCategoryEnum.CURRENT_ASSET);
        m2.setLineNo(2);
        m2.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        m2.setSubjectCodes("1002");
        m2.setCalcOperator(ReportCalcOperatorEnum.ADD);

        FinancialReportItemMappingPO m3 = new FinancialReportItemMappingPO();
        m3.setItemCode("ASSET_CURRENT_TOTAL");
        m3.setItemName("流动资产合计");
        m3.setItemCategory(ReportCategoryEnum.CURRENT_ASSET);
        m3.setLineNo(9);
        m3.setDisplayLevel(ReportDisplayLevelEnum.TOTAL);

        FinancialReportItemMappingPO m4 = new FinancialReportItemMappingPO();
        m4.setItemCode("ASSET_TOTAL");
        m4.setItemName("资产总计");
        m4.setItemCategory(ReportCategoryEnum.ASSET_TOTAL);
        m4.setLineNo(14);
        m4.setDisplayLevel(ReportDisplayLevelEnum.TOTAL);

        FinancialReportItemMappingPO m5 = new FinancialReportItemMappingPO();
        m5.setItemCode("EQUITY_HEADER");
        m5.setItemName("所有者权益：");
        m5.setItemCategory(ReportCategoryEnum.EQUITY);
        m5.setLineNo(70);
        m5.setDisplayLevel(ReportDisplayLevelEnum.HEADER);

        FinancialReportItemMappingPO m6 = new FinancialReportItemMappingPO();
        m6.setItemCode("EQUITY_CAPITAL");
        m6.setItemName("实收资本");
        m6.setItemCategory(ReportCategoryEnum.EQUITY);
        m6.setLineNo(71);
        m6.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        m6.setSubjectCodes("4001");
        m6.setCalcOperator(ReportCalcOperatorEnum.ADD);

        FinancialReportItemMappingPO m7 = new FinancialReportItemMappingPO();
        m7.setItemCode("EQUITY_TOTAL");
        m7.setItemName("所有者权益合计");
        m7.setItemCategory(ReportCategoryEnum.EQUITY);
        m7.setLineNo(75);
        m7.setDisplayLevel(ReportDisplayLevelEnum.TOTAL);

        FinancialReportItemMappingPO m8 = new FinancialReportItemMappingPO();
        m8.setItemCode("LIAB_EQUITY_TOTAL");
        m8.setItemName("负债和所有者权益总计");
        m8.setItemCategory(ReportCategoryEnum.LIAB_EQUITY_TOTAL);
        m8.setLineNo(76);
        m8.setDisplayLevel(ReportDisplayLevelEnum.TOTAL);

        when(mappingRepository.selectEnabledByReportType(ReportTypeEnum.BALANCE_SHEET))
                .thenReturn(List.of(m1, m2, m3, m4, m5, m6, m7, m8));

        BalanceSheetResponse resp = domainService.generateBalanceSheet(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getBalanced()).isTrue();
        assertThat(resp.getTotalAssetEnd()).isEqualByComparingTo(new BigDecimal("8000.00"));
        assertThat(resp.getTotalLiabilityAndEquityEnd()).isEqualByComparingTo(new BigDecimal("8000.00"));
    }

    @Test
    @DisplayName("利润表-通过数据库行项配置与动态SpEL公式引擎驱动生成")
    void testGenerateIncomeStatementByMappingsWithDynamicFormula() {
        IncomeStatementQueryRequest req = new IncomeStatementQueryRequest();
        req.setYear(2026);
        req.setMonth(3);

        LocalDate monthStart = LocalDate.of(2026, 3, 1);
        LocalDate monthEnd = LocalDate.of(2026, 3, 31);
        LocalDate yearStart = LocalDate.of(2026, 1, 1);

        // 主营业务收入 6001 贷方 100000.00 (收益类净发生额为 100000.00)
        AccountBalancePO bRev = new AccountBalancePO();
        bRev.setSubjectCode("6001");
        bRev.setCreditAmount(new BigDecimal("100000.00"));
        bRev.setDebitAmount(BigDecimal.ZERO);

        // 主营业务成本 6401 借方 60000.00 (成本费用类借方发生额为 60000.00)
        AccountBalancePO bCost = new AccountBalancePO();
        bCost.setSubjectCode("6401");
        bCost.setDebitAmount(new BigDecimal("60000.00"));
        bCost.setCreditAmount(BigDecimal.ZERO);

        // 所得税费用 6801 借方 10000.00
        AccountBalancePO bTax = new AccountBalancePO();
        bTax.setSubjectCode("6801");
        bTax.setDebitAmount(new BigDecimal("10000.00"));
        bTax.setCreditAmount(BigDecimal.ZERO);

        when(accountBalanceRepository.selectByDateRange(monthStart, monthEnd)).thenReturn(List.of(bRev, bCost, bTax));
        when(accountBalanceRepository.selectByDateRange(yearStart, monthEnd)).thenReturn(List.of(bRev, bCost, bTax));
        when(accountBalanceRepository.selectByDateRange(LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31))).thenReturn(List.of());

        // 模拟配置项驱动
        FinancialReportItemMappingPO mRev = new FinancialReportItemMappingPO();
        mRev.setItemCode(ReportItemConstants.REV_OPERATING);
        mRev.setItemName("一、营业收入");
        mRev.setItemCategory(ReportCategoryEnum.OPERATING_REVENUE);
        mRev.setLineNo(1);
        mRev.setDisplayLevel(ReportDisplayLevelEnum.HEADER);
        mRev.setSubjectCodes("6001,6051");
        mRev.setCalcOperator(ReportCalcOperatorEnum.ADD);

        FinancialReportItemMappingPO mCost = new FinancialReportItemMappingPO();
        mCost.setItemCode(ReportItemConstants.COST_OPERATING);
        mCost.setItemName("  减：营业成本");
        mCost.setItemCategory(ReportCategoryEnum.OPERATING_COST);
        mCost.setLineNo(2);
        mCost.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        mCost.setSubjectCodes("6401,6402");
        mCost.setCalcOperator(ReportCalcOperatorEnum.ADD);

        FinancialReportItemMappingPO mOpProfit = new FinancialReportItemMappingPO();
        mOpProfit.setItemCode(ReportItemConstants.PROFIT_OPERATING);
        mOpProfit.setItemName("二、营业利润");
        mOpProfit.setItemCategory(ReportCategoryEnum.PROFIT);
        mOpProfit.setLineNo(9);
        mOpProfit.setDisplayLevel(ReportDisplayLevelEnum.HEADER);
        mOpProfit.setCalcOperator(ReportCalcOperatorEnum.FORMULA);
        mOpProfit.setFormulaExpr("REV_OPERATING - COST_OPERATING");

        FinancialReportItemMappingPO mTotalProfit = new FinancialReportItemMappingPO();
        mTotalProfit.setItemCode(ReportItemConstants.PROFIT_TOTAL);
        mTotalProfit.setItemName("三、利润总额");
        mTotalProfit.setItemCategory(ReportCategoryEnum.PROFIT);
        mTotalProfit.setLineNo(12);
        mTotalProfit.setDisplayLevel(ReportDisplayLevelEnum.HEADER);
        mTotalProfit.setCalcOperator(ReportCalcOperatorEnum.FORMULA);
        mTotalProfit.setFormulaExpr("PROFIT_OPERATING");

        FinancialReportItemMappingPO mTaxExp = new FinancialReportItemMappingPO();
        mTaxExp.setItemCode(ReportItemConstants.EXP_INCOME_TAX);
        mTaxExp.setItemName("  减：所得税费用");
        mTaxExp.setItemCategory(ReportCategoryEnum.EXPENSE);
        mTaxExp.setLineNo(13);
        mTaxExp.setDisplayLevel(ReportDisplayLevelEnum.DETAIL);
        mTaxExp.setSubjectCodes("6801");
        mTaxExp.setCalcOperator(ReportCalcOperatorEnum.ADD);

        FinancialReportItemMappingPO mNetProfit = new FinancialReportItemMappingPO();
        mNetProfit.setItemCode(ReportItemConstants.PROFIT_NET);
        mNetProfit.setItemName("四、净利润");
        mNetProfit.setItemCategory(ReportCategoryEnum.PROFIT);
        mNetProfit.setLineNo(14);
        mNetProfit.setDisplayLevel(ReportDisplayLevelEnum.TOTAL);
        mNetProfit.setCalcOperator(ReportCalcOperatorEnum.FORMULA);
        mNetProfit.setFormulaExpr("PROFIT_TOTAL - EXP_INCOME_TAX");

        when(mappingRepository.selectEnabledByReportType(ReportTypeEnum.INCOME_STATEMENT))
                .thenReturn(List.of(mRev, mCost, mOpProfit, mTotalProfit, mTaxExp, mNetProfit));

        IncomeStatementResponse resp = domainService.generateIncomeStatement(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getKpi()).isNotNull();
        // 营业收入 100,000, 成本 60,000, 营业利润 40,000, 利润总额 40,000, 所得税 10,000, 净利润 30,000
        assertThat(resp.getKpi().getRevenueMonth()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(resp.getKpi().getOperatingProfitMonth()).isEqualByComparingTo(new BigDecimal("40000.00"));
        assertThat(resp.getKpi().getNetProfitMonth()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(resp.getItems()).hasSize(6);
    }
}
