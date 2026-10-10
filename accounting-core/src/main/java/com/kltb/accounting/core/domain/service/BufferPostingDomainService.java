package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import com.kltb.accounting.core.infrastructure.spel.JournalSpelContext;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 缓冲规则匹配与明细持久化领域服务
 * <p>
 * 辅助核算职责已解耦至 {@link AuxiliaryDomainService}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BufferPostingDomainService {

    private final BufferPostingRuleMapper bufferPostingRuleMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;
    private final AuxiliaryDomainService auxiliaryDomainService;

    /**
     * @deprecated 请使用 {@link AuxiliaryDomainService#calculateAuxiliaryAllocation(VoucherEntryData, List)}
     */
    @Deprecated
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
            VoucherEntryData entryData,
            List<AccountingRuleAuxiliaryPO> auxiliaryConfigs) {
        return auxiliaryDomainService.calculateAuxiliaryAllocation(entryData, auxiliaryConfigs);
    }

    /**
     * @deprecated 请使用 {@link AuxiliaryDomainService#calculateAuxiliaryAllocation(VoucherEntryData, List, BusinessDetailPO, BusinessRecordPO)}
     */
    @Deprecated
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
            VoucherEntryData entryData,
            List<AccountingRuleAuxiliaryPO> auxiliaryConfigs,
            BusinessDetailPO businessDetail,
            BusinessRecordPO journal) {
        return auxiliaryDomainService.calculateAuxiliaryAllocation(entryData, auxiliaryConfigs, businessDetail, journal);
    }

    /**
     * @deprecated 请使用 {@link AuxiliaryDomainService#persistAuxiliaryItems(List)}
     */
    @Deprecated
    public void persistAuxiliaryItems(List<AuxiliaryItemData> items) {
        auxiliaryDomainService.persistAuxiliaryItems(items);
    }


    /**
     * 缓冲规则匹配
     * P1-6 修复：XML 中已处理 NULL/空串判断
     */
    public BufferPostingRulePO matchBufferRule(
            VoucherEntryData entryData,
            String businessCode, String tradingCode, String payChannel) {

        Integer debitCreditCode = entryData.getDebitCredit();
        LocalDate accountingDate = entryData.getAccountingDate();
        String subjectCode = entryData.getSubjectCode();
        String accountNo = entryData.getAccountNo();

        List<BufferPostingRulePO> rules = bufferPostingRuleMapper.selectMatchingRules(
                businessCode, tradingCode, payChannel,
                subjectCode, accountNo,
                debitCreditCode, accountingDate);

        return rules.isEmpty() ? null : rules.get(0);
    }

    /**
     * 逐条写入缓冲记账明细
     */
    public void persistBufferPostingDetails(List<BufferPostingDetailData> details) {
        if (details == null || details.isEmpty()) {
            return;
        }

        for (BufferPostingDetailData d : details) {
            BufferPostingDetailPO po = new BufferPostingDetailPO();
            po.setRuleId(d.getRuleId());
            po.setBufferMode(d.getBufferMode() != null ? BufferModeEnum.fromCode(d.getBufferMode()) : null);
            po.setVoucherNo(d.getVoucherNo());
            po.setEntryId(d.getEntryId());
            po.setTxnNo(d.getTxnNo());
            po.setTraceNo(d.getTraceNo());
            po.setTraceSeq(d.getTraceSeq());
            po.setBusinessCode(d.getBusinessCode());
            po.setTradingCode(d.getTradingCode());
            po.setPayChannel(d.getPayChannel());
            po.setTradeType(d.getTradeType() != null ? TradeTypeEnum.fromCode(d.getTradeType()) : null);
            po.setTradeTime(d.getTradeTime());
            po.setAccountNo(d.getAccountNo());
            po.setDebitCredit(d.getDebitCredit() != null ? DebitCreditEnum.fromCode(d.getDebitCredit()) : null);
            po.setCurrency(d.getCurrency());
            po.setAmount(d.getAmount());
            po.setAccountingDate(d.getAccountingDate());
            po.setSummary(d.getSummary());
            po.setStatus(BufferStatusEnum.PENDING);
            po.setSharding(d.getSharding());
            bufferPostingDetailMapper.insert(po);
        }
    }

    /**
     * 计算分片值（同一账户必须在同一分片）
     */
    public Long calculateSharding(String accountNo) {
        if (accountNo == null || accountNo.isEmpty()) {
            return 0L;
        }
        return (long) Math.abs(accountNo.hashCode());
    }
}
