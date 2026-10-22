package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ClosingMethodEnum;
import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.request.BalanceSheetQueryRequest;
import com.kltb.accounting.api.request.GeneralLedgerQueryRequest;
import com.kltb.accounting.api.request.IncomeStatementQueryRequest;
import com.kltb.accounting.api.request.SubsidiaryLedgerQueryRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.SubjectCategoryEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.util.FinancialAmountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ZERO;

/**
 * 财务报表核心领域服务
 * <p>
 * 遵守金融核心开发规范：
 * 1. 严禁负数运算，金额与余额保持非负；
 * 2. 严禁 SQL 计算余额，基于日余额明细与过账分录在内存通过 BigDecimal 聚合；
 * 3. 严格使用 compareTo 比较 BigDecimal；
 * 4. 落地标准会计准则四大核心报表（资产负债表、利润表、科目总账、科目明细账）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportDomainService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final SubjectRepository subjectRepository;

    /**
     * 中国《企业会计准则》（CAS）财务报表标准推荐科目代码映射规范定义
     * <p>
     * 注：各编码源自财政部《企业会计准则——应用指南》会计科目规范，非国际标准（IFRS）。
     * 此处统一定义收拢，杜绝业务方法内离散硬编码魔法值。未来将平滑演进为 t_financial_report_item_mapping 数据库配置驱动。
     */
    public static final class CAS_SUBJECTS {
        // 资产类 (1xxx)
        public static final String CASH_ON_HAND = "1001";          // 库存现金
        public static final String BANK_DEPOSIT = "1002";          // 银行存款
        public static final String OTHER_MONETARY = "1012";        // 其他货币资金
        public static final String NOTES_RECEIVABLE = "1121";      // 应收票据
        public static final String ACCOUNTS_RECEIVABLE = "1122";   // 应收账款
        public static final String ADVANCE_PAYMENT = "1123";       // 预付款项
        public static final String OTHER_RECEIVABLES = "1221";     // 其他应收款
        public static final String RAW_MATERIALS = "1403";         // 原材料
        public static final String MERCHANDISE = "1405";           // 库存商品
        public static final String GOODS_IN_TRANSIT = "1406";      // 发出商品
        public static final String FIXED_ASSETS = "1601";          // 固定资产
        public static final String ACCUM_DEPRECIATION = "1602";    // 累计折旧
        public static final String INTANGIBLE_ASSETS = "1701";     // 无形资产

        // 负债类 (2xxx)
        public static final String SHORT_TERM_LOAN = "2001";       // 短期借款
        public static final String ACCOUNTS_PAYABLE = "2202";      // 应付账款
        public static final String ADVANCE_RECEIPTS = "2203";      // 预收款项
        public static final String PAYROLL_PAYABLE = "2211";       // 应付职工薪酬
        public static final String TAXES_PAYABLE = "2221";         // 应交税费
        public static final String OTHER_PAYABLES = "2241";        // 其他应付款
        public static final String LONG_TERM_LOAN = "2501";        // 长期借款

        // 所有者权益类 (4xxx)
        public static final String PAID_IN_CAPITAL = "4001";       // 实收资本
        public static final String CAPITAL_RESERVE = "4002";       // 资本公积
        public static final String SURPLUS_RESERVE = "4101";       // 盈余公积
        public static final String CURRENT_YEAR_PROFIT = "4103";   // 本年利润
        public static final String PROFIT_DISTRIBUTION = "4104";   // 利润分配

        // 损益类 (6xxx)
        public static final String REVENUE_MAIN = "6001";          // 主营业务收入
        public static final String REVENUE_OTHER = "6051";         // 其他业务收入
        public static final String COST_MAIN = "6401";             // 主营业务成本
        public static final String COST_OTHER = "6402";            // 其他业务成本
        public static final String TAX_AND_SURCHARGE = "6403";     // 税金及附加
        public static final String SELLING_EXPENSE = "6601";       // 销售费用
        public static final String ADMIN_EXPENSE = "6602";         // 管理费用
        public static final String FINANCIAL_EXPENSE = "6603";     // 财务费用
        public static final String ASSET_IMPAIRMENT = "6701";      // 资产减值损失
        public static final String FAIR_VALUE_CHANGE = "6101";     // 公允价值变动损益
        public static final String INVESTMENT_INCOME = "6111";     // 投资收益
        public static final String NON_OPERATING_INCOME = "6301";  // 营业外收入
        public static final String NON_OPERATING_EXPENSE = "6711"; // 营业外支出
        public static final String INCOME_TAX_EXPENSE = "6801";    // 所得税费用
    }

    // =========================================================================
    // 1. 资产负债表（Balance Sheet）
    // =========================================================================

    public BalanceSheetResponse generateBalanceSheet(BalanceSheetQueryRequest request) {
        LocalDate accountingDate = request.getAccountingDate();
        LocalDate compareDate = request.getCompareDate() != null
                ? request.getCompareDate()
                : LocalDate.of(accountingDate.getYear(), 1, 1);

        // 结账核算模式：REPORT(表结法，动态汇算损益，推荐日常查看) / ACCOUNT(账结法，凭证驱动，审计对账)
        ClosingMethodEnum closingMethod = ClosingMethodEnum.fromCode(request.getClosingMethod());
        boolean isReportClosing = closingMethod.isReport();

        // 1. 获取所有科目定义并构建映射
        List<AccountSubjectPO> allSubjects = subjectRepository.selectAllSubjects();
        Map<String, AccountSubjectPO> subjectMap = allSubjects.stream()
                .collect(Collectors.toMap(AccountSubjectPO::getSubjectCode, s -> s, (s1, s2) -> s1));

        // 2. 取报告期当日日余额以及年初/对比日日余额
        List<AccountBalancePO> endBalances = accountBalanceRepository.selectByDate(accountingDate);
        List<AccountBalancePO> beginBalances = accountBalanceRepository.selectByDate(compareDate);

        Map<String, BigDecimal> endAmountMap = aggregateBalancesBySubject(endBalances);
        Map<String, BigDecimal> beginAmountMap = aggregateBalancesBySubject(beginBalances);

        // 动态汇算未结转损益净额（6类科目贷方收益 - 借方支出）
        BigDecimal unclosedProfitEnd = calculateUnclosedProfit(endBalances, subjectMap);
        BigDecimal unclosedProfitBegin = calculateUnclosedProfit(beginBalances, subjectMap);

        // 3. 构建资产端行项目
        List<BalanceSheetItemResponse> assetItems = new ArrayList<>();
        BigDecimal currentAssetEnd = ZERO;
        BigDecimal currentAssetBegin = ZERO;

        // 货币资金 (1001, 1002, 1012)
        BigDecimal cashEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.CASH_ON_HAND, CAS_SUBJECTS.BANK_DEPOSIT, CAS_SUBJECTS.OTHER_MONETARY);
        BigDecimal cashBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.CASH_ON_HAND, CAS_SUBJECTS.BANK_DEPOSIT, CAS_SUBJECTS.OTHER_MONETARY);
        assetItems.add(buildItem(1, "流动资产：", 1, "", null, null));
        BalanceSheetItemResponse cashItem = buildItem(2, "  货币资金", 2, "1001,1002,1012", cashEnd, cashBegin);
        List<BalanceSheetSubjectDetailResponse> cashDetails = collectSubjectDetails(endAmountMap, beginAmountMap, subjectMap, CAS_SUBJECTS.CASH_ON_HAND, CAS_SUBJECTS.BANK_DEPOSIT, CAS_SUBJECTS.OTHER_MONETARY);
        if (!cashDetails.isEmpty()) {
            cashItem.setDetailSubjects(cashDetails);
        }
        assetItems.add(cashItem);
        currentAssetEnd = currentAssetEnd.add(cashEnd);
        currentAssetBegin = currentAssetBegin.add(cashBegin);

        // 应收票据 (1121)
        BigDecimal noteRecEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.NOTES_RECEIVABLE);
        BigDecimal noteRecBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.NOTES_RECEIVABLE);
        assetItems.add(buildItem(3, "  应收票据", 2, CAS_SUBJECTS.NOTES_RECEIVABLE, noteRecEnd, noteRecBegin));
        currentAssetEnd = currentAssetEnd.add(noteRecEnd);
        currentAssetBegin = currentAssetBegin.add(noteRecBegin);

        // 应收账款 (1122)
        BigDecimal arEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.ACCOUNTS_RECEIVABLE);
        BigDecimal arBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.ACCOUNTS_RECEIVABLE);
        assetItems.add(buildItem(4, "  应收账款", 2, CAS_SUBJECTS.ACCOUNTS_RECEIVABLE, arEnd, arBegin));
        currentAssetEnd = currentAssetEnd.add(arEnd);
        currentAssetBegin = currentAssetBegin.add(arBegin);

        // 预付款项 (1123)
        BigDecimal prepayEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.ADVANCE_PAYMENT);
        BigDecimal prepayBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.ADVANCE_PAYMENT);
        assetItems.add(buildItem(5, "  预付款项", 2, CAS_SUBJECTS.ADVANCE_PAYMENT, prepayEnd, prepayBegin));
        currentAssetEnd = currentAssetEnd.add(prepayEnd);
        currentAssetBegin = currentAssetBegin.add(prepayBegin);

        // 其他应收款 (1221)
        BigDecimal otherRecEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.OTHER_RECEIVABLES);
        BigDecimal otherRecBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.OTHER_RECEIVABLES);
        assetItems.add(buildItem(6, "  其他应收款", 2, CAS_SUBJECTS.OTHER_RECEIVABLES, otherRecEnd, otherRecBegin));
        currentAssetEnd = currentAssetEnd.add(otherRecEnd);
        currentAssetBegin = currentAssetBegin.add(otherRecBegin);

        // 存货 (1403, 1405, 1406)
        BigDecimal invEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.RAW_MATERIALS, CAS_SUBJECTS.MERCHANDISE, CAS_SUBJECTS.GOODS_IN_TRANSIT);
        BigDecimal invBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.RAW_MATERIALS, CAS_SUBJECTS.MERCHANDISE, CAS_SUBJECTS.GOODS_IN_TRANSIT);
        assetItems.add(buildItem(7, "  存货", 2, "1403,1405,1406", invEnd, invBegin));
        currentAssetEnd = currentAssetEnd.add(invEnd);
        currentAssetBegin = currentAssetBegin.add(invBegin);

        // 其他流动资产 (1011, 1101, 1231 等其他 1 开头的流动资产科目)
        BigDecimal otherCurrentAssetEnd = sumPrefixExcept(endAmountMap, "1", Set.of(CAS_SUBJECTS.CASH_ON_HAND, CAS_SUBJECTS.BANK_DEPOSIT, CAS_SUBJECTS.OTHER_MONETARY, CAS_SUBJECTS.NOTES_RECEIVABLE, CAS_SUBJECTS.ACCOUNTS_RECEIVABLE, CAS_SUBJECTS.ADVANCE_PAYMENT, CAS_SUBJECTS.OTHER_RECEIVABLES, CAS_SUBJECTS.RAW_MATERIALS, CAS_SUBJECTS.MERCHANDISE, CAS_SUBJECTS.GOODS_IN_TRANSIT, CAS_SUBJECTS.FIXED_ASSETS, CAS_SUBJECTS.ACCUM_DEPRECIATION, CAS_SUBJECTS.INTANGIBLE_ASSETS));
        BigDecimal otherCurrentAssetBegin = sumPrefixExcept(beginAmountMap, "1", Set.of(CAS_SUBJECTS.CASH_ON_HAND, CAS_SUBJECTS.BANK_DEPOSIT, CAS_SUBJECTS.OTHER_MONETARY, CAS_SUBJECTS.NOTES_RECEIVABLE, CAS_SUBJECTS.ACCOUNTS_RECEIVABLE, CAS_SUBJECTS.ADVANCE_PAYMENT, CAS_SUBJECTS.OTHER_RECEIVABLES, CAS_SUBJECTS.RAW_MATERIALS, CAS_SUBJECTS.MERCHANDISE, CAS_SUBJECTS.GOODS_IN_TRANSIT, CAS_SUBJECTS.FIXED_ASSETS, CAS_SUBJECTS.ACCUM_DEPRECIATION, CAS_SUBJECTS.INTANGIBLE_ASSETS));
        assetItems.add(buildItem(8, "  其他流动资产", 2, "1999", otherCurrentAssetEnd, otherCurrentAssetBegin));
        currentAssetEnd = currentAssetEnd.add(otherCurrentAssetEnd);
        currentAssetBegin = currentAssetBegin.add(otherCurrentAssetBegin);

        // 流动资产合计
        assetItems.add(buildItem(9, "流动资产合计", 3, "", currentAssetEnd, currentAssetBegin));

        // 非流动资产 (固定资产 1601/1602, 无形资产 1701)
        BigDecimal nonCurrentAssetEnd = ZERO;
        BigDecimal nonCurrentAssetBegin = ZERO;
        assetItems.add(buildItem(10, "非流动资产：", 1, "", null, null));

        BigDecimal fixEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.FIXED_ASSETS, CAS_SUBJECTS.ACCUM_DEPRECIATION);
        BigDecimal fixBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.FIXED_ASSETS, CAS_SUBJECTS.ACCUM_DEPRECIATION);
        assetItems.add(buildItem(11, "  固定资产", 2, "1601,1602", fixEnd, fixBegin));
        nonCurrentAssetEnd = nonCurrentAssetEnd.add(fixEnd);
        nonCurrentAssetBegin = nonCurrentAssetBegin.add(fixBegin);

        BigDecimal intanEnd = sumSubjects(endAmountMap, CAS_SUBJECTS.INTANGIBLE_ASSETS);
        BigDecimal intanBegin = sumSubjects(beginAmountMap, CAS_SUBJECTS.INTANGIBLE_ASSETS);
        assetItems.add(buildItem(12, "  无形资产", 2, CAS_SUBJECTS.INTANGIBLE_ASSETS, intanEnd, intanBegin));
        nonCurrentAssetEnd = nonCurrentAssetEnd.add(intanEnd);
        nonCurrentAssetBegin = nonCurrentAssetBegin.add(intanBegin);

        assetItems.add(buildItem(13, "非流动资产合计", 3, "", nonCurrentAssetEnd, nonCurrentAssetBegin));

        // 资产总计
        BigDecimal totalAssetEnd = currentAssetEnd.add(nonCurrentAssetEnd);
        BigDecimal totalAssetBegin = currentAssetBegin.add(nonCurrentAssetBegin);
        assetItems.add(buildItem(14, "资产总计", 3, "", totalAssetEnd, totalAssetBegin));

        // 4. 构建负债及所有者权益端
        List<BalanceSheetItemResponse> liabEquityItems = new ArrayList<>();
        BigDecimal currentLiabEnd = ZERO;
        BigDecimal currentLiabBegin = ZERO;

        liabEquityItems.add(buildItem(51, "流动负债：", 1, "", null, null));

        // 短期借款 (2001)
        BigDecimal stLoanEnd = sumSubjects(endAmountMap, "2001");
        BigDecimal stLoanBegin = sumSubjects(beginAmountMap, "2001");
        liabEquityItems.add(buildItem(52, "  短期借款", 2, "2001", stLoanEnd, stLoanBegin));
        currentLiabEnd = currentLiabEnd.add(stLoanEnd);
        currentLiabBegin = currentLiabBegin.add(stLoanBegin);

        // 应付账款 (2202)
        BigDecimal apEnd = sumSubjects(endAmountMap, "2202");
        BigDecimal apBegin = sumSubjects(beginAmountMap, "2202");
        liabEquityItems.add(buildItem(53, "  应付账款", 2, "2202", apEnd, apBegin));
        currentLiabEnd = currentLiabEnd.add(apEnd);
        currentLiabBegin = currentLiabBegin.add(apBegin);

        // 预收款项 (2203)
        BigDecimal advRecEnd = sumSubjects(endAmountMap, "2203");
        BigDecimal advRecBegin = sumSubjects(beginAmountMap, "2203");
        liabEquityItems.add(buildItem(54, "  预收款项", 2, "2203", advRecEnd, advRecBegin));
        currentLiabEnd = currentLiabEnd.add(advRecEnd);
        currentLiabBegin = currentLiabBegin.add(advRecBegin);

        // 应付职工薪酬 (2211)
        BigDecimal payrollEnd = sumSubjects(endAmountMap, "2211");
        BigDecimal payrollBegin = sumSubjects(beginAmountMap, "2211");
        liabEquityItems.add(buildItem(55, "  应付职工薪酬", 2, "2211", payrollEnd, payrollBegin));
        currentLiabEnd = currentLiabEnd.add(payrollEnd);
        currentLiabBegin = currentLiabBegin.add(payrollBegin);

        // 应交税费 (2221)
        BigDecimal taxEnd = sumSubjects(endAmountMap, "2221");
        BigDecimal taxBegin = sumSubjects(beginAmountMap, "2221");
        liabEquityItems.add(buildItem(56, "  应交税费", 2, "2221", taxEnd, taxBegin));
        currentLiabEnd = currentLiabEnd.add(taxEnd);
        currentLiabBegin = currentLiabBegin.add(taxBegin);

        // 其他应付款 (2241)
        BigDecimal otherPayEnd = sumSubjects(endAmountMap, "2241");
        BigDecimal otherPayBegin = sumSubjects(beginAmountMap, "2241");
        liabEquityItems.add(buildItem(57, "  其他应付款", 2, "2241", otherPayEnd, otherPayBegin));
        currentLiabEnd = currentLiabEnd.add(otherPayEnd);
        currentLiabBegin = currentLiabBegin.add(otherPayBegin);

        // 其他流动负债
        BigDecimal otherLiabEnd = sumPrefixExcept(endAmountMap, "2", Set.of("2001", "2202", "2203", "2211", "2221", "2241", "2501"));
        BigDecimal otherLiabBegin = sumPrefixExcept(beginAmountMap, "2", Set.of("2001", "2202", "2203", "2211", "2221", "2241", "2501"));
        liabEquityItems.add(buildItem(58, "  其他流动负债", 2, "2999", otherLiabEnd, otherLiabBegin));
        currentLiabEnd = currentLiabEnd.add(otherLiabEnd);
        currentLiabBegin = currentLiabBegin.add(otherLiabBegin);

        liabEquityItems.add(buildItem(59, "流动负债合计", 3, "", currentLiabEnd, currentLiabBegin));

        // 非流动负债 (2501 长期借款)
        BigDecimal nonCurrentLiabEnd = ZERO;
        BigDecimal nonCurrentLiabBegin = ZERO;
        liabEquityItems.add(buildItem(60, "非流动负债：", 1, "", null, null));
        BigDecimal ltLoanEnd = sumSubjects(endAmountMap, "2501");
        BigDecimal ltLoanBegin = sumSubjects(beginAmountMap, "2501");
        liabEquityItems.add(buildItem(61, "  长期借款", 2, "2501", ltLoanEnd, ltLoanBegin));
        nonCurrentLiabEnd = nonCurrentLiabEnd.add(ltLoanEnd);
        nonCurrentLiabBegin = nonCurrentLiabBegin.add(ltLoanBegin);

        liabEquityItems.add(buildItem(62, "非流动负债合计", 3, "", nonCurrentLiabEnd, nonCurrentLiabBegin));

        BigDecimal totalLiabEnd = currentLiabEnd.add(nonCurrentLiabEnd);
        BigDecimal totalLiabBegin = currentLiabBegin.add(nonCurrentLiabBegin);
        liabEquityItems.add(buildItem(63, "负债合计", 3, "", totalLiabEnd, totalLiabBegin));

        // 所有者权益 (4001, 4002, 4101, 4103, 4104)
        BigDecimal equityEnd = ZERO;
        BigDecimal equityBegin = ZERO;
        liabEquityItems.add(buildItem(70, "所有者权益：", 1, "", null, null));

        BigDecimal capEnd = sumSubjects(endAmountMap, "4001");
        BigDecimal capBegin = sumSubjects(beginAmountMap, "4001");
        liabEquityItems.add(buildItem(71, "  实收资本(或股本)", 2, "4001", capEnd, capBegin));
        equityEnd = equityEnd.add(capEnd);
        equityBegin = equityBegin.add(capBegin);

        BigDecimal resCapEnd = sumSubjects(endAmountMap, "4002");
        BigDecimal resCapBegin = sumSubjects(beginAmountMap, "4002");
        liabEquityItems.add(buildItem(72, "  资本公积", 2, "4002", resCapEnd, resCapBegin));
        equityEnd = equityEnd.add(resCapEnd);
        equityBegin = equityBegin.add(resCapBegin);

        BigDecimal surpEnd = sumSubjects(endAmountMap, "4101");
        BigDecimal surpBegin = sumSubjects(beginAmountMap, "4101");
        liabEquityItems.add(buildItem(73, "  盈余公积", 2, "4101", surpEnd, surpBegin));
        equityEnd = equityEnd.add(surpEnd);
        equityBegin = equityBegin.add(surpBegin);

        // 未分配利润（4104 利润分配 + 4103 本年利润，若为表结法动态纳入未结转净损益）
        BigDecimal p4103End = sumSubjects(endAmountMap, "4103");
        BigDecimal p4103Begin = sumSubjects(beginAmountMap, "4103");
        BigDecimal p4104End = sumSubjects(endAmountMap, "4104");
        BigDecimal p4104Begin = sumSubjects(beginAmountMap, "4104");

        BigDecimal undistEnd = p4103End.add(p4104End);
        BigDecimal undistBegin = p4103Begin.add(p4104Begin);

        if (isReportClosing) {
            undistEnd = undistEnd.add(unclosedProfitEnd);
            undistBegin = undistBegin.add(unclosedProfitBegin);
        }

        // 组装未分配利润明细科目清单
        List<BalanceSheetSubjectDetailResponse> undistDetails = new ArrayList<>();
        if (p4103End.compareTo(ZERO) != 0 || p4103Begin.compareTo(ZERO) != 0) {
            undistDetails.add(BalanceSheetSubjectDetailResponse.builder()
                    .subjectCode("4103")
                    .subjectName("本年利润 (账面已结转)")
                    .endAmount(p4103End)
                    .beginAmount(p4103Begin)
                    .build());
        }
        if (p4104End.compareTo(ZERO) != 0 || p4104Begin.compareTo(ZERO) != 0) {
            undistDetails.add(BalanceSheetSubjectDetailResponse.builder()
                    .subjectCode("4104")
                    .subjectName("利润分配")
                    .endAmount(p4104End)
                    .beginAmount(p4104Begin)
                    .build());
        }
        if (isReportClosing && (unclosedProfitEnd.compareTo(ZERO) != 0 || unclosedProfitBegin.compareTo(ZERO) != 0)) {
            undistDetails.add(BalanceSheetSubjectDetailResponse.builder()
                    .subjectCode("999999")
                    .subjectName("当期未结转净损益 (表结法动态汇算)")
                    .endAmount(unclosedProfitEnd)
                    .beginAmount(unclosedProfitBegin)
                    .build());
        }

        BalanceSheetItemResponse undistItem = buildItem(74, "  未分配利润", 2, "4103,4104", undistEnd, undistBegin);
        if (!undistDetails.isEmpty()) {
            undistItem.setDetailSubjects(undistDetails);
        }
        liabEquityItems.add(undistItem);
        equityEnd = equityEnd.add(undistEnd);
        equityBegin = equityBegin.add(undistBegin);

        liabEquityItems.add(buildItem(75, "所有者权益合计", 3, "", equityEnd, equityBegin));

        // 负债和所有者权益总计
        BigDecimal totalLiabEquityEnd = totalLiabEnd.add(equityEnd);
        BigDecimal totalLiabEquityBegin = totalLiabBegin.add(equityBegin);
        liabEquityItems.add(buildItem(76, "负债和所有者权益总计", 3, "", totalLiabEquityEnd, totalLiabEquityBegin));

        // 5. 试算平衡校验
        boolean balanced = totalAssetEnd.compareTo(totalLiabEquityEnd) == 0;
        BigDecimal diffAmount = totalAssetEnd.subtract(totalLiabEquityEnd).abs();

        return BalanceSheetResponse.builder()
                .accountingDate(accountingDate)
                .compareDate(compareDate)
                .unitName(Constants.DEFAULT_UNIT_NAME)
                .currency(Constants.DEFAULT_CURRENCY)
                .closingMethod(closingMethod.getCode())
                .unclosedProfitEnd(unclosedProfitEnd)
                .unclosedProfitBegin(unclosedProfitBegin)
                .balanced(balanced)
                .diffAmount(diffAmount)
                .totalAssetEnd(totalAssetEnd)
                .totalAssetBegin(totalAssetBegin)
                .totalLiabilityAndEquityEnd(totalLiabEquityEnd)
                .totalLiabilityAndEquityBegin(totalLiabEquityBegin)
                .assetItems(assetItems)
                .liabilityAndEquityItems(liabEquityItems)
                .build();
    }

    // =========================================================================
    // 2. 利润表（Income Statement）
    // =========================================================================

    public IncomeStatementResponse generateIncomeStatement(IncomeStatementQueryRequest request) {
        LocalDate monthStart;
        LocalDate monthEnd;
        LocalDate yearStart;
        String periodDesc;
        Map<String, BigDecimal> compareNetMap;

        if (request.getStartDate() != null && request.getEndDate() != null) {
            // 自定义期间模式
            monthStart = request.getStartDate();
            monthEnd = request.getEndDate();
            yearStart = LocalDate.of(monthStart.getYear(), 1, 1);
            periodDesc = monthStart + " ~ " + monthEnd;

            LocalDate cmpStart = request.getCompareStartDate() != null ? request.getCompareStartDate() : monthStart.minusYears(1);
            LocalDate cmpEnd = request.getCompareEndDate() != null ? request.getCompareEndDate() : monthEnd.minusYears(1);
            List<AccountBalancePO> prevBalances = accountBalanceRepository.selectByDateRange(cmpStart, cmpEnd);
            compareNetMap = aggregateNetAmounts(prevBalances);
        } else {
            // 标准月度模式
            int year = request.getYear() != null ? request.getYear() : LocalDate.now().getYear();
            int month = request.getMonth() != null ? request.getMonth() : LocalDate.now().getMonthValue();
            monthStart = LocalDate.of(year, month, 1);
            monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
            yearStart = LocalDate.of(year, 1, 1);
            periodDesc = year + "年1-" + month + "月";

            if (Integer.valueOf(2).equals(request.getCompareType())) {
                LocalDate prevMonth = monthStart.minusMonths(1);
                LocalDate prevMonthEnd = prevMonth.with(TemporalAdjusters.lastDayOfMonth());
                List<AccountBalancePO> prevBalances = accountBalanceRepository.selectByDateRange(prevMonth, prevMonthEnd);
                compareNetMap = aggregateNetAmounts(prevBalances);
            } else {
                LocalDate prevYearStart = monthStart.minusYears(1);
                LocalDate prevYearEnd = monthEnd.minusYears(1);
                List<AccountBalancePO> prevBalances = accountBalanceRepository.selectByDateRange(prevYearStart, prevYearEnd);
                compareNetMap = aggregateNetAmounts(prevBalances);
            }
        }

        // 1. 获取本期与本年日余额发生额列表
        List<AccountBalancePO> monthBalances = accountBalanceRepository.selectByDateRange(monthStart, monthEnd);
        List<AccountBalancePO> yearBalances = accountBalanceRepository.selectByDateRange(yearStart, monthEnd);

        // 计算各科目的净发生额（收入类贷方-借方，成本费用类借方-贷方）
        Map<String, BigDecimal> monthNetMap = aggregateNetAmounts(monthBalances);
        Map<String, BigDecimal> yearNetMap = aggregateNetAmounts(yearBalances);

        List<IncomeStatementItemResponse> items = new ArrayList<>();

        // 一、营业收入 (6001, 6051)
        BigDecimal revCur = sumNet(monthNetMap, "6001", "6051");
        BigDecimal revYear = sumNet(yearNetMap, "6001", "6051");
        BigDecimal revCmp = sumNet(compareNetMap, "6001", "6051");
        items.add(buildIncomeItem(1, "一、营业收入", 1, revCur, revYear, revCmp));

        // 减：营业成本 (6401, 6402)
        BigDecimal costCur = sumNet(monthNetMap, "6401", "6402");
        BigDecimal costYear = sumNet(yearNetMap, "6401", "6402");
        BigDecimal costCmp = sumNet(compareNetMap, "6401", "6402");
        items.add(buildIncomeItem(2, "    减：营业成本", 2, costCur, costYear, costCmp));

        // 税金及附加 (6403)
        BigDecimal taxCur = sumNet(monthNetMap, "6403");
        BigDecimal taxYear = sumNet(yearNetMap, "6403");
        BigDecimal taxCmp = sumNet(compareNetMap, "6403");
        items.add(buildIncomeItem(3, "    税金及附加", 2, taxCur, taxYear, taxCmp));

        // 销售费用 (6601)
        BigDecimal sellCur = sumNet(monthNetMap, "6601");
        BigDecimal sellYear = sumNet(yearNetMap, "6601");
        BigDecimal sellCmp = sumNet(compareNetMap, "6601");
        items.add(buildIncomeItem(4, "    销售费用", 2, sellCur, sellYear, sellCmp));

        // 管理费用 (6602)
        BigDecimal adminCur = sumNet(monthNetMap, "6602");
        BigDecimal adminYear = sumNet(yearNetMap, "6602");
        BigDecimal adminCmp = sumNet(compareNetMap, "6602");
        items.add(buildIncomeItem(5, "    管理费用", 2, adminCur, adminYear, adminCmp));

        // 研发费用 (6605)
        BigDecimal rdCur = sumNet(monthNetMap, "6605");
        BigDecimal rdYear = sumNet(yearNetMap, "6605");
        BigDecimal rdCmp = sumNet(compareNetMap, "6605");
        items.add(buildIncomeItem(6, "    研发费用", 2, rdCur, rdYear, rdCmp));

        // 财务费用 (6603)
        BigDecimal finCur = sumNet(monthNetMap, "6603");
        BigDecimal finYear = sumNet(yearNetMap, "6603");
        BigDecimal finCmp = sumNet(compareNetMap, "6603");
        items.add(buildIncomeItem(7, "    财务费用", 2, finCur, finYear, finCmp));

        // 加：投资收益 (6111)
        BigDecimal invCur = sumNet(monthNetMap, "6111");
        BigDecimal invYear = sumNet(yearNetMap, "6111");
        BigDecimal invCmp = sumNet(compareNetMap, "6111");
        items.add(buildIncomeItem(8, "    加：投资收益", 2, invCur, invYear, invCmp));

        // 二、营业利润
        BigDecimal opProfitCur = revCur.subtract(costCur).subtract(taxCur).subtract(sellCur)
                .subtract(adminCur).subtract(rdCur).subtract(finCur).add(invCur);
        BigDecimal opProfitYear = revYear.subtract(costYear).subtract(taxYear).subtract(sellYear)
                .subtract(adminYear).subtract(rdYear).subtract(finYear).add(invYear);
        BigDecimal opProfitCmp = revCmp.subtract(costCmp).subtract(taxCmp).subtract(sellCmp)
                .subtract(adminCmp).subtract(rdCmp).subtract(finCmp).add(invCmp);
        items.add(buildIncomeItem(9, "二、营业利润", 1, opProfitCur, opProfitYear, opProfitCmp));

        // 加：营业外收入 (6301)
        BigDecimal nonOpInCur = sumNet(monthNetMap, "6301");
        BigDecimal nonOpInYear = sumNet(yearNetMap, "6301");
        BigDecimal nonOpInCmp = sumNet(compareNetMap, "6301");
        items.add(buildIncomeItem(10, "    加：营业外收入", 2, nonOpInCur, nonOpInYear, nonOpInCmp));

        // 减：营业外支出 (6711)
        BigDecimal nonOpOutCur = sumNet(monthNetMap, "6711");
        BigDecimal nonOpOutYear = sumNet(yearNetMap, "6711");
        BigDecimal nonOpOutCmp = sumNet(compareNetMap, "6711");
        items.add(buildIncomeItem(11, "    减：营业外支出", 2, nonOpOutCur, nonOpOutYear, nonOpOutCmp));

        // 三、利润总额
        BigDecimal totalProfitCur = opProfitCur.add(nonOpInCur).subtract(nonOpOutCur);
        BigDecimal totalProfitYear = opProfitYear.add(nonOpInYear).subtract(nonOpOutYear);
        BigDecimal totalProfitCmp = opProfitCmp.add(nonOpInCmp).subtract(nonOpOutCmp);
        items.add(buildIncomeItem(12, "三、利润总额", 1, totalProfitCur, totalProfitYear, totalProfitCmp));

        // 减：所得税费用 (6801)
        BigDecimal incomeTaxCur = sumNet(monthNetMap, "6801");
        BigDecimal incomeTaxYear = sumNet(yearNetMap, "6801");
        BigDecimal incomeTaxCmp = sumNet(compareNetMap, "6801");
        items.add(buildIncomeItem(13, "    减：所得税费用", 2, incomeTaxCur, incomeTaxYear, incomeTaxCmp));

        // 四、净利润
        BigDecimal netProfitCur = totalProfitCur.subtract(incomeTaxCur);
        BigDecimal netProfitYear = totalProfitYear.subtract(incomeTaxYear);
        BigDecimal netProfitCmp = totalProfitCmp.subtract(incomeTaxCmp);
        items.add(buildIncomeItem(14, "四、净利润", 3, netProfitCur, netProfitYear, netProfitCmp));

        // 2. 构建顶层 KPI 摘要
        BigDecimal grossMarginRate = ZERO;
        if (revCur.compareTo(ZERO) > 0) {
            grossMarginRate = revCur.subtract(costCur).divide(revCur, 4, RoundingMode.HALF_UP).multiply(FinancialAmountUtil.PERCENT_BASE);
        }

        BigDecimal revYoY = calcRate(revCur, revCmp);
        BigDecimal netProfitYoY = calcRate(netProfitCur, netProfitCmp);

        IncomeStatementKpiResponse kpi = IncomeStatementKpiResponse.builder()
                .revenueMonth(revCur)
                .operatingProfitMonth(opProfitCur)
                .netProfitMonth(netProfitCur)
                .grossMarginRate(grossMarginRate.setScale(1, RoundingMode.HALF_UP))
                .netProfitYearTotal(netProfitYear)
                .revenueYoY(revYoY)
                .netProfitYoY(netProfitYoY)
                .build();

        return IncomeStatementResponse.builder()
                .periodDesc(periodDesc)
                .accountingDate(monthEnd)
                .currency(Constants.DEFAULT_CURRENCY)
                .unitName(Constants.DEFAULT_UNIT_NAME)
                .kpi(kpi)
                .items(items)
                .build();
    }

    // =========================================================================
    // 3. 科目总账（General Ledger）
    // =========================================================================

    public GeneralLedgerResponse generateGeneralLedger(GeneralLedgerQueryRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        // 1. 获取所有科目
        List<AccountSubjectPO> subjects = subjectRepository.selectAllSubjects();
        if (request.getSubjectLevel() != null) {
            subjects = subjects.stream()
                    .filter(s -> Objects.equals(s.getSubjectLevel().intValue(), request.getSubjectLevel()))
                    .toList();
        }
        if (request.getStartSubjectCode() != null && !request.getStartSubjectCode().isBlank()) {
            subjects = subjects.stream()
                    .filter(s -> s.getSubjectCode().compareTo(request.getStartSubjectCode()) >= 0)
                    .toList();
        }
        if (request.getEndSubjectCode() != null && !request.getEndSubjectCode().isBlank()) {
            subjects = subjects.stream()
                    .filter(s -> s.getSubjectCode().compareTo(request.getEndSubjectCode()) <= 0)
                    .toList();
        }
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            String kw = request.getKeyword().trim();
            subjects = subjects.stream()
                    .filter(s -> s.getSubjectCode().contains(kw) || s.getSubjectName().contains(kw))
                    .toList();
        }

        // 2. 查询期间内的日余额明细
        List<AccountBalancePO> balanceList = accountBalanceRepository.selectByDateRange(startDate, endDate);
        Map<String, List<AccountBalancePO>> balanceBySubject = balanceList.stream()
                .collect(Collectors.groupingBy(AccountBalancePO::getSubjectCode));

        List<GeneralLedgerItemResponse> items = new ArrayList<>();
        BigDecimal totalBeginDebit = ZERO;
        BigDecimal totalBeginCredit = ZERO;
        BigDecimal totalPeriodDebit = ZERO;
        BigDecimal totalPeriodCredit = ZERO;
        BigDecimal totalEndDebit = ZERO;
        BigDecimal totalEndCredit = ZERO;

        for (AccountSubjectPO subject : subjects) {
            List<AccountBalancePO> list = balanceBySubject.getOrDefault(subject.getSubjectCode(), Collections.emptyList());

            BigDecimal beginBal = ZERO;
            BigDecimal periodDebit = ZERO;
            BigDecimal periodCredit = ZERO;
            BigDecimal endBal = ZERO;

            if (!list.isEmpty()) {
                // 按日期排序
                list.sort(Comparator.comparing(AccountBalancePO::getAccountingDate));
                AccountBalancePO first = list.get(0);
                AccountBalancePO last = list.get(list.size() - 1);

                beginBal = first.getBeginBalance() != null ? first.getBeginBalance() : ZERO;
                endBal = last.getEndBalance() != null ? last.getEndBalance() : ZERO;

                for (AccountBalancePO b : list) {
                    periodDebit = periodDebit.add(b.getDebitAmount() != null ? b.getDebitAmount() : ZERO);
                    periodCredit = periodCredit.add(b.getCreditAmount() != null ? b.getCreditAmount() : ZERO);
                }
            }

            // 是否过滤全 0 科目
            boolean isZero = beginBal.compareTo(ZERO) == 0
                    && periodDebit.compareTo(ZERO) == 0
                    && periodCredit.compareTo(ZERO) == 0
                    && endBal.compareTo(ZERO) == 0;

            if (isZero && Boolean.FALSE.equals(request.getShowZeroBalance())) {
                continue;
            }

            // 方向：0-无, 1-借, 2-贷, 3-双向
            int subjectDirCode = subject.getDebitCredit() != null ? subject.getDebitCredit().getCode() : 1;
            boolean isOffBalance = (subjectDirCode == 0);
            boolean isBoth = (subjectDirCode == 3);

            // 确定期末展示方向与期末实际方向
            int endDirCode = subjectDirCode;
            String endDirDesc;
            if (endBal.compareTo(ZERO) == 0) {
                endDirDesc = "平";
                endDirCode = 0;
            } else if (isBoth) {
                // 双向科目：若有明细取最新日余额方向，否则根据借贷发生额推导
                BalanceDirectionEnum lastDir = (!list.isEmpty() && list.get(list.size() - 1).getBalanceDirection() != null)
                        ? list.get(list.size() - 1).getBalanceDirection()
                        : (periodDebit.compareTo(periodCredit) >= 0 ? BalanceDirectionEnum.DEBIT : BalanceDirectionEnum.CREDIT);
                endDirCode = lastDir.getCode();
                endDirDesc = lastDir.getDesc();
            } else if (isOffBalance) {
                endDirCode = 0;
                endDirDesc = "无";
            } else {
                endDirDesc = SubjectBalanceDirectionEnum.getDescByCode(subjectDirCode);
            }

            items.add(GeneralLedgerItemResponse.builder()
                    .subjectCode(subject.getSubjectCode())
                    .subjectName(subject.getSubjectName())
                    .subjectLevel(subject.getSubjectLevel() != null ? subject.getSubjectLevel().intValue() : 1)
                    .balanceDirection(endDirCode)
                    .balanceDirectionDesc(endDirDesc)
                    .beginBalance(beginBal)
                    .debitAmount(periodDebit)
                    .creditAmount(periodCredit)
                    .endBalance(endBal)
                    .build());

            // 统计总计（表外不计入表内借贷总计）
            if (!isOffBalance) {
                // 期初借贷汇总
                BalanceDirectionEnum beginDir = (!list.isEmpty() && list.get(0).getBalanceDirection() != null)
                        ? list.get(0).getBalanceDirection()
                        : (subjectDirCode == 2 ? BalanceDirectionEnum.CREDIT : BalanceDirectionEnum.DEBIT);
                if (beginDir == BalanceDirectionEnum.CREDIT) {
                    totalBeginCredit = totalBeginCredit.add(beginBal);
                } else if (beginDir == BalanceDirectionEnum.DEBIT) {
                    totalBeginDebit = totalBeginDebit.add(beginBal);
                }

                // 期末借贷汇总
                if (endDirCode == 2) {
                    totalEndCredit = totalEndCredit.add(endBal);
                } else if (endDirCode == 1) {
                    totalEndDebit = totalEndDebit.add(endBal);
                }

                totalPeriodDebit = totalPeriodDebit.add(periodDebit);
                totalPeriodCredit = totalPeriodCredit.add(periodCredit);
            }
        }

        boolean isBalanced = totalPeriodDebit.compareTo(totalPeriodCredit) == 0;

        return GeneralLedgerResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalSubjectCount(items.size())
                .totalBeginDebit(totalBeginDebit)
                .totalBeginCredit(totalBeginCredit)
                .totalPeriodDebit(totalPeriodDebit)
                .totalPeriodCredit(totalPeriodCredit)
                .totalEndDebit(totalEndDebit)
                .totalEndCredit(totalEndCredit)
                .isBalanced(isBalanced)
                .items(items)
                .build();
    }

    // =========================================================================
    // 4. 科目明细账（Subsidiary Ledger）
    // =========================================================================

    public SubsidiaryLedgerResponse generateSubsidiaryLedger(SubsidiaryLedgerQueryRequest request) {
        String subjectCode = request.getSubjectCode();
        AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
        String subjectName = subject != null ? subject.getSubjectName() : subjectCode;
        int level = subject != null && subject.getSubjectLevel() != null ? subject.getSubjectLevel() : 1;
        SubjectBalanceDirectionEnum subjectDir = subject != null && subject.getDebitCredit() != null
                ? subject.getDebitCredit()
                : SubjectBalanceDirectionEnum.DEBIT;
        int defaultDir = subjectDir.getCode();
        String dirDesc = subjectDir.getDesc();

        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate();

        // 1. 期初余额计算
        List<AccountBalancePO> beginList = accountBalanceRepository.selectBySubjectAndDateRange(subjectCode, startDate, startDate);
        BigDecimal beginBal = ZERO;
        BalanceDirectionEnum beginDir = BalanceDirectionEnum.FLAT;
        if (!beginList.isEmpty()) {
            AccountBalancePO first = beginList.get(0);
            beginBal = first.getBeginBalance() != null ? first.getBeginBalance() : ZERO;
            if (first.getBalanceDirection() != null) {
                beginDir = first.getBalanceDirection();
            }
        }

        // 推导期初显示方向与方向代码（明细行只展示 借/贷/平/无，绝不展示“双向”）
        int initialDirCode;
        String initialDirDesc;
        if (beginBal.compareTo(ZERO) == 0) {
            initialDirCode = 0;
            initialDirDesc = "平";
        } else if (subjectDir.isOffBalance()) {
            initialDirCode = 0;
            initialDirDesc = "无";
        } else if (subjectDir.isBidirectional()) {
            BalanceDirectionEnum eff = (beginDir != null && beginDir != BalanceDirectionEnum.FLAT)
                    ? beginDir
                    : BalanceDirectionEnum.DEBIT;
            initialDirCode = eff.getCode();
            initialDirDesc = eff.getDesc();
        } else if (subjectDir.isCredit()) {
            initialDirCode = 2;
            initialDirDesc = "贷";
        } else {
            initialDirCode = 1;
            initialDirDesc = "借";
        }

        List<SubsidiaryLedgerItemResponse> items = new ArrayList<>();

        // 期初行
        items.add(SubsidiaryLedgerItemResponse.builder()
                .rowType("BEGIN_BALANCE")
                .accountingDate(startDate)
                .voucherNo("")
                .entryId("")
                .summary("期初余额")
                .debitAmount(ZERO)
                .creditAmount(ZERO)
                .balanceDirection(initialDirCode)
                .balanceDirectionDesc(initialDirDesc)
                .balance(beginBal)
                .build());

        // 2. 取期间内所有已过账分录流水
        List<AccountingVoucherEntryPO> entries = voucherRepository.selectEntriesBySubjectAndDateRange(
                subjectCode, request.getAccountNo(), startDate, endDate);

        BigDecimal periodDebit = ZERO;
        BigDecimal periodCredit = ZERO;

        // 跟踪动态净余额（借方为正，贷方为负；表外借增贷减）
        BigDecimal currentNetBalance;
        if (subjectDir.isCredit()) {
            currentNetBalance = beginBal; // 贷方科目：贷方为正
        } else if (subjectDir.isBidirectional()) {
            currentNetBalance = (beginDir == BalanceDirectionEnum.CREDIT) ? beginBal.negate() : beginBal; // 双向科目：借为正，贷为负
        } else {
            // 借方科目 或 表外科目：借为正
            currentNetBalance = beginBal;
        }

        int currentDirCode = initialDirCode;
        String currentDirText = initialDirDesc;
        BigDecimal displayBal = beginBal;

        for (AccountingVoucherEntryPO e : entries) {
            BigDecimal debit = e.getDebitCredit() == com.kltb.accounting.core.domain.enums.DebitCreditEnum.DEBIT ? e.getAmount() : ZERO;
            BigDecimal credit = e.getDebitCredit() == com.kltb.accounting.core.domain.enums.DebitCreditEnum.CREDIT ? e.getAmount() : ZERO;

            // 过滤金额
            if (request.getMinAmount() != null && e.getAmount().compareTo(request.getMinAmount()) < 0) {
                continue;
            }
            if (request.getMaxAmount() != null && e.getAmount().compareTo(request.getMaxAmount()) > 0) {
                continue;
            }
            // 过滤摘要
            if (request.getSummaryKeyword() != null && !request.getSummaryKeyword().isBlank()) {
                if (e.getSummary() == null || !e.getSummary().contains(request.getSummaryKeyword().trim())) {
                    continue;
                }
            }

            periodDebit = periodDebit.add(debit);
            periodCredit = periodCredit.add(credit);

            // 动态轧差计算余额与明细行方向
            if (subjectDir.isOffBalance()) {
                // 表外：借增贷减，余额方向恒为平/无 (0)
                currentNetBalance = currentNetBalance.add(debit).subtract(credit);
                displayBal = currentNetBalance.abs();
                currentDirCode = 0;
                currentDirText = currentNetBalance.compareTo(ZERO) == 0 ? "平" : "无";
            } else if (subjectDir.isCredit()) {
                // 贷方科目：贷增借减
                currentNetBalance = currentNetBalance.add(credit).subtract(debit);
                displayBal = currentNetBalance.abs();
                if (currentNetBalance.compareTo(ZERO) > 0) {
                    currentDirCode = 2;
                    currentDirText = "贷";
                } else if (currentNetBalance.compareTo(ZERO) < 0) {
                    currentDirCode = 1;
                    currentDirText = "借";
                } else {
                    currentDirCode = 0;
                    currentDirText = "平";
                }
            } else {
                // 借方科目 或 双向科目：借增贷减
                currentNetBalance = currentNetBalance.add(debit).subtract(credit);
                displayBal = currentNetBalance.abs();
                if (currentNetBalance.compareTo(ZERO) > 0) {
                    currentDirCode = 1;
                    currentDirText = "借";
                } else if (currentNetBalance.compareTo(ZERO) < 0) {
                    currentDirCode = 2;
                    currentDirText = "贷";
                } else {
                    currentDirCode = 0;
                    currentDirText = "平";
                }
            }

            items.add(SubsidiaryLedgerItemResponse.builder()
                    .rowType("ENTRY")
                    .accountingDate(e.getAccountingDate())
                    .voucherNo(e.getVoucherNo())
                    .entryId(e.getEntryId())
                    .summary(e.getSummary())
                    .debitAmount(debit)
                    .creditAmount(credit)
                    .balanceDirection(currentDirCode)
                    .balanceDirectionDesc(currentDirText)
                    .balance(displayBal)
                    .build());
        }

        // 3. 本期合计行
        items.add(SubsidiaryLedgerItemResponse.builder()
                .rowType("PERIOD_TOTAL")
                .accountingDate(endDate)
                .voucherNo("")
                .entryId("")
                .summary("本期合计")
                .debitAmount(periodDebit)
                .creditAmount(periodCredit)
                .balanceDirection(currentDirCode)
                .balanceDirectionDesc(currentDirText)
                .balance(displayBal)
                .build());

        return SubsidiaryLedgerResponse.builder()
                .subjectCode(subjectCode)
                .subjectName(subjectName)
                .subjectLevel(level)
                .balanceDirectionDesc(dirDesc)
                .startDate(startDate)
                .endDate(endDate)
                .beginBalance(beginBal)
                .endBalance(displayBal)
                .totalDebitAmount(periodDebit)
                .totalCreditAmount(periodCredit)
                .items(items)
                .build();
    }

    // =========================================================================
    // 内部私有辅助方法
    // =========================================================================

    private Map<String, BigDecimal> aggregateBalancesBySubject(List<AccountBalancePO> list) {
        if (list == null || list.isEmpty()) return Collections.emptyMap();
        Map<String, BigDecimal> map = new HashMap<>();
        for (AccountBalancePO b : list) {
            String code = b.getSubjectCode();
            BigDecimal endBal = b.getEndBalance() != null ? b.getEndBalance() : ZERO;
            map.put(code, map.getOrDefault(code, ZERO).add(endBal));
        }
        return map;
    }

    private Map<String, BigDecimal> aggregateNetAmounts(List<AccountBalancePO> list) {
        if (list == null || list.isEmpty()) return Collections.emptyMap();
        Map<String, BigDecimal> map = new HashMap<>();
        for (AccountBalancePO b : list) {
            String code = b.getSubjectCode();
            BigDecimal debit = b.getDebitAmount() != null ? b.getDebitAmount() : ZERO;
            BigDecimal credit = b.getCreditAmount() != null ? b.getCreditAmount() : ZERO;

            // 收入/收益类科目（60*, 61*, 63*）贷方发生额为主
            BigDecimal net;
            if (code.startsWith("60") || code.startsWith("61") || code.startsWith("63")) {
                net = credit.subtract(debit);
            } else {
                // 成本/费用/税金（64*, 66*, 67*, 68*）借方发生额为主
                net = debit.subtract(credit);
            }
            map.put(code, map.getOrDefault(code, ZERO).add(net));
        }
        return map;
    }

    private BigDecimal sumSubjects(Map<String, BigDecimal> map, String... codes) {
        BigDecimal sum = ZERO;
        for (String code : codes) {
            for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
                if (entry.getKey().startsWith(code)) {
                    sum = sum.add(entry.getValue());
                }
            }
        }
        return sum;
    }

    private BigDecimal sumPrefixExcept(Map<String, BigDecimal> map, String prefix, Set<String> exceptPrefixes) {
        BigDecimal sum = ZERO;
        for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(prefix)) {
                boolean matchExcept = false;
                for (String exp : exceptPrefixes) {
                    if (key.startsWith(exp)) {
                        matchExcept = true;
                        break;
                    }
                }
                if (!matchExcept) {
                    sum = sum.add(entry.getValue());
                }
            }
        }
        return sum;
    }

    private BigDecimal sumNet(Map<String, BigDecimal> map, String... prefixes) {
        BigDecimal sum = ZERO;
        for (String prefix : prefixes) {
            for (Map.Entry<String, BigDecimal> entry : map.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    sum = sum.add(entry.getValue());
                }
            }
        }
        return sum;
    }

    private BalanceSheetItemResponse buildItem(int rowNo, String itemName, int level, String subjectCodes,
                                                BigDecimal end, BigDecimal begin) {
        return BalanceSheetItemResponse.builder()
                .rowNo(rowNo)
                .itemName(itemName)
                .itemLevel(level)
                .subjectCodes(subjectCodes)
                .endAmount(end != null ? end : ZERO)
                .beginAmount(begin != null ? begin : ZERO)
                .build();
    }

    private IncomeStatementItemResponse buildIncomeItem(int rowNo, String itemName, int level,
                                                        BigDecimal current, BigDecimal yearTotal, BigDecimal compare) {
        BigDecimal growth = calcRate(current, compare);
        return IncomeStatementItemResponse.builder()
                .rowNo(rowNo)
                .itemName(itemName)
                .itemLevel(level)
                .currentAmount(current)
                .yearTotalAmount(yearTotal)
                .compareAmount(compare)
                .growthRate(growth)
                .build();
    }

    private BigDecimal calcRate(BigDecimal cur, BigDecimal cmp) {
        if (cmp == null || cmp.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return cur.subtract(cmp).divide(cmp.abs(), 4, RoundingMode.HALF_UP).multiply(FinancialAmountUtil.PERCENT_BASE).setScale(1, RoundingMode.HALF_UP);
    }

    /**
     * 动态汇算未结转损益净额（表结法核心算法）
     * <p>
     * 遍历报告期科目日余额列表中的损益类科目（6 类）：
     * 贷方余额（收入/收益）为正向利润贡献，借方余额（费用/成本/支出）为负向冲减。
     *
     * @param balances   科目日余额列表
     * @param subjectMap 科目主数据映射表
     * @return 当期未结转损益净额（正数为净收益，负数为净亏损）
     */
    private BigDecimal calculateUnclosedProfit(List<AccountBalancePO> balances, Map<String, AccountSubjectPO> subjectMap) {
        if (balances == null || balances.isEmpty()) {
            return ZERO;
        }
        BigDecimal unclosedProfit = ZERO;
        for (AccountBalancePO b : balances) {
            String code = b.getSubjectCode();
            if (code == null) {
                continue;
            }
            AccountSubjectPO subject = subjectMap != null ? subjectMap.get(code) : null;
            boolean isProfitLoss = (subject != null && subject.getSubjectCategory() == SubjectCategoryEnum.PROFIT_LOSS)
                    || code.startsWith("6");
            if (!isProfitLoss) {
                continue;
            }

            BigDecimal endBal = b.getEndBalance() != null ? b.getEndBalance() : ZERO;
            if (endBal.compareTo(ZERO) == 0) {
                continue;
            }

            // 判断余额方向：贷方为收益(+)，借方为费用/支出(-)
            BalanceDirectionEnum dir = b.getBalanceDirection();
            if (dir == null || dir == BalanceDirectionEnum.FLAT) {
                if (subject != null && subject.getDebitCredit() != null) {
                    if (subject.getDebitCredit().isCredit()) {
                        dir = BalanceDirectionEnum.CREDIT;
                    } else if (subject.getDebitCredit().isDebit()) {
                        dir = BalanceDirectionEnum.DEBIT;
                    }
                }
            }

            if (dir == BalanceDirectionEnum.CREDIT) {
                unclosedProfit = unclosedProfit.add(endBal);
            } else if (dir == BalanceDirectionEnum.DEBIT) {
                unclosedProfit = unclosedProfit.subtract(endBal);
            } else {
                // 若仍无法判定方向，收入/收益类科目默认贷方，成本费用类默认借方
                if (code.startsWith("60") || code.startsWith("61") || code.startsWith("63")) {
                    unclosedProfit = unclosedProfit.add(endBal);
                } else {
                    unclosedProfit = unclosedProfit.subtract(endBal);
                }
            }
        }
        return unclosedProfit;
    }

    /**
     * 归集多科目明细清单（用于资产负债表行项目穿透展开）
     */
    private List<BalanceSheetSubjectDetailResponse> collectSubjectDetails(
            Map<String, BigDecimal> endMap, Map<String, BigDecimal> beginMap,
            Map<String, AccountSubjectPO> subjectMap, String... codes) {
        List<BalanceSheetSubjectDetailResponse> details = new ArrayList<>();
        Set<String> matchedCodes = new TreeSet<>();
        for (String code : codes) {
            for (String key : endMap.keySet()) {
                if (key.startsWith(code)) matchedCodes.add(key);
            }
            for (String key : beginMap.keySet()) {
                if (key.startsWith(code)) matchedCodes.add(key);
            }
        }
        for (String c : matchedCodes) {
            BigDecimal end = endMap.getOrDefault(c, ZERO);
            BigDecimal begin = beginMap.getOrDefault(c, ZERO);
            if (end.compareTo(ZERO) != 0 || begin.compareTo(ZERO) != 0) {
                AccountSubjectPO sub = subjectMap != null ? subjectMap.get(c) : null;
                String name = sub != null ? sub.getSubjectName() : c;
                details.add(BalanceSheetSubjectDetailResponse.builder()
                        .subjectCode(c)
                        .subjectName(name)
                        .endAmount(end)
                        .beginAmount(begin)
                        .build());
            }
        }
        return details;
    }
}
