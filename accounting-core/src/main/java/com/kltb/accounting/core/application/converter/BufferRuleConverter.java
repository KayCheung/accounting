package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.request.BufferRuleCreateRequest;
import com.kltb.accounting.api.request.BufferRuleUpdateRequest;
import com.kltb.accounting.core.application.dto.BufferRuleResponse;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.RuleStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingRulePO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 缓冲入账规则转换器
 *
 * 是否记账：否
 */
public class BufferRuleConverter {

    /**
     * 创建请求 → PO
     */
    public static BufferPostingRulePO toPO(BufferRuleCreateRequest request) {
        BufferPostingRulePO po = new BufferPostingRulePO();
        po.setRuleName(request.getRuleName());
        po.setBufferMode(Optional.ofNullable(BufferModeEnum.fromCode(request.getBufferMode()))
                .orElse(BufferModeEnum.ASYNC_SINGLE));
        po.setBusinessCode(request.getBusinessCode());
        po.setTradingCode(request.getTradingCode());
        po.setPayChannel(request.getPayChannel());
        po.setSubjectCode(request.getSubjectCode() != null ? request.getSubjectCode() : "");
        po.setAccountNo(request.getAccountNo() != null ? request.getAccountNo() : "");
        po.setDebitCredit(Optional.ofNullable(DebitCreditEnum.fromCode(request.getDebitCredit()))
                .orElse(DebitCreditEnum.DEBIT));
        po.setEffectiveTime(request.getEffectiveTime());
        po.setExpirationTime(request.getExpirationTime());
        po.setStatus(RuleStatusEnum.PENDING);
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * 更新请求 → PO 字段
     */
    public static void updatePO(BufferRuleUpdateRequest request, BufferPostingRulePO po) {
        if (request.getRuleName() != null) {
            po.setRuleName(request.getRuleName());
        }
        if (request.getBufferMode() != null) {
            Optional.ofNullable(BufferModeEnum.fromCode(request.getBufferMode()))
                    .ifPresent(po::setBufferMode);
        }
        if (request.getSubjectCode() != null) {
            po.setSubjectCode(request.getSubjectCode());
        }
        if (request.getAccountNo() != null) {
            po.setAccountNo(request.getAccountNo());
        }
        if (request.getDebitCredit() != null) {
            Optional.ofNullable(DebitCreditEnum.fromCode(request.getDebitCredit()))
                    .ifPresent(po::setDebitCredit);
        }
        if (request.getEffectiveTime() != null) {
            po.setEffectiveTime(request.getEffectiveTime());
        }
        if (request.getExpirationTime() != null) {
            po.setExpirationTime(request.getExpirationTime());
        }
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
    }

    /**
     * PO → 响应 DTO
     */
    public static BufferRuleResponse toResponse(BufferPostingRulePO po) {
        BufferRuleResponse resp = new BufferRuleResponse();
        resp.setId(po.getId());
        resp.setRuleName(po.getRuleName());
        resp.setBufferMode(Optional.ofNullable(po.getBufferMode()).map(BufferModeEnum::getCode).orElse(null));
        resp.setBusinessCode(po.getBusinessCode());
        resp.setTradingCode(po.getTradingCode());
        resp.setPayChannel(po.getPayChannel());
        resp.setSubjectCode(po.getSubjectCode());
        resp.setAccountNo(po.getAccountNo());
        resp.setDebitCredit(Optional.ofNullable(po.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        resp.setEffectiveTime(po.getEffectiveTime());
        resp.setExpirationTime(po.getExpirationTime());
        resp.setStatus(Optional.ofNullable(po.getStatus()).map(RuleStatusEnum::getCode).orElse(RuleStatusEnum.PENDING.getCode()));
        return resp;
    }

    /**
     * PO 列表 → 响应 DTO 列表
     */
    public static List<BufferRuleResponse> toResponseList(List<BufferPostingRulePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<BufferRuleResponse> result = new ArrayList<>(poList.size());
        for (BufferPostingRulePO po : poList) {
            result.add(toResponse(po));
        }
        return result;
    }
}
