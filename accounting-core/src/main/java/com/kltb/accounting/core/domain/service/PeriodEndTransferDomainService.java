package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.response.TransferPreviewEntryItemResponse;
import com.kltb.accounting.api.response.TransferPreviewResponse;
import com.kltb.accounting.api.response.TransferPreviewRuleItemResponse;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.BusinessNoGenerator;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.*;
import com.kltb.accounting.core.shared.context.TenantContext;
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
import java.util.*;
import java.util.regex.Pattern;

/**
 * 期末结转领域服务
 * <p>
 * 职责：
 * 1. 结转试算与分录预览（只读不落库）
 * 2. 按 execute_order 顺序执行启用的结转规则
 * 3. 幂等控制与强制重试支持
 * 4. 为匹配余额的账户生成结转凭证
 * 5. 驱动过账引擎 PostingDomainService 执行真实分户过账（扣减源账户、增加目标账户、写入明细账流水）
 * 6. 联动 EodDomainService 自然轧平并刷新日余额与快照
 * 7. 记录结转结果
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
    private final SubjectRepository subjectRepository;
    private final TransactionTemplate transactionTemplate;
    private final BusinessNoGenerator businessNoGenerator;

    private final AccountRepository accountRepository;
    private final AccountOpeningDomainService accountOpeningDomainService;
    private final PostingDomainService postingDomainService;
    private final EodDomainService eodDomainService;


    /**
     * 日切标准阶段调用：执行所有符合自动触发条件的期末结转规则
     * <p>
     * 自动结转条件：已启用 + 支持自动结转(autoTransfer=true) + 周期满足(每日/月末/季末/年末)
     *
     * @param accountingDate 会计日期
     * @return 各规则执行结果列表
     */
    public List<TransferRuleResult> executeTransferRules(LocalDate accountingDate) {
        return executeAutoTransfer(accountingDate, null);
    }

    /**
     * 自动结转执行（日切流水线或定时任务专用）
     *
     * @param accountingDate 会计日期
     * @return 执行结果列表
     */
    public List<TransferRuleResult> executeAutoTransfer(LocalDate accountingDate) {
        return executeAutoTransfer(accountingDate, null);
    }

    /**
     * 自动结转执行（日切流水线或定时任务专用）
     *
     * @param accountingDate 会计日期
     * @param transferType   结转类型过滤（选填）
     * @return 执行结果列表
     */
    public List<TransferRuleResult> executeAutoTransfer(LocalDate accountingDate, Integer transferType) {
        TransferTypeEnum typeEnum = transferType != null ? TransferTypeEnum.fromCode(transferType) : null;

        List<PeriodEndTransferRulePO> rules = ruleRepository.selectAutoTriggerableRules(accountingDate, typeEnum);
        log.info("[EOD-TRANSFER-AUTO] 扫描自动结转规则: date={}, 自动可触发规则数={}", accountingDate, rules.size());
        return doExecuteRuleList(rules, accountingDate, false);
    }

    /**
     * 手动执行期末结转（支持指定规则、指定类型与强制重试）
     *
     * @param accountingDate 会计日期
     * @param ruleCode       指定规则编码（选填，null 表示执行所有符合条件的启用规则）
     * @param transferType   结转类型过滤（选填）
     * @param forceRetry     是否强制重新执行（若已执行成功，true 允许重新执行）
     * @return 执行结果列表
     */
    public List<TransferRuleResult> executeTransfer(LocalDate accountingDate, String ruleCode,
                                                    Integer transferType, boolean forceRetry) {
        List<PeriodEndTransferRulePO> rules = resolveRulesToExecute(ruleCode, transferType);
        return doExecuteRuleList(rules, accountingDate, forceRetry);
    }

    /**
     * 批量执行规则清单
     */
    private List<TransferRuleResult> doExecuteRuleList(List<PeriodEndTransferRulePO> rules,
                                                       LocalDate accountingDate, boolean forceRetry) {
        if (rules.isEmpty()) {
            log.info("[EOD-TRANSFER] 无符合条件的启用结转规则: date={}", accountingDate);
            return List.of();
        }

        List<TransferRuleResult> results = new ArrayList<>();
        int successCount = 0;
        int failedCount = 0;

        for (PeriodEndTransferRulePO rule : rules) {
            try {
                // 幂等控制：同日同规则已执行成功且未要求强制重试时，跳过
                if (!forceRetry && recordRepository.existsSuccessfulTransfer(accountingDate, rule.getRuleCode())) {
                    log.info("[EOD-TRANSFER] 规则已执行，跳过: ruleCode={}, date={}",
                            rule.getRuleCode(), accountingDate);
                    continue;
                }

                if (forceRetry) {
                    recordRepository.deleteByDateAndRule(accountingDate, rule.getRuleCode());
                }

                TransferRuleResult result = executeSingleRule(rule, accountingDate);
                if (result != null) {
                    results.add(result);
                    if (result.getStatus() == TransferRecordStatusEnum.SUCCESS) {
                        successCount++;
                    } else {
                        failedCount++;
                    }
                }

            } catch (Exception e) {
                log.error("[EOD-TRANSFER-FAILED] rule={}, reason={}",
                        rule.getRuleCode(), e.getMessage(), e);
                String failedTransferNo = businessNoGenerator.generateTransferNo(accountingDate);
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
     * 结转试算与分录预览（只读不落库）
     *
     * @param accountingDate 会计日期
     * @param ruleCode       指定规则编码（选填）
     * @param transferType   结转类型过滤（选填）
     * @return 试算预览详情
     */
    public TransferPreviewResponse previewTransfer(LocalDate accountingDate, String ruleCode, Integer transferType) {
        List<PeriodEndTransferRulePO> rules = resolveRulesToExecute(ruleCode, transferType);

        TransferPreviewResponse response = new TransferPreviewResponse();
        response.setAccountingDate(accountingDate);
        response.setTotalRules(rules.size());

        List<TransferPreviewRuleItemResponse> rulePreviews = new ArrayList<>();
        BigDecimal grandTotalAmount = BigDecimal.ZERO;
        int activeRules = 0;
        boolean allBalanced = true;

        for (PeriodEndTransferRulePO rule : rules) {
            List<AccountBalancePO> matchedBalances = findPendingBalances(rule, accountingDate);

            TransferPreviewRuleItemResponse ruleItem = new TransferPreviewRuleItemResponse();
            ruleItem.setRuleCode(rule.getRuleCode());
            ruleItem.setRuleName(rule.getRuleName());
            ruleItem.setTransferType(rule.getTransferType() != null ? rule.getTransferType().getCode() : null);
            ruleItem.setTransferTypeDesc(rule.getTransferType() != null ? rule.getTransferType().getDesc() : "");
            ruleItem.setSourceSubjectCode(rule.getSourceSubjectCode());
            ruleItem.setTargetSubjectCode(rule.getTargetSubjectCode());
            ruleItem.setAutoTransfer(rule.getAutoTransfer());
            ruleItem.setPeriodCycle(rule.getPeriodCycle() != null ? rule.getPeriodCycle().getCode() : null);
            ruleItem.setPeriodCycleDesc(rule.getPeriodCycle() != null ? rule.getPeriodCycle().getDesc() : "");

            AccountSubjectPO targetSubject = subjectRepository.selectByCode(rule.getTargetSubjectCode());
            ruleItem.setTargetSubjectName(targetSubject != null ? targetSubject.getSubjectName() : "");
            ruleItem.setMatchedAccountCount(matchedBalances.size());

            // 预解析目标账户编号展示
            AccountPO targetAccount = null;
            try {
                targetAccount = resolveTargetAccount(rule.getTargetSubjectCode());
            } catch (Exception ignored) {
            }
            String targetAccountNo = targetAccount != null ? targetAccount.getAccountNo() : "系统自动开立";

            BigDecimal ruleTotalAmount = BigDecimal.ZERO;
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            List<TransferPreviewEntryItemResponse> entries = new ArrayList<>();

            boolean isDebitToCredit = rule.getTransferDirection() == TransferDirectionEnum.DEBIT_TO_CREDIT;
            int rowNum = 0;

            for (AccountBalancePO balance : matchedBalances) {
                BigDecimal absAmount = balance.getEndBalance().abs();
                ruleTotalAmount = ruleTotalAmount.add(absAmount);

                AccountSubjectPO srcSubject = subjectRepository.selectByCode(balance.getSubjectCode());
                String srcSubjectName = srcSubject != null ? srcSubject.getSubjectName() : "";

                // 源科目分录行：借方余额转出记贷方，贷方余额转出记借方
                rowNum++;
                TransferPreviewEntryItemResponse srcEntry = new TransferPreviewEntryItemResponse();
                srcEntry.setRowNum(rowNum);
                srcEntry.setSubjectCode(balance.getSubjectCode());
                srcEntry.setSubjectName(srcSubjectName);
                srcEntry.setAccountNo(StrUtil.isNotBlank(balance.getAccountNo()) ? balance.getAccountNo() : "待绑定账户");
                srcEntry.setCurrency(balance.getCurrency());
                srcEntry.setSummary(rule.getRuleName());

                if (isDebitToCredit) {
                    srcEntry.setDebitCredit(DebitCreditEnum.CREDIT.getCode());
                    srcEntry.setDebitCreditDesc("贷");
                    srcEntry.setDebitAmount(null);
                    srcEntry.setCreditAmount(absAmount);
                    totalCredit = totalCredit.add(absAmount);
                } else {
                    srcEntry.setDebitCredit(DebitCreditEnum.DEBIT.getCode());
                    srcEntry.setDebitCreditDesc("借");
                    srcEntry.setDebitAmount(absAmount);
                    srcEntry.setCreditAmount(null);
                    totalDebit = totalDebit.add(absAmount);
                }
                entries.add(srcEntry);
            }

            // 目标科目汇总分录行（复合分录呈现）
            if (ruleTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
                rowNum++;
                TransferPreviewEntryItemResponse targetEntry = new TransferPreviewEntryItemResponse();
                targetEntry.setRowNum(rowNum);
                targetEntry.setSubjectCode(rule.getTargetSubjectCode());
                targetEntry.setSubjectName(ruleItem.getTargetSubjectName());
                targetEntry.setAccountNo(targetAccountNo);
                targetEntry.setCurrency(matchedBalances.get(0).getCurrency());
                targetEntry.setSummary(rule.getRuleName());

                if (isDebitToCredit) {
                    targetEntry.setDebitCredit(DebitCreditEnum.DEBIT.getCode());
                    targetEntry.setDebitCreditDesc("借");
                    targetEntry.setDebitAmount(ruleTotalAmount);
                    targetEntry.setCreditAmount(null);
                    totalDebit = totalDebit.add(ruleTotalAmount);
                } else {
                    targetEntry.setDebitCredit(DebitCreditEnum.CREDIT.getCode());
                    targetEntry.setDebitCreditDesc("贷");
                    targetEntry.setDebitAmount(null);
                    targetEntry.setCreditAmount(ruleTotalAmount);
                    totalCredit = totalCredit.add(ruleTotalAmount);
                }
                entries.add(targetEntry);
            }

            ruleItem.setTotalAmount(ruleTotalAmount);
            ruleItem.setTotalDebitAmount(totalDebit);
            ruleItem.setTotalCreditAmount(totalCredit);
            ruleItem.setBalanced(totalDebit.compareTo(totalCredit) == 0);
            ruleItem.setEntries(entries);

            if (!ruleItem.isBalanced()) {
                allBalanced = false;
            }

            if (ruleTotalAmount.compareTo(BigDecimal.ZERO) > 0) {
                activeRules++;
                grandTotalAmount = grandTotalAmount.add(ruleTotalAmount);
            }

            rulePreviews.add(ruleItem);
        }

        response.setActiveRules(activeRules);
        response.setGrandTotalAmount(grandTotalAmount);
        response.setAllBalanced(allBalanced);
        response.setRulePreviews(rulePreviews);

        return response;
    }

    /**
     * 解析待执行的规则列表
     */
    private List<PeriodEndTransferRulePO> resolveRulesToExecute(String ruleCode, Integer transferType) {
        if (ruleCode != null && !ruleCode.isBlank()) {
            PeriodEndTransferRulePO rule = ruleRepository.findByRuleCode(ruleCode);
            if (rule != null && rule.getStatus() == AvailableStatusEnum.ENABLED) {
                return List.of(rule);
            }
            return List.of();
        }

        TransferTypeEnum typeEnum = transferType != null ? TransferTypeEnum.fromCode(transferType) : null;
        return ruleRepository.selectEnabledRules(typeEnum);
    }

    /**
     * 查找待结转的余额项（优先从日余额表读取；若未生成日余额则从活跃账户表读取实时余额）
     */
    private List<AccountBalancePO> findPendingBalances(PeriodEndTransferRulePO rule, LocalDate accountingDate) {
        Pattern pattern = wildcardToPattern(rule.getSourceSubjectCode());
        List<AccountBalancePO> balances = accountBalanceRepository.selectByDate(accountingDate);
        if (balances != null && !balances.isEmpty()) {
            List<AccountBalancePO> matched = balances.stream()
                    .filter(b -> b.getSubjectCode() != null && pattern.matcher(b.getSubjectCode()).matches())
                    .filter(b -> b.getEndBalance() != null && b.getEndBalance().compareTo(BigDecimal.ZERO) != 0)
                    .toList();
            if (!matched.isEmpty()) {
                return matched;
            }
        }

        // 兼容日切前或未生成日余额时的场景：直接从 t_account 查询匹配科目且有余额的活跃账户
        List<AccountPO> allAccounts = accountRepository.selectAllActiveAccounts();
        List<AccountBalancePO> dynamicBalances = new ArrayList<>();
        for (AccountPO acc : allAccounts) {
            if (acc.getSubjectCode() != null && pattern.matcher(acc.getSubjectCode()).matches()) {
                if (acc.getBalance() != null && acc.getBalance().compareTo(BigDecimal.ZERO) != 0) {
                    AccountBalancePO bal = new AccountBalancePO();
                    bal.setAccountingDate(accountingDate);
                    bal.setSubjectCode(acc.getSubjectCode());
                    bal.setAccountNo(acc.getAccountNo());
                    bal.setCurrency(acc.getCurrency() != null ? acc.getCurrency() : Constants.DEFAULT_CURRENCY);
                    bal.setBalanceDirection(acc.getBalanceDirection());
                    bal.setDebitAmount(BigDecimal.ZERO);
                    bal.setCreditAmount(BigDecimal.ZERO);
                    bal.setEndBalance(acc.getBalance());
                    bal.setTenantId(acc.getTenantId() != null ? acc.getTenantId() : TenantContext.get());
                    dynamicBalances.add(bal);
                }
            }
        }
        return dynamicBalances;
    }

    /**
     * 解析目标科目对应的实体分户账户（优先获取内部账户，若不存在则自愈开户）
     */
    private AccountPO resolveTargetAccount(String targetSubjectCode) {
        List<AccountPO> accounts = accountRepository.selectBySubjectCode(targetSubjectCode);
        if (accounts != null && !accounts.isEmpty()) {
            for (AccountPO acc : accounts) {
                if ("INNER".equals(acc.getOwnerId()) && acc.getStatus() == AccountStatusEnum.NORMAL) {
                    return acc;
                }
            }
            for (AccountPO acc : accounts) {
                if (acc.getStatus() == AccountStatusEnum.NORMAL) {
                    return acc;
                }
            }
        }
        return accountOpeningDomainService.openInternalAccount(targetSubjectCode);
    }

    /**
     * 解析源余额对应的实体分户账户（优先按账号查询，若缺失则按科目匹配或自愈开户）
     */
    private AccountPO resolveSourceAccount(AccountBalancePO balance) {
        if (StrUtil.isNotBlank(balance.getAccountNo())) {
            AccountPO acc = accountRepository.selectByAccountNo(balance.getAccountNo());
            if (acc != null) {
                return acc;
            }
        }
        List<AccountPO> accounts = accountRepository.selectBySubjectCode(balance.getSubjectCode());
        if (accounts != null && !accounts.isEmpty()) {
            for (AccountPO acc : accounts) {
                if (acc.getStatus() == AccountStatusEnum.NORMAL) {
                    return acc;
                }
            }
        }
        return accountOpeningDomainService.openInternalAccount(balance.getSubjectCode());
    }

    /**
     * 执行单条结转规则
     */
    private TransferRuleResult executeSingleRule(PeriodEndTransferRulePO rule, LocalDate accountingDate) {
        String transferNo = generateTransferNo(accountingDate, rule.getRuleCode());

        return transactionTemplate.execute(status -> {
            try {
                // 1. 查找待结转余额项
                List<AccountBalancePO> matchedBalances = findPendingBalances(rule, accountingDate);
                if (matchedBalances.isEmpty()) {
                    log.info("[EOD-TRANSFER] 无符合条件的余额账户: ruleCode={}, pattern={}",
                            rule.getRuleCode(), rule.getSourceSubjectCode());
                    return new TransferRuleResult(
                            rule.getRuleCode(), rule.getRuleName(), transferNo, null,
                            BigDecimal.ZERO, TransferRecordStatusEnum.SUCCESS, null);
                }

                // 2. 解析目标分户账户与科目
                AccountPO targetAccount = resolveTargetAccount(rule.getTargetSubjectCode());
                AccountSubjectPO targetSubject = subjectRepository.selectByCode(rule.getTargetSubjectCode());

                // 3. 计算结转总金额
                BigDecimal totalAmount = BigDecimal.ZERO;
                for (AccountBalancePO balance : matchedBalances) {
                    totalAmount = totalAmount.add(balance.getEndBalance().abs());
                }

                // 4. 生成统一凭证头
                String voucherNo = businessNoGenerator.generateVoucherNo("PET", accountingDate);
                String txnNo = businessNoGenerator.generateTxnNo(accountingDate);

                AccountingVoucherPO voucher = new AccountingVoucherPO();
                voucher.setVoucherNo(voucherNo);
                voucher.setTxnNo(txnNo);
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
                voucher.setStatus(VoucherStatusEnum.PENDING);
                voucher.setAccountingDate(accountingDate);
                voucher.setSummary(renderSummary(rule.getSummaryTemplate(), accountingDate));
                voucher.setBookkeeperName(Constants.SYSTEM_OPERATOR);
                voucher.setTenantId(TenantContext.get());
                voucherRepository.insert(voucher);

                // 5. 构造转出与转入分录列表
                List<AccountingVoucherEntryPO> entries = new ArrayList<>();
                boolean isDebitToCredit = rule.getTransferDirection() == TransferDirectionEnum.DEBIT_TO_CREDIT;
                DebitCreditEnum targetSubjectDir = (targetSubject != null && targetSubject.getDebitCredit() != null)
                        ? targetSubject.getDebitCredit() : DebitCreditEnum.CREDIT;

                BigDecimal totalDebit = BigDecimal.ZERO;
                BigDecimal totalCredit = BigDecimal.ZERO;
                int rowNum = 0;

                for (AccountBalancePO balance : matchedBalances) {
                    BigDecimal absAmount = balance.getEndBalance().abs();
                    AccountPO srcAccount = resolveSourceAccount(balance);

                    // 源科目分录：借方余额转出记贷方，贷方余额转出记借方
                    DebitCreditEnum srcDc = isDebitToCredit ? DebitCreditEnum.CREDIT : DebitCreditEnum.DEBIT;
                    ChangeDirectionEnum srcChangeDir = ChangeDirectionEnum.DECREASE; // 结转清零必为减少

                    rowNum++;
                    AccountingVoucherEntryPO srcEntry = new AccountingVoucherEntryPO();
                    srcEntry.setVoucherNo(voucherNo);
                    srcEntry.setEntryId(businessNoGenerator.generateEntryId());
                    srcEntry.setRowNum(rowNum);
                    srcEntry.setSubjectCode(balance.getSubjectCode());
                    srcEntry.setAccountNo(srcAccount.getAccountNo());
                    srcEntry.setDebitCredit(srcDc);
                    srcEntry.setChangeDirection(srcChangeDir);
                    srcEntry.setAmount(absAmount);
                    srcEntry.setCurrency(balance.getCurrency());
                    srcEntry.setSummary(rule.getRuleName());
                    srcEntry.setStatus(VoucherEntryStatusEnum.PENDING);
                    srcEntry.setAccountingDate(accountingDate);
                    srcEntry.setUnilateral(1);
                    srcEntry.setBuffered(0);
                    srcEntry.setExchangeRate(BigDecimal.ONE);
                    srcEntry.setUnitPrice(BigDecimal.ZERO);
                    srcEntry.setQuantity(0);
                    srcEntry.setPricingUnit("");
                    srcEntry.setTenantId(TenantContext.get());
                    voucherRepository.insertEntry(srcEntry);
                    entries.add(srcEntry);

                    if (srcDc == DebitCreditEnum.DEBIT) {
                        totalDebit = totalDebit.add(absAmount);
                    } else {
                        totalCredit = totalCredit.add(absAmount);
                    }
                }

                // 目标科目汇总分录（复合分录：多借一贷或一借多贷）
                DebitCreditEnum targetDc = isDebitToCredit ? DebitCreditEnum.DEBIT : DebitCreditEnum.CREDIT;
                ChangeDirectionEnum targetChangeDir = (targetDc == targetSubjectDir)
                        ? ChangeDirectionEnum.INCREASE : ChangeDirectionEnum.DECREASE;

                rowNum++;
                AccountingVoucherEntryPO targetEntry = new AccountingVoucherEntryPO();
                targetEntry.setVoucherNo(voucherNo);
                targetEntry.setEntryId(businessNoGenerator.generateEntryId());
                targetEntry.setRowNum(rowNum);
                targetEntry.setSubjectCode(rule.getTargetSubjectCode());
                targetEntry.setAccountNo(targetAccount.getAccountNo());
                targetEntry.setDebitCredit(targetDc);
                targetEntry.setChangeDirection(targetChangeDir);
                targetEntry.setAmount(totalAmount);
                targetEntry.setCurrency(matchedBalances.get(0).getCurrency());
                targetEntry.setSummary(rule.getRuleName());
                targetEntry.setStatus(VoucherEntryStatusEnum.PENDING);
                targetEntry.setAccountingDate(accountingDate);
                targetEntry.setUnilateral(1);
                targetEntry.setBuffered(0);
                targetEntry.setExchangeRate(BigDecimal.ONE);
                targetEntry.setUnitPrice(BigDecimal.ZERO);
                targetEntry.setQuantity(0);
                targetEntry.setPricingUnit("");
                targetEntry.setTenantId(TenantContext.get());
                voucherRepository.insertEntry(targetEntry);
                entries.add(targetEntry);

                if (targetDc == DebitCreditEnum.DEBIT) {
                    totalDebit = totalDebit.add(totalAmount);
                } else {
                    totalCredit = totalCredit.add(totalAmount);
                }

                // 凭证借贷严格平衡校验
                if (totalDebit.compareTo(totalCredit) != 0) {
                    throw new AccountException(ResultCode.TRIAL_BALANCE_FAILED,
                            "结转凭证借贷不平衡: debit=" + totalDebit + ", credit=" + totalCredit);
                }

                // 6. 执行真实分户过账：按 account_no 升序锁定账户，驱动 t_account 更新与 t_account_detail 写入
                List<AccountingVoucherEntryPO> sortedEntries = entries.stream()
                        .sorted(Comparator.comparing(AccountingVoucherEntryPO::getAccountNo))
                        .toList();
                postingDomainService.executeRealTimePosting(voucher, sortedEntries);

                // 更新凭证为已过账
                voucher.setStatus(VoucherStatusEnum.POSTED);
                voucherRepository.updateById(voucher);

                // 7. 驱动日余额自然计算与 upsert（基于全部分录汇总，借贷轧差源科目自然为 0，目标科目自然增加）
                List<AccountBalancePO> updatedBalances = eodDomainService.calculateDailyBalances(accountingDate);
                if (updatedBalances != null && !updatedBalances.isEmpty()) {
                    accountBalanceRepository.batchUpsert(updatedBalances);
                }

                // 8. 记录结转结果
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

                log.info("[EOD-TRANSFER] 结转真实过账成功: ruleCode={}, transferNo={}, voucherNo={}, amount={}, targetAccount={}",
                        rule.getRuleCode(), transferNo, voucherNo, totalAmount, targetAccount.getAccountNo());

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
     * 将通配符模式转为 Java 正则表达式
     */
    private Pattern wildcardToPattern(String wildcard) {
        if (wildcard == null || wildcard.isEmpty()) {
            return Pattern.compile(".*");
        }
        String regex = wildcard.replace("*", ".*");
        return Pattern.compile("^" + regex + "$");
    }

    /**
     * 生成结转流水号
     */
    private String generateTransferNo(LocalDate date, String ruleCode) {
        return businessNoGenerator.generateTransferNo(date);
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
