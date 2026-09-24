package com.kltb.accounting.core.application.converter;

import com.kltb.accounting.api.request.TemplateCreateRequest;
import com.kltb.accounting.api.request.TemplateUpdateRequest;
import com.kltb.accounting.core.application.dto.TemplateResponse;
import com.kltb.accounting.core.domain.enums.BalanceDirectionEnum;
import com.kltb.accounting.core.domain.enums.CustomerTypeEnum;
import com.kltb.accounting.core.domain.enums.TemplateStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountTemplatePO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 开户模板转换器
 *
 * 是否记账：否
 */
public class TemplateConverter {

    private static final Map<Integer, CustomerTypeEnum> CUSTOMER_TYPE_MAP = Map.of(
            1, CustomerTypeEnum.PERSONAL,
            2, CustomerTypeEnum.ENTERPRISE,
            99, CustomerTypeEnum.OTHER
    );

    private static final Map<Integer, BalanceDirectionEnum> BALANCE_DIR_MAP = Map.of(
            1, BalanceDirectionEnum.DEBIT,
            2, BalanceDirectionEnum.CREDIT
    );

    /**
     * 创建请求 → PO
     */
    public static AccountTemplatePO toPO(TemplateCreateRequest request) {
        AccountTemplatePO po = new AccountTemplatePO();
        po.setTemplateName(request.getTemplateName());
        po.setBusinessCode(request.getBusinessCode());
        po.setCustomerType(CUSTOMER_TYPE_MAP.getOrDefault(request.getCustomerType(), CustomerTypeEnum.OTHER));
        po.setAutoOpen(request.getAutoOpen() != null && request.getAutoOpen());
        po.setSubjectCode(request.getSubjectCode());
        po.setAccountType(request.getAccountType());
        po.setCurrency(request.getCurrency() != null ? request.getCurrency() : "CNY");
        po.setBalanceDirection(BALANCE_DIR_MAP.getOrDefault(request.getBalanceDirection(), BalanceDirectionEnum.DEBIT));
        po.setAcctNoRule(request.getAcctNoRule());
        po.setAcctNameRule(request.getAcctNameRule());
        TemplateStatusEnum status = TemplateStatusEnum.fromCode(request.getStatus());
        po.setStatus(status != null ? status : TemplateStatusEnum.PENDING);
        po.setCreateId("system");
        po.setCreateName("system");
        po.setUpdateId("system");
        po.setUpdateName("system");
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
            po.setBalanceDirection(BALANCE_DIR_MAP.getOrDefault(request.getBalanceDirection(), po.getBalanceDirection()));
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
        po.setUpdateId("system");
        po.setUpdateName("system");
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
