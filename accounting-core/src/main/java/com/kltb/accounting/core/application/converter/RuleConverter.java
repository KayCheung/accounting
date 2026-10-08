package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.request.RuleAuxiliaryRequest;
import com.kltb.accounting.api.request.RuleCreateRequest;
import com.kltb.accounting.api.request.RuleEntryRequest;
import com.kltb.accounting.api.request.RuleUpdateRequest;
import com.kltb.accounting.core.application.dto.RuleAuxiliaryResponse;
import com.kltb.accounting.core.application.dto.RuleEntryResponse;
import com.kltb.accounting.core.application.dto.RuleResponse;
import com.kltb.accounting.core.domain.enums.AccountScopeEnum;
import com.kltb.accounting.core.domain.enums.AllocationMethodEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.OpenAccountFlagEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleAuxiliaryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRuleDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingRulePO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 记账规则转换器
 *
 * 是否记账：否
 */
public class RuleConverter {

    /**
     * 创建请求 → PO
     */
    public static AccountingRulePO toPO(RuleCreateRequest request) {
        AccountingRulePO po = new AccountingRulePO();
        po.setRuleName(request.getRuleName());
        po.setVoucherType(request.getVoucherType());
        po.setBusinessCode(request.getBusinessCode());
        po.setTradingCode(request.getTradingCode());
        po.setPayChannel(request.getPayChannel());
        po.setOpenAccount(request.getIsOpenAccount() != null && request.getIsOpenAccount()
                ? OpenAccountFlagEnum.ENABLED : OpenAccountFlagEnum.DISABLED);
        po.setFreezeDuration(request.getFreezeDuration() != null ? request.getFreezeDuration() : 0);
        po.setPreRuleId(request.getPreRuleId() != null ? request.getPreRuleId() : 0L);
        po.setStatus(Optional.ofNullable(RuleStatusEnum.fromCode(request.getStatus()))
                .orElse(RuleStatusEnum.PENDING));
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * 更新请求 → PO 字段
     */
    public static void updatePO(RuleUpdateRequest request, AccountingRulePO po) {
        if (request.getRuleName() != null) {
            po.setRuleName(request.getRuleName());
        }
        if (request.getVoucherType() != null) {
            po.setVoucherType(request.getVoucherType());
        }
        if (request.getIsOpenAccount() != null) {
            po.setOpenAccount(request.getIsOpenAccount() ? OpenAccountFlagEnum.ENABLED : OpenAccountFlagEnum.DISABLED);
        }
        if (request.getFreezeDuration() != null) {
            po.setFreezeDuration(request.getFreezeDuration());
        }
        if (request.getPreRuleId() != null) {
            po.setPreRuleId(request.getPreRuleId());
        }
        if (request.getStatus() != null) {
            Optional.ofNullable(RuleStatusEnum.fromCode(request.getStatus()))
                    .ifPresent(po::setStatus);
        }
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
    }

    /**
     * 明细请求 → PO
     */
    public static AccountingRuleDetailPO toDetailPO(RuleEntryRequest request, Long ruleId) {
        AccountingRuleDetailPO po = new AccountingRuleDetailPO();
        po.setRuleId(ruleId);
        po.setRowNum(request.getRowNum());
        po.setFundsType(request.getFundsType());
        po.setSubjectCode(request.getSubjectCode());
        po.setAccountScope(Optional.ofNullable(AccountScopeEnum.fromCode(request.getAccountScope()))
                .orElse(AccountScopeEnum.INTERNAL));
        po.setDebitCredit(Optional.ofNullable(DebitCreditEnum.fromCode(request.getDebitCredit()))
                .orElse(DebitCreditEnum.DEBIT));
        po.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        po.setUnilateral(request.getIsUnilateral() != null && request.getIsUnilateral());
        po.setExtendScript(request.getExtendScript() != null ? request.getExtendScript() : "");
        po.setSummary(request.getSummary() != null ? request.getSummary() : "");
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * 辅助请求 → PO
     */
    public static AccountingRuleAuxiliaryPO toAuxiliaryPO(RuleAuxiliaryRequest request,
                                                           Long ruleId, Long ruleDetailId) {
        AccountingRuleAuxiliaryPO po = new AccountingRuleAuxiliaryPO();
        po.setRuleId(ruleId);
        po.setRuleDetailId(ruleDetailId);
        po.setAuxType(request.getAuxType());
        po.setAuxCode(request.getAuxCode());
        po.setAllocationMethod(Optional.ofNullable(AllocationMethodEnum.fromCode(request.getAllocationMethod()))
                .orElse(AllocationMethodEnum.NONE));
        po.setAllocationValue(request.getAllocationValue() != null ? request.getAllocationValue() : BigDecimal.ZERO);
        po.setExtendScript(request.getExtendScript() != null ? request.getExtendScript() : "");
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * PO → 响应 DTO
     */
    public static RuleResponse toResponse(AccountingRulePO po,
                                           List<RuleEntryResponse> entries) {
        RuleResponse resp = new RuleResponse();
        resp.setId(po.getId());
        resp.setRuleName(po.getRuleName());
        resp.setVoucherType(po.getVoucherType());
        resp.setBusinessCode(po.getBusinessCode());
        resp.setTradingCode(po.getTradingCode());
        resp.setPayChannel(po.getPayChannel());
        resp.setIsOpenAccount(po.getOpenAccount() != null && po.getOpenAccount() == OpenAccountFlagEnum.ENABLED);
        resp.setFreezeDuration(po.getFreezeDuration());
        resp.setPreRuleId(po.getPreRuleId());
        resp.setStatus(Optional.ofNullable(po.getStatus()).map(RuleStatusEnum::getCode).orElse(null));
        resp.setEntries(entries);
        return resp;
    }

    /**
     * 明细 PO → 响应 DTO
     */
    public static RuleEntryResponse toEntryResponse(AccountingRuleDetailPO po,
                                                      List<RuleAuxiliaryResponse> auxiliaries) {
        RuleEntryResponse resp = new RuleEntryResponse();
        resp.setId(po.getId());
        resp.setRowNum(po.getRowNum());
        resp.setFundsType(po.getFundsType());
        resp.setSubjectCode(po.getSubjectCode());
        resp.setAccountScope(Optional.ofNullable(po.getAccountScope()).map(AccountScopeEnum::getCode).orElse(null));
        resp.setDebitCredit(Optional.ofNullable(po.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        resp.setCurrency(po.getCurrency());
        resp.setIsUnilateral(po.getUnilateral());
        resp.setExtendScript(po.getExtendScript());
        resp.setSummary(po.getSummary());
        resp.setAuxiliaries(auxiliaries);
        return resp;
    }

    /**
     * 辅助 PO → 响应 DTO
     */
    public static RuleAuxiliaryResponse toAuxiliaryResponse(AccountingRuleAuxiliaryPO po) {
        RuleAuxiliaryResponse resp = new RuleAuxiliaryResponse();
        resp.setId(po.getId());
        resp.setAuxType(po.getAuxType());
        resp.setAuxCode(po.getAuxCode());
        resp.setAllocationMethod(Optional.ofNullable(po.getAllocationMethod()).map(AllocationMethodEnum::getCode).orElse(null));
        resp.setAllocationValue(po.getAllocationValue());
        resp.setExtendScript(po.getExtendScript());
        return resp;
    }

    /**
     * PO 列表 → 响应 DTO 列表（简单转换，不含明细）
     */
    public static List<RuleResponse> toResponseList(List<AccountingRulePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<RuleResponse> result = new ArrayList<>(poList.size());
        for (AccountingRulePO po : poList) {
            result.add(toResponse(po, List.of()));
        }
        return result;
    }
}
