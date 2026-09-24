package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.SnapshotTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalancePO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountBalanceSnapshotPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountSubjectPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherEntryMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountBalanceSnapshotRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private final TransactionTemplate transactionTemplate;

    /**
     * 计算指定会计日的各科目日余额
     *
     * @param accountingDate 会计日期
     * @return 各科目余额列表
     */
    public List<AccountBalancePO> calculateDailyBalances(LocalDate accountingDate) {
        List<Map<String, Object>> entries = voucherEntryMapper.sumEntriesByAccount(accountingDate);

        List<AccountBalancePO> balances = new ArrayList<>();

        for (Map<String, Object> row : entries) {
            String subjectCode = (String) row.get("subject_code");
            String accountNo = (String) row.get("account_no");
            BigDecimal debitAmount = toBigDecimal(row.get("total_debit"));
            BigDecimal creditAmount = toBigDecimal(row.get("total_credit"));

            AccountBalancePO balance = new AccountBalancePO();
            balance.setAccountingDate(accountingDate);
            balance.setSubjectCode(subjectCode);
            balance.setAccountNo(accountNo != null ? accountNo : "");
            balance.setCurrency("CNY");
            balance.setDebitAmount(debitAmount);
            balance.setCreditAmount(creditAmount);

            // Query previous day end balance for the same account
            AccountBalancePO previousDay = accountBalanceRepository
                    .selectPreviousDayBalance(accountNo != null ? accountNo : "", accountingDate.minusDays(1));
            if (previousDay != null) {
                balance.setBeginBalance(previousDay.getEndBalance());
                balance.setBalanceDirection(previousDay.getBalanceDirection());
            } else {
                // 新开户/首次记账，从科目表获取默认余额方向
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

            // Negative balance check
            if (endBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("[EOD-BALANCE-NEGATIVE] subjectCode={}, endBalance={}, "
                                + "beginBalance={}, debitAmount={}, creditAmount={}",
                        subjectCode, endBalance, beginBalance, debitAmount, creditAmount);
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
