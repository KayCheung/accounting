package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.constant.Constants;
import com.kltb.accounting.api.request.TemplateCreateRequest;
import com.kltb.accounting.api.request.TemplateUpdateRequest;
import com.kltb.accounting.core.application.dto.TemplateResponse;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 开户模板转换器
 *
 * 是否记账：否
 */
public class TemplateConverter {

    /**
     * 创建请求 → PO
     */
    public static AccountTemplatePO toPO(TemplateCreateRequest request) {
        AccountTemplatePO po = new AccountTemplatePO();
        po.setTemplateName(request.getTemplateName());
        po.setBusinessCode(request.getBusinessCode());
        po.setCustomerType(Optional.ofNullable(CustomerTypeEnum.fromCode(request.getCustomerType()))
                .orElse(CustomerTypeEnum.OTHER));
        po.setAutoOpen(request.getAutoOpen() != null && request.getAutoOpen());
        po.setSubjectCode(request.getSubjectCode());
        po.setAccountType(request.getAccountType());
        po.setCurrency(request.getCurrency() != null ? request.getCurrency() : Constants.DEFAULT_CURRENCY);
        po.setBalanceDirection(BalanceDirectionEnum.fromCode(request.getBalanceDirection()));
        po.setAcctNoRule(request.getAcctNoRule());
        po.setAcctNameRule(request.getAcctNameRule());
        TemplateStatusEnum status = TemplateStatusEnum.fromCode(request.getStatus());
        po.setStatus(status != null ? status : TemplateStatusEnum.PENDING);
        po.setCreateId(Constants.SYSTEM_OPERATOR);
        po.setCreateName(Constants.SYSTEM_OPERATOR);
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
        return po;
    }

    /**
     * 更新请求 → PO 字段
     */
    public static void updatePO(TemplateUpdateRequest request, AccountTemplatePO po) {
        if (request.getTemplateName() != null) {
            po.setTemplateName(request.getTemplateName());
        }
        if (request.getAccountType() != null) {
            po.setAccountType(request.getAccountType());
        }
        if (request.getCurrency() != null) {
            po.setCurrency(request.getCurrency());
        }
        if (request.getBalanceDirection() != null) {
            Optional.ofNullable(BalanceDirectionEnum.fromCode(request.getBalanceDirection()))
                    .ifPresent(po::setBalanceDirection);
        }
        if (request.getAcctNoRule() != null) {
            po.setAcctNoRule(request.getAcctNoRule());
        }
        if (request.getAcctNameRule() != null) {
            po.setAcctNameRule(request.getAcctNameRule());
        }
        if (request.getAutoOpen() != null) {
            po.setAutoOpen(request.getAutoOpen());
        }
        if (request.getStatus() != null) {
            TemplateStatusEnum newStatus = TemplateStatusEnum.fromCode(request.getStatus());
            if (newStatus != null) {
                po.setStatus(newStatus);
            }
        }
        po.setUpdateId(Constants.SYSTEM_OPERATOR);
        po.setUpdateName(Constants.SYSTEM_OPERATOR);
    }

    /**
     * PO → 响应 DTO
     */
    public static TemplateResponse toResponse(AccountTemplatePO po) {
        TemplateResponse resp = new TemplateResponse();
        resp.setId(po.getId());
        resp.setTemplateName(po.getTemplateName());
        resp.setBusinessCode(po.getBusinessCode());
        resp.setCustomerType(Optional.ofNullable(po.getCustomerType()).map(CustomerTypeEnum::getCode).orElse(null));
        resp.setAutoOpen(po.getAutoOpen());
        resp.setStatus(Optional.ofNullable(po.getStatus()).map(TemplateStatusEnum::getCode).orElse(null));
        resp.setSubjectCode(po.getSubjectCode());
        resp.setAccountType(po.getAccountType());
        resp.setCurrency(po.getCurrency());
        resp.setBalanceDirection(Optional.ofNullable(po.getBalanceDirection()).map(BalanceDirectionEnum::getCode).orElse(null));
        resp.setAcctNoRule(po.getAcctNoRule());
        resp.setAcctNameRule(po.getAcctNameRule());
        return resp;
    }

    /**
     * PO 列表 → 响应 DTO 列表
     */
    public static List<TemplateResponse> toResponseList(List<AccountTemplatePO> poList) {
        if (poList == null || poList.isEmpty()) {
            return List.of();
        }
        List<TemplateResponse> result = new ArrayList<>(poList.size());
        for (AccountTemplatePO po : poList) {
            result.add(toResponse(po));
        }
        return result;
    }
}
