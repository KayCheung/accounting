package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.RedisSequenceGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 期末结转领域服务（Step 17 Task 8）
 * <p>
 * 职责：
 * 1. 按 execute_order 顺序执行启用的结转规则
 * 2. 幂等控制（同日同规则不重复执行）
 * 3. 为匹配余额的账户生成结转凭证
 * 4. 将源账户余额清零
 * 5. 记录结转结果
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodEndTransferDomainService {

    private final PeriodEndTransferRuleRepository ruleRepository;
    private final PeriodEndTransferRecordRepository recordRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final AccountingVoucherRepository voucherRepository;
    private final TransactionTemplate transactionTemplate;
    private final RedisSequenceGenerator seqGen;

    /**
     * 执行所有启用的期末结转规则
     *
     * @param accountingDate 会计日期
     * @return 各规则执行结果列表
     */
    public List<TransferRuleResult> executeTransferRules(LocalDate accountingDate) {
        List<PeriodEndTransferRulePO> rules = ruleRepository.selectEnabledRules(null);
        if (rules.isEmpty()) {
            log.info("[EOD-TRANSFER] 无启用的结转规则: date={}", accountingDate);
            return List.of();
        }

        List<TransferRuleResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (PeriodEndTransferRulePO rule : rules) {
            try {
                // 幂等控制：同日同规则已执行成功则跳过
                if (recordRepository.existsSuccessfulTransfer(accountingDate, rule.getRuleCode())) {
                    log.info("[EOD-TRANSFER] 规则已执行，跳过: ruleCode={}, date={}",
                            rule.getRuleCode(), accountingDate);
                    continue;
                }

                TransferRuleResult result = executeSingleRule(rule, accountingDate);
                results.add(result);
                if (result.getStatus() == TransferRecordStatusEnum.SUCCESS) {
                    successCount++;
                } else {
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("[EOD-TRANSFER-FAILED] rule={}, reason={}",
                        rule.getRuleCode(), e.getMessage(), e);
                String failedTransferNo = seqGen.generate("EODTR", accountingDate, 4, 25);
                writeFailedTransferRecord(rule, accountingDate, failedTransferNo, e.getMessage());
                TransferRuleResult failResult = new TransferRuleResult(
                        rule.getRuleCode(), rule.getRuleName(), failedTransferNo, null,
                        BigDecimal.ZERO, TransferRecordStatusEnum.FAILED,
                        e.getMessage());
                results.add(failResult);
                failedCount++;
            }
        }

        log.info("[EOD-TRANSFER-SUMMARY] date={}, rules={}, success={}, failed={}",
                accountingDate, rules.size(), successCount, failedCount);
        return results;
    }

    /**
     * 执行单条结转规则
     */
    private TransferRuleResult executeSingleRule(PeriodEndTransferRulePO rule, LocalDate accountingDate) {
        String transferNo = generateTransferNo(accountingDate, rule.getRuleCode());

        return transactionTemplate.execute(status -> {
            try {
                // 1. 解析通配符模式
                java.util.regex.Pattern pattern = wildcardToPattern(rule.getSourceSubjectCode());

                // 2. 查找匹配余额账户
                List<AccountBalancePO> balances = accountBalanceRepository.selectByDate(accountingDate);
                List<AccountBalancePO> matchedBalances = balances.stream()
                        .filter(b -> b.getSubjectCode() != null
                                && pattern.matcher(b.getSubjectCode()).matches())
                        .filter(b -> b.getEndBalance() != null
                                && b.getEndBalance().compareTo(BigDecimal.ZERO) != 0)
                        .toList();

                if (matchedBalances.isEmpty()) {
                    log.info("[EOD-TRANSFER] 无符合条件的余额账户: ruleCode={}, pattern={}",
                            rule.getRuleCode(), rule.getSourceSubjectCode());
                    return new TransferRuleResult(
                            rule.getRuleCode(), rule.getRuleName(), transferNo, null,
                            BigDecimal.ZERO, TransferRecordStatusEnum.SUCCESS, null);
                }

                // 3. 计算结转总金额
                BigDecimal totalAmount = BigDecimal.ZERO;
                for (AccountBalancePO balance : matchedBalances) {
                    totalAmount = totalAmount.add(balance.getEndBalance().abs());
                }

                // 4. 生成结转凭证
                String voucherNo = generateTransferVoucher(
                        rule, accountingDate, transferNo, matchedBalances);

                // 5. 余额清零：通过借贷发生额调整（符合财务律法）
                for (AccountBalancePO balance : matchedBalances) {
                    BigDecimal absAmount = balance.getEndBalance().abs();
                    BalanceDirectionEnum direction = balance.getBalanceDirection();
                    if (direction == BalanceDirectionEnum.DEBIT) {
                        // 借方余额账户：结转金额计入贷方发生额
                        // end_balance = begin + debit - credit，增加 credit 使 end_balance = 0
                        balance.setCreditAmount(balance.getCreditAmount().add(absAmount));
                    } else {
                        // 贷方余额账户：结转金额计入借方发生额
                        // end_balance = begin + credit - debit，增加 debit 使 end_balance = 0
                        balance.setDebitAmount(balance.getDebitAmount().add(absAmount));
                    }
                    balance.setEndBalance(BigDecimal.ZERO);
                }
                accountBalanceRepository.batchUpsert(matchedBalances);

                // 6. 记录结转结果
                PeriodEndTransferRecordPO record = new PeriodEndTransferRecordPO();
                record.setTransferNo(transferNo);
                record.setAccountingDate(accountingDate);
                record.setTransferType(rule.getTransferType());
                record.setRuleCode(rule.getRuleCode());
                record.setVoucherNo(voucherNo);
                record.setTotalAmount(totalAmount);
                record.setStatus(TransferRecordStatusEnum.SUCCESS);
                record.setExecuteTime(LocalDateTime.now());
                record.setFinishTime(LocalDateTime.now());
                recordRepository.insert(record);

                log.info("[EOD-TRANSFER] 结转成功: ruleCode={}, transferNo={}, voucherNo={}, amount={}",
                        rule.getRuleCode(), transferNo, voucherNo, totalAmount);

                return new TransferRuleResult(
                        rule.getRuleCode(), rule.getRuleName(), transferNo, voucherNo,
                        totalAmount, TransferRecordStatusEnum.SUCCESS, null);
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    /**
     * 生成结转凭证（含借贷分录）
     */
    private String generateTransferVoucher(
            PeriodEndTransferRulePO rule, LocalDate accountingDate,
            String transferNo, List<AccountBalancePO> balances) {

        String voucherNo = seqGen.generate("VOU", accountingDate, 6, 25);

        BigDecimal totalAmount = balances.stream()
                .map(AccountBalancePO::getEndBalance)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setTxnNo(null);
        voucher.setTraceNo(transferNo);
        voucher.setTraceSeq(1);
        voucher.setVoucherType("结账凭证");
        voucher.setPostingType(PostingTypeEnum.AUTOMATIC);
        voucher.setBusinessCode("EOD");
        voucher.setTradingCode(null);
        voucher.setPayChannel(null);
        voucher.setTradeType(TradeTypeEnum.BLUE);
        voucher.setTradeTime(LocalDateTime.now());
        voucher.setAmount(totalAmount);
        voucher.setStatus(VoucherStatusEnum.POSTED);
        voucher.setAccountingDate(accountingDate);
        voucher.setSummary(renderSummary(rule.getSummaryTemplate(), accountingDate));
        voucher.setBookkeeperName("SYSTEM");
        voucherRepository.insert(voucher);

        boolean isDebitToCredit = rule.getTransferDirection() == TransferDirectionEnum.DEBIT_TO_CREDIT;

        // 借贷发生额累计（用于末尾平衡校验）
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        int rowNum = 0;
        for (AccountBalancePO balance : balances) {
            BigDecimal absAmount = balance.getEndBalance().abs();

            // 源科目分录
            rowNum++;
            AccountingVoucherEntryPO entry = new AccountingVoucherEntryPO();
            entry.setVoucherNo(voucherNo);
            entry.setEntryId(seqGen.generate("ENT", LocalDateTime.now(), "yyyyMMddHHmmssSSS", 4, 2));
            entry.setRowNum(rowNum);
            entry.setSubjectCode(balance.getSubjectCode());
            entry.setAccountNo(balance.getAccountNo());
            entry.setAmount(absAmount);
            entry.setCurrency(balance.getCurrency());
            entry.setSummary(rule.getRuleName());
            entry.setStatus(VoucherEntryStatusEnum.POSTED);
            entry.setAccountingDate(accountingDate);
            entry.setDebitCredit(isDebitToCredit ? DebitCreditEnum.DEBIT : DebitCreditEnum.CREDIT);
            voucherRepository.insertEntry(entry);

            // 累计借贷发生额
            if (isDebitToCredit) {
                totalDebit = totalDebit.add(absAmount);
            } else {
                totalCredit = totalCredit.add(absAmount);
            }

            // 目标科目分录
            rowNum++;
            AccountingVoucherEntryPO targetEntry = new AccountingVoucherEntryPO();
            targetEntry.setVoucherNo(voucherNo);
            targetEntry.setEntryId(seqGen.generate("ENT", LocalDateTime.now(), "yyyyMMddHHmmssSSS", 4, 2));
            targetEntry.setRowNum(rowNum);
            targetEntry.setSubjectCode(rule.getTargetSubjectCode());
            targetEntry.setAccountNo(null);
            targetEntry.setAmount(absAmount);
            targetEntry.setCurrency(balance.getCurrency());
            targetEntry.setSummary(rule.getRuleName());
            targetEntry.setStatus(VoucherEntryStatusEnum.POSTED);
            targetEntry.setAccountingDate(accountingDate);
            targetEntry.setDebitCredit(isDebitToCredit ? DebitCreditEnum.CREDIT : DebitCreditEnum.DEBIT);
            voucherRepository.insertEntry(targetEntry);

            // 累计借贷发生额
            if (isDebitToCredit) {
                totalCredit = totalCredit.add(absAmount);
            } else {
                totalDebit = totalDebit.add(absAmount);
            }
        }

        // 借贷平衡校验：ΣDebit == ΣCredit
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new AccountException(ResultCode.TRIAL_BALANCE_FAILED,
                    "结转凭证借贷不平衡: debit=" + totalDebit + ", credit=" + totalCredit);
        }

        return voucherNo;
    }

    /**
     * 将通配符模式转为 Java 正则表达式
     * <p>
     * 例: "6*" -> "^6.*", "6001" -> "^6001$", "6*1*" -> "^6.*1.*"
     * 比简单前缀匹配更灵活，支持多级通配符
     * </p>
     */
    private java.util.regex.Pattern wildcardToPattern(String wildcard) {
        if (wildcard == null || wildcard.isEmpty()) {
            return java.util.regex.Pattern.compile(".*");
        }
        String regex = wildcard.replace("*", ".*");
        return java.util.regex.Pattern.compile("^" + regex + "$");
    }

    /**
     * 生成结转流水号
     */
    private String generateTransferNo(LocalDate date, String ruleCode) {
        return seqGen.generate("EODTR", date, 4, 25);
    }

    /**
     * 写入失败的结转记录到数据库（独立事务）
     */
    private void writeFailedTransferRecord(PeriodEndTransferRulePO rule, LocalDate accountingDate,
                                            String transferNo, String failReason) {
        transactionTemplate.execute(status -> {
            try {
                PeriodEndTransferRecordPO record = new PeriodEndTransferRecordPO();
                record.setTransferNo(transferNo);
                record.setAccountingDate(accountingDate);
                record.setTransferType(rule.getTransferType());
                record.setRuleCode(rule.getRuleCode());
                record.setVoucherNo(null);
                record.setTotalAmount(BigDecimal.ZERO);
                record.setStatus(TransferRecordStatusEnum.FAILED);
                record.setFailReason(failReason);
                record.setExecuteTime(LocalDateTime.now());
                record.setFinishTime(LocalDateTime.now());
                recordRepository.insert(record);
                return null;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
    }

    /**
     * 渲染摘要模板
     */
    private String renderSummary(String template, LocalDate date) {
        if (template == null || template.isEmpty()) {
            return date.format(DateTimeFormatter.ofPattern("yyyy年MM月期末结转"));
        }
        return template.replace("{year}", String.valueOf(date.getYear()))
                .replace("{month}", String.valueOf(date.getMonthValue()));
    }

    /**
     * 结转规则执行结果
     */
    @Data
    public static class TransferRuleResult {
        private final String ruleCode;
        private final String ruleName;
        private final String transferNo;
        private final String voucherNo;
        private final BigDecimal totalAmount;
        private final TransferRecordStatusEnum status;
        private final String failReason;
    }
}
