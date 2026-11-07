package com.kltb.accounting.core.domain.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.ChangeDirectionEnum;
import com.kltb.accounting.core.infrastructure.dictionary.DictionaryComponent;
import com.kltb.accounting.core.infrastructure.persistence.entity.DictionaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.BusinessRecordPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.AccountingVoucherAuxiliaryMapper;
import com.kltb.accounting.core.infrastructure.spel.JournalSpelContext;
import com.kltb.accounting.core.infrastructure.spel.RuleScriptExecutor;
import com.kltb.accounting.core.shared.context.TenantContext;
import com.kltb.accounting.core.shared.exception.AccountException;
import com.kltb.accounting.core.shared.util.FinancialAmountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    private final DictionaryComponent dictionaryComponent;

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

        BigDecimal entryAmount = (entryData != null && entryData.getAmount() != null)
                ? entryData.getAmount()
                : BigDecimal.ZERO;

        // 构建 SpEL 统一上下文
        JournalSpelContext spelContext = JournalSpelContext.of(
                entryAmount,
                businessDetail,
                journal
        );

        List<AuxiliaryItemData> result = new ArrayList<>();

        // 按辅助核算维度（auxType）分组独立计算与校验
        Map<String, List<AccountingRuleAuxiliaryPO>> auxTypeGroups = auxiliaryConfigs.stream()
                .collect(Collectors.groupingBy(
                        c -> StrUtil.isNotBlank(c.getAuxType()) ? c.getAuxType() : "",
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (Map.Entry<String, List<AccountingRuleAuxiliaryPO>> groupEntry : auxTypeGroups.entrySet()) {
            String auxType = groupEntry.getKey();
            List<AccountingRuleAuxiliaryPO> configs = groupEntry.getValue();
            List<AuxiliaryItemData> groupResult = new ArrayList<>();

            // 1. 不分摊项（NONE 或未配置分摊方式）：分录金额全额记录到该辅助核算项
            List<AccountingRuleAuxiliaryPO> noneConfigs = configs.stream()
                    .filter(c -> c.getAllocationMethod() == null || c.getAllocationMethod() == AllocationMethodEnum.NONE)
                    .collect(Collectors.toList());

            for (AccountingRuleAuxiliaryPO config : noneConfigs) {
                groupResult.add(buildAuxiliaryItem(entryData, config, entryAmount, spelContext));
            }

            // 2. 固定金额分摊
            List<AccountingRuleAuxiliaryPO> fixed = configs.stream()
                    .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.FIXED_AMOUNT)
                    .collect(Collectors.toList());

            BigDecimal fixedTotal = BigDecimal.ZERO;
            for (AccountingRuleAuxiliaryPO config : fixed) {
                BigDecimal auxAmount = config.getAllocationValue();
                if (auxAmount == null || auxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (auxAmount.compareTo(entryAmount) > 0) {
                    throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                            "辅助核算固定金额超过分录金额: auxCode=" + config.getAuxCode()
                                    + ", auxAmount=" + auxAmount + ", entryAmount=" + entryAmount);
                }
                fixedTotal = fixedTotal.add(auxAmount);
                groupResult.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
            }

            // 3. SpEL 脚本动态金额分摊
            List<AccountingRuleAuxiliaryPO> spelConfigs = configs.stream()
                    .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.SPEL_SCRIPT)
                    .collect(Collectors.toList());

            BigDecimal spelTotal = BigDecimal.ZERO;
            for (AccountingRuleAuxiliaryPO config : spelConfigs) {
                BigDecimal auxAmount = BigDecimal.ZERO;
                if (StrUtil.isNotBlank(config.getExtendScript()) && ruleScriptExecutor != null) {
                    auxAmount = ruleScriptExecutor.execute(config.getExtendScript(), spelContext);
                }
                if (auxAmount == null || auxAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (auxAmount.compareTo(entryAmount) > 0) {
                    throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                            "辅助核算SpEL计算金额超过分录金额: auxCode=" + config.getAuxCode()
                                    + ", auxAmount=" + auxAmount + ", entryAmount=" + entryAmount);
                }
                spelTotal = spelTotal.add(auxAmount);
                groupResult.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
            }

            // 4. 按比例分摊（在扣除固定金额和 SpEL 金额后的剩余金额上按比例分摊，前 N-1 按比例，最后一条补差）
            List<AccountingRuleAuxiliaryPO> proportional = configs.stream()
                    .filter(c -> c.getAllocationMethod() != null && c.getAllocationMethod() == AllocationMethodEnum.PERCENTAGE)
                    .collect(Collectors.toList());

            if (!proportional.isEmpty()) {
                BigDecimal remaining = entryAmount.subtract(fixedTotal).subtract(spelTotal);
                if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                    throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                            "辅助核算固定与SpEL分摊合计超过分录金额: entryAmount=" + entryAmount
                                    + ", fixedTotal=" + fixedTotal + ", spelTotal=" + spelTotal);
                }
                BigDecimal allocated = BigDecimal.ZERO;

                for (int i = 0; i < proportional.size(); i++) {
                    AccountingRuleAuxiliaryPO config = proportional.get(i);
                    BigDecimal auxAmount;

                    if (i < proportional.size() - 1) {
                        // allocation_value 为百分比，如 30 表示 30%
                        BigDecimal ratio = config.getAllocationValue().divide(
                                FinancialAmountUtil.PERCENT_BASE, 6, RoundingMode.HALF_UP);
                        auxAmount = remaining.multiply(ratio).setScale(6, RoundingMode.HALF_UP);
                        allocated = allocated.add(auxAmount);
                    } else {
                        // 最后一条补差
                        auxAmount = remaining.subtract(allocated);
                    }

                    groupResult.add(buildAuxiliaryItem(entryData, config, auxAmount, spelContext));
                }
            }

            // 该维度核算项合计校验：有辅助核算项时，该维度合计必须等于分录金额
            if (!groupResult.isEmpty()) {
                BigDecimal groupTotal = groupResult.stream()
                        .map(AuxiliaryItemData::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (groupTotal.compareTo(entryAmount) != 0) {
                    throw new AccountException(ResultCode.AUXILIARY_AMOUNT_MISMATCH,
                            "辅助核算分摊金额合计不等于分录金额: auxType=" + auxType
                                    + ", auxTotal=" + groupTotal
                                    + ", entryAmount=" + entryAmount);
                }
            }

            result.addAll(groupResult);
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

        String resolvedAuxName = resolvedAuxCode;
        if (dictionaryComponent != null && StrUtil.isNotBlank(resolvedAuxCode) && StrUtil.isNotBlank(config.getAuxType())) {
            try {
                DictionaryPO dict = dictionaryComponent.findByTypeAndCodeOrName(config.getAuxType(), resolvedAuxCode);
                if (dict == null) {
                    dict = dictionaryComponent.findByTypeAndCodeOrName(config.getAuxType().toLowerCase(), resolvedAuxCode);
                }
                if (dict == null) {
                    dict = dictionaryComponent.findByTypeAndCodeOrName(config.getAuxType().toUpperCase(), resolvedAuxCode);
                }
                if (dict != null && StrUtil.isNotBlank(dict.getDictName())) {
                    resolvedAuxName = dict.getDictName();
                }
            } catch (Exception e) {
                log.debug("[Auxiliary] 辅助核算字典名称解析异常: auxType={}, auxCode={}", config.getAuxType(), resolvedAuxCode);
            }
        }

        Integer changeDir = (entryData != null && entryData.getChangeDirection() != null)
                ? entryData.getChangeDirection()
                : (entryData != null ? entryData.getDebitCredit() : 1);

        return new AuxiliaryItemData(
                entryData != null ? entryData.getEntryId() : null,
                entryData != null ? entryData.getVoucherNo() : null,
                entryData != null ? entryData.getSubjectCode() : null,
                config.getAuxType(),
                resolvedAuxCode,
                resolvedAuxName,
                changeDir,
                amount,
                entryData != null ? entryData.getAccountingDate() : null
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
            if (StrUtil.isBlank(item.getVoucherNo()) || StrUtil.isBlank(item.getEntryId())) {
                throw new AccountException(ResultCode.SYSTEM_ERROR,
                        "辅助核算项缺失关联凭证号或分录号: auxCode=" + item.getAuxCode());
            }

            AccountingVoucherAuxiliaryPO po = new AccountingVoucherAuxiliaryPO();
            po.setVoucherNo(item.getVoucherNo());
            po.setEntryId(item.getEntryId());
            po.setSubjectCode(item.getSubjectCode());
            po.setAuxType(item.getAuxType());
            po.setAuxCode(item.getAuxCode());
            po.setAuxName(StrUtil.isNotBlank(item.getAuxName()) ? item.getAuxName() : item.getAuxCode());
            po.setChangeDirection(item.getChangeDirection() != null ?
                    ChangeDirectionEnum.fromCode(item.getChangeDirection()) : ChangeDirectionEnum.INCREASE);
            po.setAmount(item.getAmount());
            po.setAccountingDate(item.getAccountingDate());
            po.setTenantId(TenantContext.get());
            voucherAuxiliaryMapper.insert(po);
        }
    }
}
