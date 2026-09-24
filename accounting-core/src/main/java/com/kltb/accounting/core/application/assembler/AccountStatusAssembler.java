package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.AccountStatusChangeResponse;
import com.kltb.accounting.api.response.AccountStatusResponse;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 账户状态 PO ↔ Response 转换器
 */
@Component
public class AccountStatusAssembler {

    private static final LocalDate EPOCH_DATE = LocalDate.of(1970, 1, 1);

    /**
     * PO → 状态查询响应
     */
    public AccountStatusResponse toStatusResponse(AccountPO po) {
        AccountStatusResponse response = new AccountStatusResponse();
        response.setAccountNo(po.getAccountNo());
        response.setAccountName(po.getAccountName());
        response.setSubjectCode(po.getSubjectCode());
        response.setStatus(Optional.ofNullable(po.getStatus()).map(AccountStatusEnum::getCode).orElse(null));
        response.setStatusDesc(Optional.ofNullable(po.getStatus()).map(AccountStatusEnum::getDesc).orElse(null));
        response.setRiskStatus(Optional.ofNullable(po.getRiskStatus()).map(RiskStatusEnum::getCode).orElse(null));
        response.setRiskStatusDesc(Optional.ofNullable(po.getRiskStatus()).map(RiskStatusEnum::getDesc).orElse(null));
        response.setBalance(po.getBalance());
        response.setOpenDate(po.getOpenDate());
        response.setInactiveDate(normalizeInactiveDate(po.getInactiveDate()));
        return response;
    }

    /**
     * PO → 状态变更响应
     *
     * @param po 更新后的账户PO
     * @param previousStatus 变更前状态
     */
    public AccountStatusChangeResponse toChangeResponse(AccountPO po, AccountStatusEnum previousStatus) {
        AccountStatusChangeResponse response = new AccountStatusChangeResponse();
        response.setAccountNo(po.getAccountNo());
        response.setPreviousStatus(Optional.ofNullable(previousStatus).map(AccountStatusEnum::getCode).orElse(null));
        response.setCurrentStatus(Optional.ofNullable(po.getStatus()).map(AccountStatusEnum::getCode).orElse(null));
        response.setStatusDesc(Optional.ofNullable(po.getStatus()).map(AccountStatusEnum::getDesc).orElse(null));
        response.setRiskStatus(Optional.ofNullable(po.getRiskStatus()).map(RiskStatusEnum::getCode).orElse(null));
        response.setRiskStatusDesc(Optional.ofNullable(po.getRiskStatus()).map(RiskStatusEnum::getDesc).orElse(null));
        return response;
    }

    /**
     * DDL 默认值 1970-01-01 转为 null，前端显示"未注销"
     */
    private LocalDate normalizeInactiveDate(LocalDate inactiveDate) {
        if (inactiveDate != null && inactiveDate.equals(EPOCH_DATE)) {
            return null;
        }
        return inactiveDate;
    }
}
