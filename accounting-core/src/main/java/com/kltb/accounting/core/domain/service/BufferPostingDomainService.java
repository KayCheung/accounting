package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.TradeTypeEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingDetailMapper;
import com.kltb.accounting.core.infrastructure.persistence.mapper.BufferPostingRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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
