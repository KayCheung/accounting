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
import com.kltb.accounting.core.shared.util.FinancialAmountUtil;
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

        // 3.5 预加载当前会计日之前所有已过账凭证按账户累计借贷发生额（权威历史期初累计源）
        Map<String, Map<String, Object>> histEntryMap = new HashMap<>();
        try {
            List<Map<String, Object>> histRows = voucherEntryMapper.sumEntriesBeforeDateGroupAccount(accountingDate);
            if (histRows != null) {
                for (Map<String, Object> r : histRows) {
                    String acctNo = (String) r.get("account_no");
                    if (acctNo != null) {
                        histEntryMap.put(acctNo, r);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[EOD-BALANCE] 预加载历史凭证发生额失败，将退化为结转余额: {}", e.getMessage());
        }

        List<AccountBalancePO> balances = new ArrayList<>();
        Set<String> processedAccountNos = new HashSet<>();

        // 4. 遍历所有有效账户计算或结转当日日余额
        for (AccountPO account : activeAccounts) {
            String accountNo = account.getAccountNo() != null ? account.getAccountNo() : "";
            processedAccountNos.add(accountNo);
            String subjectCode = account.getSubjectCode();

            Map<String, Object> entryRow = entryMap.get(accountNo);
            BigDecimal debitAmount = entryRow != null ? FinancialAmountUtil.toBigDecimal(entryRow.get("total_debit")) : BigDecimal.ZERO;
            BigDecimal creditAmount = entryRow != null ? FinancialAmountUtil.toBigDecimal(entryRow.get("total_credit")) : BigDecimal.ZERO;

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

            AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
            boolean isBidirectional = subject != null && subject.getDebitCredit() != null && subject.getDebitCredit().isBidirectional();
            boolean isOffBalance = subject != null && subject.getDebitCredit() != null && subject.getDebitCredit().isOffBalance();

            BalanceDirectionEnum direction = account.getBalanceDirection();
            if (direction == null) {
                if (subject != null && subject.getDebitCredit() != null) {
                    if (isBidirectional || isOffBalance) {
                        direction = BalanceDirectionEnum.FLAT;
                    } else if (subject.getDebitCredit() == com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.CREDIT) {
                        direction = BalanceDirectionEnum.CREDIT;
                    } else {
                        direction = BalanceDirectionEnum.DEBIT;
                    }
                } else {
                    direction = BalanceDirectionEnum.DEBIT;
                }
            }

            // 优先依据历史凭证累计发生额核定权威期初余额（彻底解决 MQ 延迟落账或历史跳日导致结转期初失真）
            Map<String, Object> histRow = histEntryMap.get(accountNo);
            BigDecimal beginBalance;
            BalanceDirectionEnum beginDirection = direction;

            if (histRow != null) {
                BigDecimal histDebit = FinancialAmountUtil.toBigDecimal(histRow.get("total_debit"));
                BigDecimal histCredit = FinancialAmountUtil.toBigDecimal(histRow.get("total_credit"));
                if (isBidirectional) {
                    BigDecimal net = histDebit.subtract(histCredit);
                    if (net.compareTo(BigDecimal.ZERO) > 0) {
                        beginBalance = net;
                        beginDirection = BalanceDirectionEnum.DEBIT;
                    } else if (net.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = net.abs();
                        beginDirection = BalanceDirectionEnum.CREDIT;
                    } else {
                        beginBalance = BigDecimal.ZERO;
                        beginDirection = BalanceDirectionEnum.FLAT;
                    }
                } else if (isOffBalance || direction == BalanceDirectionEnum.DEBIT) {
                    beginBalance = histDebit.subtract(histCredit);
                    if (beginBalance.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = BigDecimal.ZERO;
                    }
                } else {
                    beginBalance = histCredit.subtract(histDebit);
                    if (beginBalance.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = BigDecimal.ZERO;
                    }
                }

                if (previousBalance != null && previousBalance.getEndBalance().compareTo(beginBalance) != 0) {
                    log.info("[EOD-BALANCE-SYNC] 账户 [{}] 前序结转余额 ({}) 与历史凭证累计期初 ({}) 不一致，已自动采用权威凭证累计期初校准",
                            accountNo, previousBalance.getEndBalance(), beginBalance);
                }
            } else if (previousBalance != null) {
                beginBalance = previousBalance.getEndBalance();
                beginDirection = previousBalance.getBalanceDirection() != null ? previousBalance.getBalanceDirection() : direction;
            } else {
                beginBalance = account.getOpeningBalance() != null ? account.getOpeningBalance() : BigDecimal.ZERO;
                beginDirection = direction;
            }

            balance.setBeginBalance(beginBalance);
            balance.setBalanceDirection(beginDirection);

            BigDecimal endBalance;
            BalanceDirectionEnum endDirection = direction;
            if (isBidirectional) {
                // 双向科目：跨方向动态轧差与翻转
                BigDecimal signedBegin = (direction == BalanceDirectionEnum.DEBIT)
                        ? beginBalance
                        : (direction == BalanceDirectionEnum.CREDIT ? beginBalance.negate() : BigDecimal.ZERO);
                BigDecimal signedNet = signedBegin.add(debitAmount).subtract(creditAmount);
                if (signedNet.compareTo(BigDecimal.ZERO) > 0) {
                    endBalance = signedNet;
                    endDirection = BalanceDirectionEnum.DEBIT;
                } else if (signedNet.compareTo(BigDecimal.ZERO) < 0) {
                    endBalance = signedNet.abs();
                    endDirection = BalanceDirectionEnum.CREDIT;
                } else {
                    endBalance = BigDecimal.ZERO;
                    endDirection = BalanceDirectionEnum.FLAT;
                }
            } else if (isOffBalance) {
                // 表外科目：借增贷减
                endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
                endDirection = BalanceDirectionEnum.FLAT;
            } else {
                // 单向科目：遵循传统资产/负债方向
                if (beginDirection == BalanceDirectionEnum.DEBIT) {
                    endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
                } else {
                    endBalance = beginBalance.add(creditAmount).subtract(debitAmount);
                }
            }

            // 负数余额校验（双向科目已翻转为绝对值，仅对非双向科目校验）
            if (!isBidirectional && endBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[EOD-BALANCE-NEGATIVE] accountNo={}, subjectCode={}, endBalance={}, "
                                + "beginBalance={}, debitAmount={}, creditAmount={}",
                        accountNo, subjectCode, endBalance, beginBalance, debitAmount, creditAmount);
                throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
                        "日余额计算出现负值: accountNo=" + accountNo + ", subjectCode=" + subjectCode);
            }

            balance.setBalanceDirection(endDirection);
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
            BigDecimal debitAmount = FinancialAmountUtil.toBigDecimal(row.get("total_debit"));
            BigDecimal creditAmount = FinancialAmountUtil.toBigDecimal(row.get("total_credit"));

            AccountBalancePO balance = new AccountBalancePO();
            balance.setAccountingDate(accountingDate);
            balance.setSubjectCode(subjectCode);
            balance.setAccountNo(key);
            balance.setCurrency(Constants.DEFAULT_CURRENCY);
            balance.setDebitAmount(debitAmount);
            balance.setCreditAmount(creditAmount);
            balance.setTenantId(TenantContext.get() != null ? TenantContext.get() : TenantContext.SYSTEM_TENANT);

            AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
            boolean isBidirectional = subject != null && subject.getDebitCredit() != null && subject.getDebitCredit().isBidirectional();
            boolean isOffBalance = subject != null && subject.getDebitCredit() != null && subject.getDebitCredit().isOffBalance();

            BalanceDirectionEnum direction = BalanceDirectionEnum.DEBIT;
            if (subject != null && subject.getDebitCredit() != null) {
                if (isBidirectional || isOffBalance) {
                    direction = BalanceDirectionEnum.FLAT;
                } else if (subject.getDebitCredit() == com.kltb.accounting.core.domain.enums.SubjectBalanceDirectionEnum.CREDIT) {
                    direction = BalanceDirectionEnum.CREDIT;
                }
            }

            AccountBalancePO previousDay = previousDayMap.get(key);
            if (previousDay == null && !key.isEmpty()) {
                previousDay = accountBalanceRepository.selectLatestBalanceBeforeDate(key, accountingDate);
            }

            Map<String, Object> histRow = histEntryMap.get(key);
            BigDecimal beginBalance;
            BalanceDirectionEnum beginDirection = direction;

            if (histRow != null) {
                BigDecimal histDebit = FinancialAmountUtil.toBigDecimal(histRow.get("total_debit"));
                BigDecimal histCredit = FinancialAmountUtil.toBigDecimal(histRow.get("total_credit"));
                if (isBidirectional) {
                    BigDecimal net = histDebit.subtract(histCredit);
                    if (net.compareTo(BigDecimal.ZERO) > 0) {
                        beginBalance = net;
                        beginDirection = BalanceDirectionEnum.DEBIT;
                    } else if (net.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = net.abs();
                        beginDirection = BalanceDirectionEnum.CREDIT;
                    } else {
                        beginBalance = BigDecimal.ZERO;
                        beginDirection = BalanceDirectionEnum.FLAT;
                    }
                } else if (isOffBalance || direction == BalanceDirectionEnum.DEBIT) {
                    beginBalance = histDebit.subtract(histCredit);
                    if (beginBalance.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = BigDecimal.ZERO;
                    }
                } else {
                    beginBalance = histCredit.subtract(histDebit);
                    if (beginBalance.compareTo(BigDecimal.ZERO) < 0) {
                        beginBalance = BigDecimal.ZERO;
                    }
                }
            } else if (previousDay != null) {
                beginBalance = previousDay.getEndBalance();
                beginDirection = previousDay.getBalanceDirection() != null ? previousDay.getBalanceDirection() : direction;
            } else {
                beginBalance = BigDecimal.ZERO;
                beginDirection = direction;
            }

            balance.setBeginBalance(beginBalance);
            balance.setBalanceDirection(beginDirection);

            BigDecimal endBalance;
            BalanceDirectionEnum endDirection = beginDirection;
            if (isBidirectional) {
                // 双向科目：跨方向动态轧差与翻转
                BigDecimal signedBegin = (direction == BalanceDirectionEnum.DEBIT)
                        ? beginBalance
                        : (direction == BalanceDirectionEnum.CREDIT ? beginBalance.negate() : BigDecimal.ZERO);
                BigDecimal signedNet = signedBegin.add(debitAmount).subtract(creditAmount);
                if (signedNet.compareTo(BigDecimal.ZERO) > 0) {
                    endBalance = signedNet;
                    endDirection = BalanceDirectionEnum.DEBIT;
                } else if (signedNet.compareTo(BigDecimal.ZERO) < 0) {
                    endBalance = signedNet.abs();
                    endDirection = BalanceDirectionEnum.CREDIT;
                } else {
                    endBalance = BigDecimal.ZERO;
                    endDirection = BalanceDirectionEnum.FLAT;
                }
            } else if (isOffBalance) {
                // 表外科目：借增贷减
                endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
                endDirection = BalanceDirectionEnum.FLAT;
            } else {
                // 单向科目：遵循传统资产/负债方向
                if (beginDirection == BalanceDirectionEnum.DEBIT) {
                    endBalance = beginBalance.add(debitAmount).subtract(creditAmount);
                } else {
                    endBalance = beginBalance.add(creditAmount).subtract(debitAmount);
                }
            }

            // 负数余额校验（双向科目已翻转为绝对值，仅对非双向科目校验）
            if (!isBidirectional && endBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[EOD-BALANCE-NEGATIVE] accountNo={}, subjectCode={}, endBalance={}, "
                                + "beginBalance={}, debitAmount={}, creditAmount={}",
                        key, subjectCode, endBalance, beginBalance, debitAmount, creditAmount);
                throw new AccountException(ResultCode.DAILY_BALANCE_NEGATIVE,
                        "日余额计算出现负值: subjectCode=" + subjectCode);
            }

            balance.setBalanceDirection(endDirection);
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
}
