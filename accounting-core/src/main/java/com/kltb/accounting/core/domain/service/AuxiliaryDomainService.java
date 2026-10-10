package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.spel.JournalSpelContext;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 辅助核算领域服务（专职负责凭证辅助核算项分摊计算、SpEL 动态脚本/编码解析与持久化）
 * <p>
 * 遵循单一职责原则（SRP）与领域驱动设计（DDD），从缓冲过账中彻底解耦独立。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuxiliaryDomainService {

    private final AccountingVoucherAuxiliaryMapper voucherAuxiliaryMapper;
    private final RuleScriptExecutor ruleScriptExecutor;

    /**
     * 辅助核算分摊引擎（向下兼容）
     *
     * @param entryData        分录数据
     * @param auxiliaryConfigs 该分录行对应的辅助核算配置列表
     * @return 分摊后的辅助核算项列表
     */
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
            VoucherEntryData entryData,
            List<AccountingRuleAuxiliaryPO> auxiliaryConfigs) {
        return calculateAuxiliaryAllocation(entryData, auxiliaryConfigs, null, null);
    }

    /**
     * 辅助核算分摊引擎（支持流水明细、主单上下文、SpEL 动态脚本与动态核算编码解析）
     *
     * @param entryData        分录数据
     * @param auxiliaryConfigs 该分录行对应的辅助核算配置列表
     * @param businessDetail   关联的业务流水明细（可为空）
     * @param journal          关联的业务流水主单（可为空）
     * @return 分摊后的辅助核算项列表
     */
    public List<AuxiliaryItemData> calculateAuxiliaryAllocation(
            VoucherEntryData entryData,
            List<AccountingRuleAuxiliaryPO> auxiliaryConfigs,
            BusinessDetailPO businessDetail,
            BusinessRecordPO journal) {

        if (auxiliaryConfigs == null || auxiliaryConfigs.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建 SpEL 统一上下文
        JournalSpelContext spelContext = JournalSpelContext.of(
                entryData != null ? entryData.getAmount() : BigDecimal.ZERO,
                businessDetail,
                journal
        );

        List<AuxiliaryItemData> result = new ArrayList<>();

        // 按分摊方式分组
        List<AccountingRuleAuxiliaryPO> fixed = auxiliaryConfigs.stream()
                .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.FIXED_AMOUNT)
                .collect(Collectors.toList());

        List<AccountingRuleAuxiliaryPO> spelConfigs = auxiliaryConfigs.stream()
                .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.SPEL_SCRIPT)
                .collect(Collectors.toList());

        List<AccountingRuleAuxiliaryPO> proportional = auxiliaryConfigs.stream()
                .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.PERCENTAGE)
                .collect(Collectors.toList());

        // 1. 固定金额分摊
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
            result.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
        }

        // 2. SpEL 脚本动态金额分摊
        BigDecimal spelTotal = BigDecimal.ZERO;
        for (AccountingRuleAuxiliaryPO config : spelConfigs) {
            BigDecimal auxAmount = BigDecimal.ZERO;
            if (StrUtil.isNotBlank(config.getExtendScript()) && ruleScriptExecutor != null) {
                auxAmount = ruleScriptExecutor.execute(config.getExtendScript(), spelContext);
            }
            if (auxAmount == null || auxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (auxAmount.compareTo(entryData.getAmount()) > 0) {
                throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                        "辅助核算SpEL计算金额超过分录金额: auxCode=" + config.getAuxCode()
                                + ", auxAmount=" + auxAmount + ", entryAmount=" + entryData.getAmount());
            }
            spelTotal = spelTotal.add(auxAmount);
            result.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
        }

        // 3. 按比例分摊（在扣除固定金额和 SpEL 金额后的剩余金额上按比例分摊，前 N-1 按比例，最后一条补差）
        if (!proportional.isEmpty()) {
            BigDecimal remaining = entryData.getAmount().subtract(fixedTotal).subtract(spelTotal);
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                        "辅助核算固定与SpEL分摊合计超过分录金额: entryAmount=" + entryData.getAmount()
                                + ", fixedTotal=" + fixedTotal + ", spelTotal=" + spelTotal);
            }
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

                result.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
            }
        }

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
     * 构建辅助核算项（支持动态 #{...} 表达式解析 auxCode）
     */
    private AuxiliaryItemData buildAuxiliaryItem(
            VoucherEntryData entryData,
            AccountingRuleAuxiliaryPO config,
            BigDecimal amount,
            JournalSpelContext spelContext) {

        String resolvedAuxCode = config.getAuxCode();
        if (resolvedAuxCode != null && resolvedAuxCode.contains("#{") && ruleScriptExecutor != null && spelContext != null) {
            try {
                String dynamicCode = ruleScriptExecutor.executeTemplate(resolvedAuxCode, spelContext);
                if (StrUtil.isNotBlank(dynamicCode)) {
                    resolvedAuxCode = dynamicCode;
                }
            } catch (Exception e) {
                log.warn("[Auxiliary] 辅助核算编码 SpEL 模板解析异常: auxCode={}, error={}", resolvedAuxCode, e.getMessage());
            }
        }

        return new AuxiliaryItemData(
                entryData.getEntryId(),
                entryData.getVoucherNo(),
                entryData.getSubjectCode(),
                config.getAuxType(),
                resolvedAuxCode,
                resolvedAuxCode,
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
}
