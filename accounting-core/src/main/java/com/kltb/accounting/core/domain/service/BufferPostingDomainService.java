package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.infrastructure.persistence.entity.*;
import com.kltb.accounting.core.infrastructure.persistence.mapper.*;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 缓冲规则匹配 + 辅助核算分摊领域服务
 */
@Service
@RequiredArgsConstructor
public class BufferPostingDomainService {

    private final BufferPostingRuleMapper bufferPostingRuleMapper;
    private final BufferPostingDetailMapper bufferPostingDetailMapper;
    private final AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;

    /**
     * 辅助核算分摊引擎
     *
     * @param entryData        分录数据
     * @param auxiliaryConfigs 该分录行对应的辅助核算配置列表
     * @return 分摊后的辅助核算项列表
     */
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
            VoucherEntryData entryData,
            List<AccountingRuleAuxiliaryPO> auxiliaryConfigs) {

        List<AuxiliaryItemData> result = new ArrayList<>();

        // 按分摊方式分组
        List<AccountingRuleAuxiliaryPO> proportional = auxiliaryConfigs.stream()
                .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.PERCENTAGE)
                .collect(Collectors.toList());

        List<AccountingRuleAuxiliaryPO> fixed = auxiliaryConfigs.stream()
                .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.FIXED_AMOUNT)
                .collect(Collectors.toList());

        // 固定金额分摊
        BigDecimal fixedTotal = BigDecimal.ZERO;
        for (AccountingRuleAuxiliaryPO config : fixed) {
            BigDecimal auxAmount = config.getAllocationValue();
            if (auxAmount == null || auxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (auxAmount.compareTo(entryData.getAmount()) > 0) {
                throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                        "辅助核算固定金额超过分录金额: auxCode=" + config.getAuxCode()
                                + ", auxAmount=" + auxAmount + ", entryAmount=" + entryData.getAmount());
            }
            fixedTotal = fixedTotal.add(auxAmount);
            result.add(buildAuxiliaryItem(entryData, config, auxAmount));
        }

        // 按比例分摊（前 N-1 按比例，最后一条补差）
        if (!proportional.isEmpty()) {
            BigDecimal remaining = entryData.getAmount().subtract(fixedTotal);
            BigDecimal allocated = BigDecimal.ZERO;

            for (int i = 0; i < proportional.size(); i++) {
                AccountingRuleAuxiliaryPO config = proportional.get(i);
                BigDecimal auxAmount;

                if (i < proportional.size() - 1) {
                    // allocation_value 为百分比，如 30 表示 30%
                    BigDecimal ratio = config.getAllocationValue().divide(
                            new BigDecimal("100"), 6, RoundingMode.HALF_UP);
                    auxAmount = remaining.multiply(ratio).setScale(6, RoundingMode.HALF_UP);
                    allocated = allocated.add(auxAmount);
                } else {
                    // 最后一条补差
                    auxAmount = remaining.subtract(allocated);
                }

                result.add(buildAuxiliaryItem(entryData, config, auxAmount));
            }
        }

        // 不分摊（AllocationMethodEnum.NONE）不生成记录，直接跳过

        // 最终校验：有分摊项时，合计必须等于分录金额
        if (!result.isEmpty()) {
            BigDecimal auxTotal = result.stream()
                    .map(AuxiliaryItemData::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (auxTotal.compareTo(entryData.getAmount()) != 0) {
                throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                        "辅助核算分摊金额合计不等于分录金额: auxTotal=" + auxTotal
                                + ", entryAmount=" + entryData.getAmount());
            }
        }

        return result;
    }

    /**
     * 构建辅助核算项
     */
    private AuxiliaryItemData buildAuxiliaryItem(
            VoucherEntryData entryData,
            AccountingRuleAuxiliaryPO config,
            BigDecimal amount) {

        return new AuxiliaryItemData(
                entryData.getEntryId(),
                entryData.getVoucherNo(),
                entryData.getSubjectCode(),
                config.getAuxType(),
                config.getAuxCode(),
                config.getAuxCode(),
                entryData.getDebitCredit(),
                amount,
                entryData.getAccountingDate()
        );
    }

    /**
     * 逐条写入辅助核算项
     */
    public void persistAuxiliaryItems(List<AuxiliaryItemData> items) {
        if (items == null || items.isEmpty()) {
            return;
        }

        for (AuxiliaryItemData item : items) {
            AccountingVoucherAuxiliaryPO po = new AccountingVoucherAuxiliaryPO();
            po.setVoucherNo(item.getVoucherNo());
            po.setEntryId(item.getEntryId());
            po.setSubjectCode(item.getSubjectCode());
            po.setAuxType(item.getAuxType());
            po.setAuxCode(item.getAuxCode());
            po.setAuxName(item.getAuxName());
            po.setChangeDirection(item.getChangeDirection() != null ?
                    ChangeDirectionEnum.fromCode(item.getChangeDirection()) : null);
            po.setAmount(item.getAmount());
            po.setAccountingDate(item.getAccountingDate());
            voucherAuxiliaryMapper.insert(po);
        }
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
