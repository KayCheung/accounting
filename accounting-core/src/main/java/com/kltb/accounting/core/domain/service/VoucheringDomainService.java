package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.account.BusinessNoGenerator;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.dictionary.VoucherTypeMeta;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingRuleRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessDetailRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.BusinessRecordRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.SubjectRepository;
import com.kltb.accounting.core.infrastructure.spel.JournalSpelContext;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 凭证生成领域服务（P1-1 修复：不开启独立事务，由 Application Service 控制事务边界）
 */
@Service
public class VoucheringDomainService {

    private final AccountingRuleRepository accountingRuleRepository;
    private final AccountingVoucherRepository accountingVoucherRepository;
    private final BusinessRecordRepository businessRecordRepository;
    private final BusinessDetailRepository businessDetailRepository;
    private final BusinessNoGenerator businessNoGenerator;
    private final RuleScriptExecutor ruleScriptExecutor;
    private final DictionaryComponent dictionaryComponent;
    private final SubjectRepository subjectRepository;

    @Autowired
    public VoucheringDomainService(
            AccountingRuleRepository accountingRuleRepository,
            AccountingVoucherRepository accountingVoucherRepository,
            BusinessRecordRepository businessRecordRepository,
            BusinessDetailRepository businessDetailRepository,
            BusinessNoGenerator businessNoGenerator,
            RuleScriptExecutor ruleScriptExecutor,
            DictionaryComponent dictionaryComponent,
            @Autowired(required = false) SubjectRepository subjectRepository) {
        this.accountingRuleRepository = accountingRuleRepository;
        this.accountingVoucherRepository = accountingVoucherRepository;
        this.businessRecordRepository = businessRecordRepository;
        this.businessDetailRepository = businessDetailRepository;
        this.businessNoGenerator = businessNoGenerator;
        this.ruleScriptExecutor = ruleScriptExecutor;
        this.dictionaryComponent = dictionaryComponent;
        this.subjectRepository = subjectRepository;
    }

    public VoucheringDomainService(
            AccountingRuleRepository accountingRuleRepository,
            AccountingVoucherRepository accountingVoucherRepository,
            BusinessRecordRepository businessRecordRepository,
            BusinessDetailRepository businessDetailRepository,
            BusinessNoGenerator businessNoGenerator,
            RuleScriptExecutor ruleScriptExecutor,
            DictionaryComponent dictionaryComponent) {
        this(accountingRuleRepository, accountingVoucherRepository, businessRecordRepository,
                businessDetailRepository, businessNoGenerator, ruleScriptExecutor, dictionaryComponent, null);
    }

    /**
     * 按 traceNo 查询流水及其明细
     */
    public JournalWithDetails loadJournal(String traceNo) {
        BusinessRecordPO record = businessRecordRepository.selectByTraceNo(traceNo);
        if (record == null) {
            throw new ServiceException(ResultCode.JOURNAL_NOT_FOUND, "流水不存在: " + traceNo);
        }
        List<BusinessDetailPO> details = businessDetailRepository.selectByTraceNo(traceNo);
        return new JournalWithDetails(record, details);
    }

    /**
     * 匹配记账规则（含 status=2 校验）
     * P1-3 修复：一次查询获取辅助核算映射
     */
    public AccountingRuleWithDetails matchRule(
            String businessCode, String tradingCode, String payChannel) {

        AccountingRulePO rule = accountingRuleRepository.selectByBusinessKey(
                businessCode, tradingCode, payChannel);
        if (rule == null) {
            throw new AccountException(ResultCode.RULE_NOT_FOUND,
                    "记账规则不存在: businessCode=" + businessCode + ", tradingCode=" + tradingCode
                            + ", payChannel=" + payChannel);
        }
        if (rule.getStatus() != null && rule.getStatus().getCode() != 2) {
            throw new AccountException(ResultCode.RULE_DISABLED,
                    "记账规则已停用: ruleId=" + rule.getId());
        }

        List<AccountingRuleDetailPO> details = accountingRuleRepository.selectDetailsWithAuxiliary(rule.getId());

        // P1-3 修复：一次查询获取该规则下所有辅助核算配置，按 ruleDetailId 分组
        Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap =
                accountingRuleRepository.selectAuxiliariesByRuleId(rule.getId());

        return new AccountingRuleWithDetails(rule, details, auxMap);
    }

    /**
     * 执行 SpEL 脚本计算分录金额
     * P1-4 修复：#root 为 BusinessDetailPO / JournalSpelContext
     */
    public BigDecimal calculateEntryAmount(
            AccountingRuleDetailPO ruleDetail,
            BusinessDetailPO businessDetail) {
        return calculateEntryAmount(ruleDetail, businessDetail, null);
    }

    /**
     * 执行 SpEL 脚本计算分录金额（包含流水主单上下文）
     */
    public BigDecimal calculateEntryAmount(
            AccountingRuleDetailPO ruleDetail,
            BusinessDetailPO businessDetail,
            BusinessRecordPO journal) {

        String script = ruleDetail.getExtendScript();
        if (StringUtils.isBlank(script)) {
            // 无 SpEL 脚本时，直接使用业务明细金额
            return businessDetail.getAmount();
        }
        JournalSpelContext context = JournalSpelContext.of(businessDetail, journal);
        return ruleScriptExecutor.execute(script, context);
    }

    /**
     * 借贷平衡校验
     */
    public void validateDebitCreditBalance(List<VoucherEntryData> entries) {
        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getDebitCredit() != null && e.getDebitCredit() == 1)
                .map(VoucherEntryData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getDebitCredit() != null && e.getDebitCredit() == 2)
                .map(VoucherEntryData::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            BigDecimal diff = totalDebit.subtract(totalCredit).abs();
            throw new AccountException(ResultCode.VOUCHER_NOT_BALANCED,
                    "借贷不平衡: ΣDebit=" + totalDebit + ", ΣCredit=" + totalCredit + ", 差额=" + diff);
        }
    }

    /**
     * 写入凭证 + 分录（不开启事务，由 Application Service 控制事务边界）
     * P1-1 修复：不做 save 以外的操作，事务由上层控制
     * P1-5 修复：voucherType 从 rule.getVoucherType() 获取
     *
     * @return 生成的凭证号
     */
    public String persistVoucher(
            BusinessRecordPO journal,
            AccountingRulePO rule,
            List<VoucherEntryData> entries,
            String bookkeeperName) {

        VoucherTypeMeta vMeta = dictionaryComponent != null ? dictionaryComponent.getVoucherTypeMeta(rule.getVoucherType()) : null;
        String prefix = (vMeta != null && StrUtil.isNotBlank(vMeta.getVoucherNoPrefix())) ? vMeta.getVoucherNoPrefix() : "VOU";
        LocalDate seqDate = journal.getAccountingDate() != null ? journal.getAccountingDate() : LocalDate.now();
        String voucherNo = businessNoGenerator.generateVoucherNo(prefix, seqDate);

        // 1. 写入 t_accounting_voucher
        AccountingVoucherPO voucher = new AccountingVoucherPO();
        voucher.setVoucherNo(voucherNo);
        voucher.setTxnNo("");
        voucher.setTraceNo(journal.getTraceNo());
        voucher.setTraceSeq(journal.getTraceSeq());
        voucher.setVoucherType(rule.getVoucherType());
        voucher.setPostingType(PostingTypeEnum.AUTOMATIC);
        voucher.setBusinessCode(journal.getBusinessCode());
        voucher.setTradingCode(journal.getTradingCode());
        voucher.setPayChannel(journal.getPayChannel());
        voucher.setTradeType(journal.getTradeType());
        voucher.setTradeTime(journal.getTradeTime());
        voucher.setAmount(journal.getAmount());
        voucher.setStatus(VoucherStatusEnum.PENDING);
        voucher.setAccountingDate(journal.getAccountingDate());
        voucher.setSummary(journal.getSummary());
        voucher.setBookkeeperName(bookkeeperName);
        accountingVoucherRepository.insert(voucher);

        // 2. 写入 t_accounting_voucher_entry
        for (VoucherEntryData entry : entries) {
            entry.setVoucherNo(voucherNo);
            entry.setEntryId(businessNoGenerator.generateEntryId());

            AccountingVoucherEntryPO entryPO = new AccountingVoucherEntryPO();
            entryPO.setVoucherNo(voucherNo);
            entryPO.setEntryId(entry.getEntryId());
            entryPO.setRowNum(entry.getRowNum());
            entryPO.setSubjectCode(entry.getSubjectCode());
            entryPO.setAccountNo(entry.getAccountNo());
            // debit_credit 字段类型为 DebitCreditEnum
            entryPO.setDebitCredit(entry.getDebitCredit() != null ?
                    DebitCreditEnum.fromCode(entry.getDebitCredit()) : null);
            entryPO.setAmount(entry.getAmount());
            entryPO.setCurrency(StringUtils.defaultIfBlank(entry.getCurrency(), Constants.DEFAULT_CURRENCY));
            entryPO.setSummary(entry.getSummary());
            entryPO.setStatus(VoucherEntryStatusEnum.PENDING);
            entryPO.setAccountingDate(entry.getAccountingDate());
            entryPO.setUnilateral(Boolean.TRUE.equals(entry.getIsUnilateral()) ? 1 : 0);
            entryPO.setBuffered(Boolean.TRUE.equals(entry.getIsBuffered()) ? 1 : 0);
            entryPO.setExchangeRate(BigDecimal.ONE);
            entryPO.setUnitPrice(BigDecimal.ZERO);
            entryPO.setQuantity(0);
            entryPO.setPricingUnit("");
            entryPO.setTenantId(TenantContext.get());

            // 核心财务律法：设置分录增减方向 changeDirection（1-增, 2-减）
            if (entry.getChangeDirection() != null) {
                entryPO.setChangeDirection(ChangeDirectionEnum.fromCode(entry.getChangeDirection()));
            } else {
                // 兜底机制：优先从科目表推导；若无科目信息，默认 INCREASE 避免数据库非空约束报错
                ChangeDirectionEnum changeDir = resolveChangeDirection(entry.getSubjectCode(), entryPO.getDebitCredit());
                entryPO.setChangeDirection(changeDir != null ? changeDir : ChangeDirectionEnum.INCREASE);
            }

            accountingVoucherRepository.insertEntry(entryPO);
        }

        return voucherNo;
    }

    /**
     * 根据科目余额方向推导增减方向（借贷一致为增，借贷相反为减）
     */
    private ChangeDirectionEnum resolveChangeDirection(String subjectCode, DebitCreditEnum debitCredit) {
        if (subjectRepository == null || StrUtil.isBlank(subjectCode) || debitCredit == null) {
            return null;
        }
        try {
            AccountSubjectPO subject = subjectRepository.selectByCode(subjectCode);
            if (subject != null && subject.getDebitCredit() != null) {
                return (debitCredit == subject.getDebitCredit())
                        ? ChangeDirectionEnum.INCREASE
                        : ChangeDirectionEnum.DECREASE;
            }
        } catch (Exception ignored) {
            // 忽略非核心查询异常，返回 null 走默认兜底
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    public static class JournalWithDetails {
        private final BusinessRecordPO record;
        private final List<BusinessDetailPO> details;
    }

    @Data
    @AllArgsConstructor
    public static class AccountingRuleWithDetails {
        private final AccountingRulePO rule;
        private final List<AccountingRuleDetailPO> details;
        private final Map<Long, List<AccountingRuleAuxiliaryPO>> auxMap;
    }
}
