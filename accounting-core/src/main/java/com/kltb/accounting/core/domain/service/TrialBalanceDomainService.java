package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.response.GlAccountDiffResponse;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountBalanceMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.util.FinancialAmountUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrialBalanceDomainService {

    private static final BigDecimal BALANCE_THRESHOLD = new BigDecimal("0.000001");

    private final AccountingVoucherEntryMapper voucherEntryMapper;
    private final SubjectRepository subjectRepository;
    private final AccountBalanceMapper accountBalanceMapper;
    private final AccountMapper accountMapper;
    private final AccountDetailMapper accountDetailMapper;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountRepository accountRepository;

    public TrialBalanceResult executeTrialBalance(LocalDate accountingDate) {
        List<Map<String, Object>> subjectSummaries = voucherEntryMapper.sumEntriesBySubject(accountingDate);

        BigDecimal totalDebitAll = BigDecimal.ZERO;
        BigDecimal totalCreditAll = BigDecimal.ZERO;
        List<SubjectDetail> subjectDetails = new ArrayList<>();

        for (Map<String, Object> row : subjectSummaries) {
            String subjectCode = (String) row.get("subject_code");
            BigDecimal totalDebit = FinancialAmountUtil.toBigDecimal(row.get("total_debit"));
            BigDecimal totalCredit = FinancialAmountUtil.toBigDecimal(row.get("total_credit"));

            totalDebitAll = totalDebitAll.add(totalDebit);
            totalCreditAll = totalCreditAll.add(totalCredit);

            BigDecimal netDiff = totalDebit.subtract(totalCredit);
            subjectDetails.add(new SubjectDetail(
                    subjectCode,
                    getSubjectName(subjectCode),
                    totalDebit,
                    totalCredit,
                    netDiff));
        }

        BigDecimal diff = totalDebitAll.subtract(totalCreditAll).abs();
        boolean passed = diff.compareTo(BALANCE_THRESHOLD) <= 0;

        List<SubjectDetail> imbalancedSubjects = new ArrayList<>();
        if (!passed) {
            for (SubjectDetail detail : subjectDetails) {
                if (detail.getNetDiff().abs().compareTo(BALANCE_THRESHOLD) > 0) {
                    imbalancedSubjects.add(detail);
                    log.error("[TRIAL-BALANCE-IMBALANCED] subject={}, debit={}, credit={}, diff={}",
                            detail.getSubjectCode(), detail.getTotalDebit(),
                            detail.getTotalCredit(), detail.getNetDiff().abs());
                }
            }
            log.error("[TRIAL-BALANCE-FAILED] date={}, totalDebit={}, totalCredit={}, diff={}",
                    accountingDate, totalDebitAll, totalCreditAll, diff);
        }

        return new TrialBalanceResult(
                accountingDate, passed, totalDebitAll, totalCreditAll,
                diff, subjectDetails, imbalancedSubjects);
    }

    public TrialBalanceResult executeAndThrow(LocalDate accountingDate) {
        TrialBalanceResult result = executeTrialBalance(accountingDate);
        if (!result.isPassed()) {
            throw new AccountException(ResultCode.TRIAL_BALANCE_FAILED,
                    "试算平衡失败: date=" + accountingDate
                            + ", totalDebit=" + result.getTotalDebit()
                            + ", totalCredit=" + result.getTotalCredit()
                            + ", diff=" + result.getDiff());
        }
        return result;
    }

    /**
     * 执行总分核对（Step 17S 新增）
     * <p>
     * 核对逻辑：
     * 1. 总账侧：从 t_account_balance 按科目汇总 end_balance（余额表汇总）
     * 2. 分户侧：从 t_account 按科目汇总 balance（分户余额汇总）
     * 3. 逐科目对比：|总账汇总 - 分户汇总| &lt;= 0.000001
     *
     * @param accountingDate 会计日期
     * @return 核对结果
     */
    public GlReconciliationResult executeGlReconciliation(LocalDate accountingDate) {
        // 总账侧：余额表按科目汇总（t_account_balance）
        List<Map<String, Object>> glSums = accountBalanceMapper.sumBalancesBySubject(accountingDate);
        Map<String, BigDecimal> glBalanceBySubject = new HashMap<>();
        BigDecimal totalGlAll = BigDecimal.ZERO;
        for (Map<String, Object> row : glSums) {
            String subjectCode = (String) row.get("subject_code");
            BigDecimal bal = FinancialAmountUtil.toBigDecimal(row.get("total_balance"));
            glBalanceBySubject.put(subjectCode, bal);
            totalGlAll = totalGlAll.add(bal);
        }

        // 分户侧：账户余额按科目汇总（t_account）
        List<Map<String, Object>> accountSums = accountMapper.sumBalancesBySubject();
        Map<String, BigDecimal> accountBalanceBySubject = new HashMap<>();
        Map<String, Integer> accountCountBySubject = new HashMap<>();
        BigDecimal totalAccountAll = BigDecimal.ZERO;
        for (Map<String, Object> row : accountSums) {
            String subjectCode = (String) row.get("subject_code");
            BigDecimal bal = FinancialAmountUtil.toBigDecimal(row.get("total_balance"));
            accountBalanceBySubject.put(subjectCode, bal);
            totalAccountAll = totalAccountAll.add(bal);
            Object countObj = row.get("account_count");
            accountCountBySubject.put(subjectCode, countObj instanceof Number ? ((Number) countObj).intValue() : 0);
        }

        // 识别历史断档会计日（凭证存在但日余额缺失的日期）
        List<LocalDate> missingHistoryDates = findMissingHistoryDates(accountingDate);

        // 逐科目对比
        List<GlDiffDetail> diffDetails = new ArrayList<>();
        BigDecimal totalDiff = BigDecimal.ZERO;

        // 检查总账侧所有科目
        for (Map.Entry<String, BigDecimal> entry : glBalanceBySubject.entrySet()) {
            String subjectCode = entry.getKey();
            BigDecimal glTotal = entry.getValue();
            BigDecimal accountTotal = accountBalanceBySubject.getOrDefault(subjectCode, BigDecimal.ZERO);
            BigDecimal diff = glTotal.subtract(accountTotal).abs();

            if (diff.compareTo(BALANCE_THRESHOLD) > 0) {
                Integer acctCount = accountCountBySubject.getOrDefault(subjectCode, 0);
                String reason = diagnoseSubjectDiffReason(subjectCode, glTotal, accountTotal, accountingDate, missingHistoryDates);
                diffDetails.add(new GlDiffDetail(subjectCode, getSubjectName(subjectCode),
                        glTotal, accountTotal, diff, acctCount, reason, true));
                totalDiff = totalDiff.add(diff);
                log.error("[GL-RECONCILIATION-IMBALANCED] subject={}, glTotal={}, accountTotal={}, diff={}, reason={}",
                        subjectCode, glTotal, accountTotal, diff, reason);
            }
        }

        // 检查分户侧有但总账没有的科目
        for (Map.Entry<String, BigDecimal> entry : accountBalanceBySubject.entrySet()) {
            String subjectCode = entry.getKey();
            if (!glBalanceBySubject.containsKey(subjectCode)) {
                BigDecimal accountTotal = entry.getValue();
                BigDecimal diff = accountTotal.abs();
                if (diff.compareTo(BALANCE_THRESHOLD) > 0) {
                    Integer acctCount = accountCountBySubject.getOrDefault(subjectCode, 0);
                    String reason = diagnoseSubjectDiffReason(subjectCode, BigDecimal.ZERO, accountTotal, accountingDate, missingHistoryDates);
                    diffDetails.add(new GlDiffDetail(subjectCode, getSubjectName(subjectCode),
                            BigDecimal.ZERO, accountTotal, diff, acctCount, reason, true));
                    totalDiff = totalDiff.add(diff);
                    log.error("[GL-RECONCILIATION-IMBALANCED] subject={}, glTotal=0, accountTotal={}, diff={}, reason={}",
                            subjectCode, accountTotal, diff, reason);
                }
            }
        }

        boolean passed = totalDiff.compareTo(BALANCE_THRESHOLD) <= 0;

        if (!passed) {
            log.error("[GL-RECONCILIATION-FAILED] date={}, totalDiff={}, diffCount={}",
                    accountingDate, totalDiff, diffDetails.size());
        } else {
            log.info("[GL-RECONCILIATION] date={}, PASSED, subjects={}", accountingDate, glBalanceBySubject.size());
        }

        return new GlReconciliationResult(accountingDate, passed, totalGlAll, totalAccountAll, totalDiff, diffDetails);
    }

    /**
     * 查询指定科目在指定会计日的分户账户穿透明细对比
     */
    public GlAccountDiffResponse getGlAccountDiffDetails(
            LocalDate accountingDate, String subjectCode) {
        List<AccountPO> activeAccounts =
                accountRepository.selectActiveAccountsBySubject(subjectCode);
        List<AccountBalancePO> dayBalances = accountBalanceMapper.selectBySubjectAndDate(subjectCode, accountingDate);
        Map<String, AccountBalancePO> balanceMap = dayBalances.stream()
                .filter(b -> b.getAccountNo() != null)
                .collect(Collectors.toMap(AccountBalancePO::getAccountNo, b -> b, (b1, b2) -> b1));

        List<GlAccountDiffResponse.AccountItem> items = new ArrayList<>();
        Set<String> processedAccountNos = new HashSet<>();

        for (AccountPO acct : activeAccounts) {
            String no = acct.getAccountNo();
            processedAccountNos.add(no);
            AccountBalancePO bal = balanceMap.get(no);
            BigDecimal glEnd = bal != null && bal.getEndBalance() != null ? bal.getEndBalance() : BigDecimal.ZERO;
            BigDecimal acctBal = acct.getBalance() != null ? acct.getBalance() : BigDecimal.ZERO;
            BigDecimal diff = glEnd.subtract(acctBal).abs();

            items.add(GlAccountDiffResponse.AccountItem.builder()
                    .accountNo(no)
                    .accountName(acct.getAccountName())
                    .glEndBalance(glEnd)
                    .accountBalance(acctBal)
                    .diff(diff)
                    .statusDesc(acct.getStatus() != null ? acct.getStatus().getDesc() : "正常")
                    .latestVoucherDate(bal != null && bal.getAccountingDate() != null ? bal.getAccountingDate().toString() : null)
                    .build());
        }

        // 补充日余额表中存在但分户表中已注销或特殊的记录
        for (AccountBalancePO bal : dayBalances) {
            String no = bal.getAccountNo();
            if (no != null && !processedAccountNos.contains(no)) {
                BigDecimal glEnd = bal.getEndBalance() != null ? bal.getEndBalance() : BigDecimal.ZERO;
                items.add(GlAccountDiffResponse.AccountItem.builder()
                        .accountNo(no)
                        .accountName("历史结转/未激活账户")
                        .glEndBalance(glEnd)
                        .accountBalance(BigDecimal.ZERO)
                        .diff(glEnd)
                        .statusDesc("无分户")
                        .latestVoucherDate(bal.getAccountingDate() != null ? bal.getAccountingDate().toString() : null)
                        .build());
            }
        }

        // 差异倒序排列（差异最大的排在最前）
        items.sort((a, b) -> b.getDiff().compareTo(a.getDiff()));

        return com.kltb.accounting.api.response.GlAccountDiffResponse.builder()
                .subjectCode(subjectCode)
                .subjectName(getSubjectName(subjectCode))
                .accounts(items)
                .build();
    }

    private List<LocalDate> findMissingHistoryDates(LocalDate accountingDate) {
        try {
            List<LocalDate> voucherDates = voucherEntryMapper.selectDistinctDatesWithEntriesBeforeDate(accountingDate);
            List<LocalDate> balanceDates = accountBalanceMapper.selectDistinctDatesBeforeDate(accountingDate);
            Set<LocalDate> balanceDateSet = new HashSet<>(balanceDates != null ? balanceDates : List.of());
            if (voucherDates != null) {
                List<LocalDate> diffDates = new ArrayList<>();
                for (LocalDate d : voucherDates) {
                    if (!d.isBefore(accountingDate)) {
                        continue;
                    }
                    if (!balanceDateSet.contains(d)) {
                        diffDates.add(d);
                    } else {
                        BigDecimal vDebit = voucherEntryMapper.sumDailyDebitTotalByDate(d);
                        BigDecimal bDebit = accountBalanceMapper.sumDailyDebitTotalByDate(d);
                        if (vDebit != null && bDebit != null && vDebit.compareTo(bDebit) != 0) {
                            diffDates.add(d);
                        }
                    }
                }
                return diffDates;
            }
        } catch (Exception e) {
            log.warn("[GL-RECONCILIATION] 检查历史断档日期异常: {}", e.getMessage());
        }
        return List.of();
    }

    private String diagnoseSubjectDiffReason(String subjectCode, BigDecimal glTotal, BigDecimal accountTotal,
                                             LocalDate accountingDate, List<LocalDate> missingHistoryDates) {
        if (missingHistoryDates != null && !missingHistoryDates.isEmpty()) {
            return "检测到前序历史会计日存在未日切结转的分录（如 " + missingHistoryDates.get(0) + "），导致总账期初少计，建议点击【一键修复】";
        }
        try {
            BigDecimal futureNet = voucherEntryMapper.sumNetChangeAfterDate(subjectCode, accountingDate);
            if (futureNet != null && futureNet.abs().compareTo(BALANCE_THRESHOLD) > 0) {
                return "检测到分户表包含未来会计日（> " + accountingDate + "）的新发生额 ¥" + futureNet + "，当前为跨日对账截面差异";
            }
        } catch (Exception ignored) {
        }
        BigDecimal diff = glTotal.subtract(accountTotal).abs();
        return "总账汇总 (¥" + glTotal + ") 与分户汇总 (¥" + accountTotal + ") 存在差额 ¥" + diff + "，建议点击【一键修复】重新校准";
    }

    /**
     * 执行余额核对（Step 17S 新增）
     * <p>
     * 核对逻辑：
     * 1. 账户余额 vs t_account 实际余额
     * 2. 日余额表 end_balance vs 明细最后一条 post_balance
     *
     * @param accountingDate 会计日期
     * @return 核对结果
     */
    public BalanceReconciliationResult executeBalanceReconciliation(LocalDate accountingDate) {
        List<AccountBalancePO> balances = accountBalanceRepository.selectByDate(accountingDate);
        List<BalanceDiffDetail> diffDetails = new ArrayList<>();
        BigDecimal totalDiff = BigDecimal.ZERO;

        // 批量加载账户余额（避免 N+1 查询）
        List<String> accountNos = balances.stream()
                .map(AccountBalancePO::getAccountNo)
                .filter(no -> no != null && !no.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        Map<String, BigDecimal> accountBalanceMap = new HashMap<>();
        if (!accountNos.isEmpty()) {
            List<Map<String, Object>> accountRows = accountMapper.selectBalancesByAccountNos(accountNos);
            for (Map<String, Object> row : accountRows) {
                String acctNo = (String) row.get("account_no");
                accountBalanceMap.put(acctNo, FinancialAmountUtil.toBigDecimal(row.get("balance")));
            }
        }

        for (AccountBalancePO balance : balances) {
            // 核对 1：账户余额 vs t_account 实际余额
            String accountNo = balance.getAccountNo();
            if (accountNo != null && !accountNo.isEmpty()) {
                BigDecimal accountBalance = accountBalanceMap.get(accountNo);
                if (accountBalance != null) {
                    BigDecimal diff1 = balance.getEndBalance().subtract(accountBalance).abs();
                    if (diff1.compareTo(BALANCE_THRESHOLD) > 0) {
                        BalanceDiffDetail detail = new BalanceDiffDetail(
                                accountNo, balance.getSubjectCode(), "ACCOUNT_BALANCE",
                                balance.getEndBalance(), accountBalance, diff1);
                        diffDetails.add(detail);
                        totalDiff = totalDiff.add(diff1);
                        log.error("[BALANCE-RECONCILIATION-IMBALANCED] account={}, subject={}, "
                                        + "balanceEndBalance={}, accountBalance={}, diff={}",
                                accountNo, balance.getSubjectCode(),
                                balance.getEndBalance(), accountBalance, diff1);
                    }
                }
            }

            // 核对 2：日余额表 end_balance vs 明细最后一条 post_balance
            BigDecimal lastPostBalance = accountDetailMapper.selectLastPostBalance(accountNo, accountingDate);
            if (lastPostBalance != null) {
                BigDecimal diff2 = balance.getEndBalance().subtract(lastPostBalance).abs();
                if (diff2.compareTo(BALANCE_THRESHOLD) > 0) {
                    BalanceDiffDetail detail = new BalanceDiffDetail(
                            accountNo, balance.getSubjectCode(), "POST_BALANCE",
                            balance.getEndBalance(), lastPostBalance, diff2);
                    diffDetails.add(detail);
                    totalDiff = totalDiff.add(diff2);
                    log.error("[BALANCE-RECONCILIATION-IMBALANCED] account={}, subject={}, "
                                    + "endBalance={}, lastPostBalance={}, diff={}",
                            accountNo, balance.getSubjectCode(),
                            balance.getEndBalance(), lastPostBalance, diff2);
                }
            }
        }

        boolean passed = totalDiff.compareTo(BALANCE_THRESHOLD) <= 0;

        if (!passed) {
            log.error("[BALANCE-RECONCILIATION-FAILED] date={}, totalDiff={}, diffCount={}",
                    accountingDate, totalDiff, diffDetails.size());
        } else {
            log.info("[BALANCE-RECONCILIATION] date={}, PASSED, accounts={}", accountingDate, balances.size());
        }

        return new BalanceReconciliationResult(accountingDate, passed, totalDiff, diffDetails);
    }

    private String getSubjectName(String subjectCode) {
        AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
        return subject != null ? subject.getSubjectName() : subjectCode;
    }

    @Data
    public static class TrialBalanceResult {
        private final LocalDate accountingDate;
        private final boolean passed;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final BigDecimal diff;
        private final List<SubjectDetail> subjectDetails;
        private final List<SubjectDetail> imbalancedSubjects;
    }

    @Data
    public static class SubjectDetail {
        private final String subjectCode;
        private final String subjectName;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final BigDecimal netDiff;
    }

    // ==================== 总分核对结果 ====================

    @Data
    public static class GlReconciliationResult {
        private final LocalDate accountingDate;
        private final boolean passed;
        private final BigDecimal totalGlBalance;
        private final BigDecimal totalAccountBalance;
        private final BigDecimal totalDiff;
        private final List<GlDiffDetail> diffDetails;

        public GlReconciliationResult(LocalDate accountingDate, boolean passed, BigDecimal totalDiff, List<GlDiffDetail> diffDetails) {
            this(accountingDate, passed, BigDecimal.ZERO, BigDecimal.ZERO, totalDiff, diffDetails);
        }

        public GlReconciliationResult(LocalDate accountingDate, boolean passed,
                                      BigDecimal totalGlBalance, BigDecimal totalAccountBalance,
                                      BigDecimal totalDiff, List<GlDiffDetail> diffDetails) {
            this.accountingDate = accountingDate;
            this.passed = passed;
            this.totalGlBalance = totalGlBalance;
            this.totalAccountBalance = totalAccountBalance;
            this.totalDiff = totalDiff;
            this.diffDetails = diffDetails;
        }
    }

    @Data
    public static class GlDiffDetail {
        private final String subjectCode;
        private final String subjectName;
        private final BigDecimal glTotalBalance;
        private final BigDecimal accountTotalBalance;
        private final BigDecimal diff;
        private final Integer accountCount;
        private final String reasonDesc;
        private final Boolean repairable;

        public GlDiffDetail(String subjectCode, String subjectName,
                            BigDecimal glTotalBalance, BigDecimal accountTotalBalance,
                            BigDecimal diff) {
            this(subjectCode, subjectName, glTotalBalance, accountTotalBalance, diff, null, null, true);
        }

        public GlDiffDetail(String subjectCode, String subjectName,
                            BigDecimal glTotalBalance, BigDecimal accountTotalBalance,
                            BigDecimal diff, Integer accountCount, String reasonDesc, Boolean repairable) {
            this.subjectCode = subjectCode;
            this.subjectName = subjectName;
            this.glTotalBalance = glTotalBalance;
            this.accountTotalBalance = accountTotalBalance;
            this.diff = diff;
            this.accountCount = accountCount;
            this.reasonDesc = reasonDesc;
            this.repairable = repairable;
        }
    }

    // ==================== 余额核对结果 ====================

    @Data
    public static class BalanceReconciliationResult {
        private final LocalDate accountingDate;
        private final boolean passed;
        private final BigDecimal totalDiff;
        private final List<BalanceDiffDetail> diffDetails;
    }

    @Data
    public static class BalanceDiffDetail {
        private final String accountNo;
        private final String subjectCode;
        private final String diffType; // ACCOUNT_BALANCE or POST_BALANCE
        private final BigDecimal balanceEndBalance;
        private final BigDecimal comparedBalance;
        private final BigDecimal diff;

        public BalanceDiffDetail(String accountNo, String subjectCode, String diffType,
                                 BigDecimal balanceEndBalance, BigDecimal comparedBalance,
                                 BigDecimal diff) {
            this.accountNo = accountNo;
            this.subjectCode = subjectCode;
            this.diffType = diffType;
            this.balanceEndBalance = balanceEndBalance;
            this.comparedBalance = comparedBalance;
            this.diff = diff;
        }
    }
}
