package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.FreezeDetailResponse;
import com.kltb.accounting.core.domain.enums.FreezeStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 冻结记录 PO ↔ Response 转换器
 */
@Component
public class FreezeAssembler {

    /**
     * PO → 冻结记录响应
     */
    public FreezeDetailResponse toDetailResponse(AccountFreezeDetailPO po) {
        FreezeDetailResponse response = new FreezeDetailResponse();
        response.setFreezeId(po.getVoucherNo());
        response.setAccountNo(po.getAccountNo());
        response.setFreezeAmount(po.getFreezeAmount());
        response.setStatus(Optional.ofNullable(po.getStatus()).map(FreezeStatusEnum::getCode).orElse(null));
        response.setStatusDesc(Optional.ofNullable(po.getStatus()).map(FreezeStatusEnum::getDesc).orElse(null));
        response.setExpireTime(po.getExpireTime());
        response.setTradeTime(po.getTradeTime());
        response.setCreateTime(po.getCreateTime());
        response.setSummary(po.getSummary());
        return response;
    }

    /**
     * PO 列表 → 响应列表
     */
    public List<FreezeDetailResponse> toListResponse(List<AccountFreezeDetailPO> records) {
        return records.stream()
                .map(this::toDetailResponse)
                .collect(Collectors.toList());
    }
}
