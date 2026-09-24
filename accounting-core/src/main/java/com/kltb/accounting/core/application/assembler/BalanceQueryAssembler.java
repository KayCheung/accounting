package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.AccountDetailResponse;
import com.kltb.accounting.api.response.AggregateBalanceResponse;
import com.kltb.accounting.api.response.FreezeListResponse;
import com.kltb.accounting.core.domain.enums.*;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 余额查询 PO → Response 转换器
 */
@Component
public class BalanceQueryAssembler {

    /**
     * 聚合余额 DTO → Response
     */
    public AggregateBalanceResponse toAggregateResponse(
            BalanceQueryDomainService.AggregateBalanceDTO dto) {
        AggregateBalanceResponse response = new AggregateBalanceResponse();
        response.setAccountNo(dto.getAccountNo());
        response.setAccountName(dto.getAccountName());
        response.setSubjectCode(dto.getSubjectCode());
        response.setMainBalance(dto.getMainBalance());
        response.setAvailableBalance(dto.getAvailableBalance());
        response.setFrozenBalance(dto.getFrozenBalance());
        response.setBufferEstimate(dto.getBufferEstimate());
        response.setTotalBalance(dto.getTotalBalance());
        response.setStatus(dto.getStatus());
        response.setRiskStatus(dto.getRiskStatus());
        response.setBalanceDirection(dto.getBalanceDirection());
        response.setCurrency(dto.getCurrency());
        response.setQueryTime(dto.getQueryTime());
        return response;
    }

    /**
     * AccountDetailPO → Response
     */
    public AccountDetailResponse toDetailResponse(AccountDetailPO po) {
        AccountDetailResponse response = new AccountDetailResponse();
        response.setVoucherNo(po.getVoucherNo());
        response.setEntryId(po.getEntryId());
        response.setTxnNo(po.getTxnNo());
        response.setTraceNo(po.getTraceNo());
        response.setSubjectCode(po.getSubjectCode());
        response.setAccountNo(po.getAccountNo());
        response.setBusinessCode(po.getBusinessCode());
        response.setTradingCode(po.getTradingCode());
        response.setTradeType(Optional.ofNullable(po.getTradeType()).map(TradeTypeEnum::getCode).orElse(null));
        response.setTradeTypeDesc(Optional.ofNullable(po.getTradeType()).map(TradeTypeEnum::getDesc).orElse(null));
        response.setTradeTime(po.getTradeTime());
        response.setDebitCredit(Optional.ofNullable(po.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        response.setDebitCreditDesc(Optional.ofNullable(po.getDebitCredit()).map(DebitCreditEnum::getDesc).orElse(null));
        response.setChangeDirection(Optional.ofNullable(po.getChangeDirection()).map(ChangeDirectionEnum::getCode).orElse(null));
        response.setChangeDirectionDesc(Optional.ofNullable(po.getChangeDirection()).map(ChangeDirectionEnum::getDesc).orElse(null));
        response.setCurrency(po.getCurrency());
        response.setPreBalance(po.getPreBalance());
        response.setAmount(po.getAmount());
        response.setPostBalance(po.getPostBalance());
        response.setAccountingDate(po.getAccountingDate());
        response.setSummary(po.getSummary());
        return response;
    }

    public List<AccountDetailResponse> toDetailResponseList(List<AccountDetailPO> records) {
        return records.stream()
                .map(this::toDetailResponse)
                .collect(Collectors.toList());
    }

    /**
     * AccountFreezeDetailPO → FreezeListResponse
     */
    public FreezeListResponse toFreezeListResponse(AccountFreezeDetailPO po) {
        FreezeListResponse response = new FreezeListResponse();
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

    public List<FreezeListResponse> toFreezeListResponseList(List<AccountFreezeDetailPO> records) {
        return records.stream()
                .map(this::toFreezeListResponse)
                .collect(Collectors.toList());
    }
}
