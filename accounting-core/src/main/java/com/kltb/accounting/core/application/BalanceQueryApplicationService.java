package com.kltb.accounting.core.application;

import com.kltb.accounting.api.request.AccountDetailQueryRequest;
import com.kltb.accounting.api.request.FreezeRecordQueryRequest;
import com.kltb.accounting.api.response.AccountDetailResponse;
import com.kltb.accounting.api.response.AggregateBalanceResponse;
import com.kltb.accounting.api.response.FreezeListResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.assembler.BalanceQueryAssembler;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService.AccountDetailPageResult;
import com.kltb.accounting.core.domain.service.BalanceQueryDomainService.FreezeRecordPageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 余额查询应用服务（用例编排）
 * <p>
 * 职责：
 * 1. 参数校验 + 委托 BalanceQueryDomainService
 * 2. PO/DTO → Response 转换
 * 3. 纯查询操作，不涉及写入
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceQueryApplicationService {

    private final BalanceQueryDomainService balanceQueryDomainService;
    private final BalanceQueryAssembler balanceQueryAssembler;

    /**
     * 聚合余额查询
     */
    public AggregateBalanceResponse queryAggregateBalance(String accountNo) {
        var dto = balanceQueryDomainService.queryAggregateBalance(accountNo);
        return balanceQueryAssembler.toAggregateResponse(dto);
    }

    /**
     * 账户明细分页查询
     */
    public PageResponse<AccountDetailResponse> queryAccountDetails(AccountDetailQueryRequest request) {
        AccountDetailPageResult result = balanceQueryDomainService.queryAccountDetails(
                request.getAccountNo(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTradeType(),
                request.getDebitCredit(),
                request.getPageNo(),
                request.getPageSize()
        );
        return PageResponse.<AccountDetailResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current((long) result.getCurrent())
                .list(balanceQueryAssembler.toDetailResponseList(result.getList()))
                .build();
    }

    /**
     * 冻结记录分页查询
     */
    public PageResponse<FreezeListResponse> queryFreezeRecords(FreezeRecordQueryRequest request) {
        FreezeRecordPageResult result = balanceQueryDomainService.queryFreezeRecords(
                request.getAccountNo(),
                request.getStatus(),
                request.getPageNo(),
                request.getPageSize()
        );
        return PageResponse.<FreezeListResponse>builder()
                .total(result.getTotal())
                .pages(result.getPages())
                .current((long) result.getCurrent())
                .list(balanceQueryAssembler.toFreezeListResponseList(result.getList()))
                .build();
    }
}
