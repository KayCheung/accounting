package com.kltb.accounting.core.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.kltb.accounting.api.request.FreezeDeductRequest;
import com.kltb.accounting.api.request.FreezePageQueryRequest;
import com.kltb.accounting.api.request.FundFreezeRequest;
import com.kltb.accounting.api.request.FundUnfreezeRequest;
import com.kltb.accounting.api.response.FreezeDetailResponse;
import com.kltb.accounting.api.response.PageResponse;
import com.kltb.accounting.core.application.assembler.FreezeAssembler;
import com.kltb.accounting.core.domain.service.FreezeDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountFreezeDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.FreezeDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 资金冻结应用服务（用例编排）
 * <p>
 * 职责：
 * 1. 参数校验 + DTO 转换
 * 2. 委托调用 FreezeDomainService
 * 3. PO → Response 转换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FreezeApplicationService {

    private final FreezeDomainService freezeDomainService;
    private final FreezeDetailRepository freezeDetailRepository;
    private final FreezeAssembler freezeAssembler;

    /**
     * 资金冻结
     */
    public FreezeDetailResponse freezeFund(FundFreezeRequest request) {
        AccountFreezeDetailPO record = freezeDomainService.freezeFund(
                request.getAccountNo(),
                request.getFreezeAmount(),
                request.getExpireTime(),
                request.getReason());
        return freezeAssembler.toDetailResponse(record);
    }

    /**
     * 资金解冻
     */
    public void unfreezeFund(FundUnfreezeRequest request) {
        freezeDomainService.unfreezeFund(
                request.getFreezeId(),
                request.getUnfreezeAmount(),
                request.getReason());
    }

    /**
     * 冻结扣款
     */
    public void deductFromFreeze(FreezeDeductRequest request) {
        freezeDomainService.deductFromFreeze(
                request.getFreezeId(),
                request.getDeductAmount(),
                request.getReason());
    }

    /**
     * 查询冻结记录
     */
    public FreezeDetailResponse queryFreezeRecord(String freezeId) {
        AccountFreezeDetailPO record = freezeDomainService.queryFreezeRecord(freezeId);
        return freezeAssembler.toDetailResponse(record);
    }

    /**
     * 查询冻结记录列表
     */
    public List<FreezeDetailResponse> queryFreezeRecords(String accountNo, Integer status) {
        List<AccountFreezeDetailPO> records = freezeDomainService.queryFreezeRecords(accountNo, status);
        return freezeAssembler.toListResponse(records);
    }

    /**
     * 分页查询冻结记录
     */
    public PageResponse<FreezeDetailResponse> queryFreezePage(FreezePageQueryRequest request) {
        LambdaQueryWrapper<AccountFreezeDetailPO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(request.getAccountNo())) {
            wrapper.eq(AccountFreezeDetailPO::getAccountNo, request.getAccountNo().trim());
        }
        if (StringUtils.isNotBlank(request.getFreezeId())) {
            wrapper.eq(AccountFreezeDetailPO::getVoucherNo, request.getFreezeId().trim());
        }
        if (request.getStatus() != null) {
            wrapper.eq(AccountFreezeDetailPO::getStatus, request.getStatus());
        }
        if (request.getStartDate() != null) {
            wrapper.ge(AccountFreezeDetailPO::getCreateTime, request.getStartDate().atStartOfDay());
        }
        if (request.getEndDate() != null) {
            wrapper.le(AccountFreezeDetailPO::getCreateTime, request.getEndDate().atTime(23, 59, 59));
        }
        wrapper.eq(AccountFreezeDetailPO::getIsDelete, 0);
        wrapper.orderByDesc(AccountFreezeDetailPO::getCreateTime);

        int pageNo = request.getPageNo() != null && request.getPageNo() > 0 ? request.getPageNo() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 20;

        IPage<AccountFreezeDetailPO> page = freezeDetailRepository.selectPage(wrapper, pageNo, pageSize);

        return PageResponse.<FreezeDetailResponse>builder()
                .total(page.getTotal())
                .pages(page.getPages())
                .current(page.getCurrent())
                .list(freezeAssembler.toListResponse(page.getRecords()))
                .build();
    }
}
