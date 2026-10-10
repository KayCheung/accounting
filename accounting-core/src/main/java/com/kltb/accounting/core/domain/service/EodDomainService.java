package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SnapshotTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceSnapshotRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 日终余额计算与快照生成服务（Step 17 Task 7）
 * <p>
 * 职责：
 * 1. 根据已记账分录计算各科目日余额
 * 2. 批量 upsert 到 t_account_balance
 * 3. 生成日快照（月末同时生成月快照）
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EodDomainService {

    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountBalanceSnapshotRepository snapshotRepository;
    private final AccountingVoucherEntryMapper voucherEntryMapper;
    private final SubjectRepository subjectRepository;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 计算指定会计日的各科目日余额
     * <p>
     * 业务规则：
     * 1. 遍历系统中所有有效分户账户（覆盖无交易账户，实现日余额全量自然结转）；
     * 2. 匹配当日凭证发生额（借方发生额、贷方发生额）；
     * 3. 继承前一日（或最近一笔历史）日余额作为期初，按余额方向计算期末余额；
     * 4. 补充凭证分录中可能存在的特殊汇总行（如无账户编号或已注销账户）；
     * 5. 确保总账侧 t_account_balance 与分户侧 t_account 余额 100% 平衡一致。
     * </p>
     *
     * @param accountingDate 会计日期
     * @return 各科目余额列表
     */
    public List<AccountBalancePO> calculateDailyBalances(LocalDate accountingDate) {
        // 1. 查询当日所有已过账分录按账户汇总发生的借贷方金额
        List<Map<String, Object>> entries = voucherEntryMapper.sumEntriesByAccount(accountingDate);
        Map<String, Map<String, Object>> entryMap = new HashMap<>();
        for (Map<String, Object> row : entries) {
            String accountNo = (String) row.get("account_no");
            entryMap.put(accountNo != null ? accountNo : "", row);
        }

        // 2. 查询系统中所有有效分户账户
        List<AccountPO> activeAccounts = accountRepository.selectAllActiveAccounts();

        // 3. 批量加载前一日日余额（避免循环查库 N+1）
        LocalDate previousDate = accountingDate.minusDays(1);
        List<AccountBalancePO> previousDayBalances = accountBalanceRepository.selectByDate(previousDate);
        Map<String, AccountBalancePO> previousDayMap = new HashMap<>();
        for (AccountBalancePO b : previousDayBalances) {
            String no = b.getAccountNo() != null ? b.getAccountNo() : "";
            previousDayMap.put(no, b);
        }

        List<AccountBalancePO> balances = new ArrayList<>();
        Set<String> processedAccountNos = new HashSet<>();

        // 4. 遍历所有有效账户计算或结转当日日余额
        for (AccountPO account : activeAccounts) {
            String accountNo = account.getAccountNo() != null ? account.getAccountNo() : "";
            processedAccountNos.add(accountNo);
            String subjectCode = account.getSubjectCode();

            Map<String, Object> entryRow = entryMap.get(accountNo);
            BigDecimal debitAmount = entryRow != null ? toBigDecimal(entryRow.get("total_debit")) : BigDecimal.ZERO;
            BigDecimal creditAmount = entryRow != null ? toBigDecimal(entryRow.get("total_credit")) : BigDecimal.ZERO;

            AccountBalancePO balance = new AccountBalancePO();
            balance.setAccountingDate(accountingDate);
            balance.setSubjectCode(subjectCode);
            balance.setAccountNo(accountNo);
            balance.setCurrency(account.getCurrency() != null ? account.getCurrency() : Constants.DEFAULT_CURRENCY);
            balance.setDebitAmount(debitAmount);
            balance.setCreditAmount(creditAmount);
            balance.setTenantId(account.getTenantId() != null ? account.getTenantId()
                    : (TenantContext.get() != null ? TenantContext.get() : TenantContext.SYSTEM_TENANT));

            // 获取前一日日余额（支持跨日/历史回溯容错）
            AccountBalancePO previousBalance = previousDayMap.get(accountNo);
            if (previousBalance == null && !accountNo.isEmpty()) {
                previousBalance = accountBalanceRepository.selectLatestBalanceBeforeDate(accountNo, accountingDate);
            }

            if (previousBalance != null) {
                balance.setBeginBalance(previousBalance.getEndBalance());
                balance.setBalanceDirection(previousBalance.getBalanceDirection() != null
                        ? previousBalance.getBalanceDirection()
                        : account.getBalanceDirection());
            } else {
                // 首次记账或首日日切，从账户或科目获取方向与期初
                BalanceDirectionEnum direction = account.getBalanceDirection();
                if (direction == null) {
                    AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
                    direction = (subject != null && subject.getDebitCredit() == DebitCreditEnum.CREDIT)
                            ? BalanceDirectionEnum.CREDIT
                            : BalanceDirectionEnum.DEBIT;
                }
                balance.setBalanceDirection(direction);

                if (debitAmount.compareTo(BigDecimal.ZERO) == 0 && creditAmount.compareTo(BigDecimal.ZERO) == 0) {
                    // 当日无交易，期初余额即为账户当前余额
                    balance.setBeginBalance(account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO);
                } else {
                    // 当日有交易，由于分户余额已在过账时更新，反算当日初始期初
                    BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
                    BigDecimal calcBegin;
                    if (direction == BalanceDirectionEnum.DEBIT) {
                        calcBegin = currentBalance.subtract(debitAmount).add(creditAmount);
                    } else {
                        calcBegin = currentBalance.subtract(creditAmount).add(debitAmount);
                    }
                    if (calcBegin.compareTo(BigDecimal.ZERO) < 0) {
                        calcBegin = account.getOpeningBalance() != null ? account.getOpeningBalance() : BigDecimal.ZERO;
                    }
                    balance.setBeginBalance(calcBegin);
                }
            }

            BigDecimal beginBalance = balance.getBeginBalance();
            BalanceDirectionEnum direction = balance.getBalanceDirection();

            BigDecimal endBalance;
            if (direction == BalanceDirectionEnum.DEBIT) {
                endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
            } else {
                endBalance = beginBalance.add(creditAmount).subtract(debitAmount);
            }

            // 负数余额校验
            if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[EOD-BALANCE-NEGATIVE] accountNo={}, subjectCode={}, endBalance={}, "
                                + "beginBalance={}, debitAmount={}, creditAmount={}",
                        accountNo, subjectCode, endBalance, beginBalance, debitAmount, creditAmount);
                throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
                        "日余额计算出现负值: accountNo=" + accountNo + ", subjectCode=" + subjectCode);
            }

            balance.setEndBalance(endBalance);
            balances.add(balance);
        }

        // 5. 补充可能存在于凭证分录但未在 activeAccounts 中的特殊记录（如无账户编号的汇总分录或已注销账户）
        for (Map<String, Object> row : entries) {
            String accountNo = (String) row.get("account_no");
            String key = accountNo != null ? accountNo : "";
            if (processedAccountNos.contains(key)) {
                continue;
            }

            String subjectCode = (String) row.get("subject_code");
            BigDecimal debitAmount = toBigDecimal(row.get("total_debit"));
            BigDecimal creditAmount = toBigDecimal(row.get("total_credit"));

            AccountBalancePO balance = new AccountBalancePO();
            balance.setAccountingDate(accountingDate);
            balance.setSubjectCode(subjectCode);
            balance.setAccountNo(key);
            balance.setCurrency(Constants.DEFAULT_CURRENCY);
            balance.setDebitAmount(debitAmount);
            balance.setCreditAmount(creditAmount);
            balance.setTenantId(TenantContext.get() != null ? TenantContext.get() : TenantContext.SYSTEM_TENANT);

            AccountBalancePO previousDay = previousDayMap.get(key);
            if (previousDay == null && !key.isEmpty()) {
                previousDay = accountBalanceRepository.selectLatestBalanceBeforeDate(key, accountingDate);
            }

            if (previousDay != null) {
                balance.setBeginBalance(previousDay.getEndBalance());
                balance.setBalanceDirection(previousDay.getBalanceDirection());
            } else {
                AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
                balance.setBeginBalance(BigDecimal.ZERO);
                if (subject != null && subject.getDebitCredit() == DebitCreditEnum.CREDIT) {
                    balance.setBalanceDirection(BalanceDirectionEnum.CREDIT);
                } else {
                    balance.setBalanceDirection(BalanceDirectionEnum.DEBIT);
                }
            }

            BigDecimal beginBalance = balance.getBeginBalance();
            BalanceDirectionEnum direction = balance.getBalanceDirection();

            BigDecimal endBalance;
            if (direction == BalanceDirectionEnum.DEBIT) {
                endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
            } else {
                endBalance = beginBalance.add(creditAmount).subtract(debitAmount);
            }

            if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[EOD-BALANCE-NEGATIVE] accountNo={}, subjectCode={}, endBalance={}, "
                                + "beginBalance={}, debitAmount={}, creditAmount={}",
                        key, subjectCode, endBalance, beginBalance, debitAmount, creditAmount);
                throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
                        "日余额计算出现负值: subjectCode=" + subjectCode);
            }

            balance.setEndBalance(endBalance);
            balances.add(balance);
        }

        return balances;
    }

    /**
     * 批量保存日余额到数据库
     *
     * @param accountingDate 会计日期
     * @param balances       余额列表
     */
    public void upsertDailyBalances(LocalDate accountingDate, List<AccountBalancePO> balances) {
        if (balances == null || balances.isEmpty()) {
            return;
        }

        transactionTemplate.execute(status -> {
            try {
                accountBalanceRepository.batchUpsert(balances);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });

        log.info("[EOD-BALANCE-UPSERT] date={}, count={}", accountingDate, balances.size());
    }

    /**
     * 生成指定会计日的余额快照
     * <p>
     * 每日生成 DAY 类型快照；若为月末则同时生成 MONTH 类型快照。
     * </p>
     *
     * @param accountingDate 会计日期
     * @return 生成的快照总数
     */
    public int generateDailySnapshot(LocalDate accountingDate) {
        List<AccountBalancePO> dayBalances = accountBalanceRepository.selectByDate(accountingDate);
        if (dayBalances == null || dayBalances.isEmpty()) {
            return 0;
        }

        List<AccountBalanceSnapshotPO> snapshots = new ArrayList<>();
        LocalDateTime snapshotTime = LocalDateTime.now();

        for (AccountBalancePO balance : dayBalances) {
            AccountBalanceSnapshotPO snapshot = new AccountBalanceSnapshotPO();
            snapshot.setSnapshotDate(balance.getAccountingDate());
            snapshot.setSnapshotType(SnapshotTypeEnum.DAY);
            snapshot.setSnapshotTime(snapshotTime);
            snapshot.setSubjectCode(balance.getSubjectCode());
            snapshot.setAccountNo(balance.getAccountNo());
            snapshot.setCurrency(balance.getCurrency());
            snapshot.setBalanceDirection(balance.getBalanceDirection());
            snapshot.setBalance(balance.getEndBalance());
            snapshot.setTenantId(balance.getTenantId() != null ? balance.getTenantId() : TenantContext.get());
            snapshot.setExtJson(String.format("{\"debitAmount\":%s,\"creditAmount\":%s}",
                    balance.getDebitAmount(), balance.getCreditAmount()));
            snapshots.add(snapshot);

            // Month-end: also generate MONTH snapshot
            if (isMonthEnd(accountingDate)) {
                AccountBalanceSnapshotPO monthSnapshot = new AccountBalanceSnapshotPO();
                monthSnapshot.setSnapshotDate(balance.getAccountingDate());
                monthSnapshot.setSnapshotType(SnapshotTypeEnum.MONTH);
                monthSnapshot.setSnapshotTime(snapshotTime);
                monthSnapshot.setSubjectCode(balance.getSubjectCode());
                monthSnapshot.setAccountNo(balance.getAccountNo());
                monthSnapshot.setCurrency(balance.getCurrency());
                monthSnapshot.setBalanceDirection(balance.getBalanceDirection());
                monthSnapshot.setBalance(balance.getEndBalance());
                monthSnapshot.setTenantId(balance.getTenantId() != null ? balance.getTenantId() : TenantContext.get());
                monthSnapshot.setExtJson(String.format("{\"debitAmount\":%s,\"creditAmount\":%s}",
                        balance.getDebitAmount(), balance.getCreditAmount()));
                snapshots.add(monthSnapshot);
            }
        }

        if (!snapshots.isEmpty()) {
            transactionTemplate.execute(status -> {
                try {
                    snapshotRepository.batchInsert(snapshots);
                    return null;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw e;
                }
            });
        }

        log.info("[EOD-SNAPSHOT] date={}, daySnapshots={}, totalSnapshots={}",
                accountingDate, dayBalances.size(), snapshots.size());
        return snapshots.size();
    }

    /**
     * 判断指定日期是否为月末
     */
    private boolean isMonthEnd(LocalDate date) {
        return date.getDayOfMonth() == date.lengthOfMonth();
    }

    /**
     * 安全地将对象转换为 BigDecimal
     */
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        return BigDecimal.ZERO;
    }
}
