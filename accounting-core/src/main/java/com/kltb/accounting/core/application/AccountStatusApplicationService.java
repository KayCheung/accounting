package com.kltb.accounting.core.application;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.AccountCancelRequest;
import com.kltb.accounting.api.request.AccountFreezeRequest;
import com.kltb.accounting.api.request.AccountRiskStatusRequest;
import com.kltb.accounting.api.request.AccountUnfreezeRequest;
import com.kltb.accounting.api.response.AccountStatusChangeResponse;
import com.kltb.accounting.api.response.AccountStatusResponse;
import com.kltb.accounting.core.application.assembler.AccountStatusAssembler;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.enums.RiskStatusEnum;
import com.kltb.accounting.core.domain.service.AccountStatusChangeDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import com.kltb.accounting.core.shared.exception.AccountException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 账户状态管理应用服务（用例编排）
 * <p>
 * 职责：
 * 1. 将 Request DTO 转换为领域方法参数
 * 2. 调用领域服务完成状态变更
 * 3. 将返回的 PO 转换为 Response DTO
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountStatusApplicationService {

    private final AccountStatusChangeDomainService accountStatusChangeDomainService;
    private final AccountStatusAssembler accountStatusAssembler;

    /**
     * 冻结账户
     */
    public AccountStatusChangeResponse freeze(AccountFreezeRequest request) {
        AccountPO previous = accountStatusChangeDomainService.queryAccountStatus(request.getAccountNo());
        AccountPO updated = accountStatusChangeDomainService.freezeAccount(
                request.getAccountNo(), request.getReason());
        return accountStatusAssembler.toChangeResponse(updated, previous.getStatus());
    }

    /**
     * 解冻账户
     */
    public AccountStatusChangeResponse unfreeze(AccountUnfreezeRequest request) {
        AccountPO previous = accountStatusChangeDomainService.queryAccountStatus(request.getAccountNo());
        AccountPO updated = accountStatusChangeDomainService.unfreezeAccount(request.getAccountNo());
        return accountStatusAssembler.toChangeResponse(updated, previous.getStatus());
    }

    /**
     * 注销账户
     */
    public AccountStatusChangeResponse cancel(AccountCancelRequest request) {
        AccountPO previous = accountStatusChangeDomainService.queryAccountStatus(request.getAccountNo());
        AccountPO updated = accountStatusChangeDomainService.cancelAccount(
                request.getAccountNo(), request.getReason());
        return accountStatusAssembler.toChangeResponse(updated, previous.getStatus());
    }

    /**
     * 查询账户状态
     */
    public AccountStatusResponse queryStatus(String accountNo) {
        AccountPO account = accountStatusChangeDomainService.queryAccountStatus(accountNo);
        return accountStatusAssembler.toStatusResponse(account);
    }

    /**
     * 变更风控状态
     */
    public AccountStatusChangeResponse changeRiskStatus(AccountRiskStatusRequest request) {
        AccountPO previous = accountStatusChangeDomainService.queryAccountStatus(request.getAccountNo());
        RiskStatusEnum riskStatus = resolveRiskStatus(request.getRiskStatus());
        AccountPO updated = accountStatusChangeDomainService.changeRiskStatus(
                request.getAccountNo(), riskStatus);
        return accountStatusAssembler.toChangeResponse(updated, previous.getStatus());
    }

    private RiskStatusEnum resolveRiskStatus(Integer code) {
        for (RiskStatusEnum status : RiskStatusEnum.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new AccountException(ResultCode.PARAM_ERROR, "非法的风控状态码: " + code);
    }
}
