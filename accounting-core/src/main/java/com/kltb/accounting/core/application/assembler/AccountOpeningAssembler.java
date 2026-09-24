package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.AccountOpenResponse;
import com.kltb.accounting.api.response.BatchOpenResultResponse;
import com.kltb.accounting.core.domain.enums.AccountStatusEnum;
import com.kltb.accounting.core.domain.model.BatchOpenResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountPO;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 开户 PO ↔ Response 转换器
 */
@Component
public class AccountOpeningAssembler {

    public AccountOpenResponse toResponse(AccountPO po) {
        AccountOpenResponse response = new AccountOpenResponse();
        response.setAccountNo(po.getAccountNo());
        response.setAccountName(po.getAccountName());
        response.setSubjectCode(po.getSubjectCode());
        response.setOwnerId(po.getOwnerId());
        response.setStatus(Optional.ofNullable(po.getStatus()).map(AccountStatusEnum::getCode).orElse(null));
        response.setOpenDate(po.getOpenDate());
        return response;
    }

    public BatchOpenResultResponse toBatchResponse(BatchOpenResult result) {
        BatchOpenResultResponse response = new BatchOpenResultResponse();
        response.setTotalCount(result.getTotalCount());
        response.setAlreadyExists(result.getAlreadyExists());
        response.setNewlyCreated(result.getNewlyCreated());
        response.setFailed(result.getFailed());
        response.setFailedReasons(result.getFailedReasons());
        return response;
    }
}
