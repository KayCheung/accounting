package com.kltb.accounting.core.application.service;

import com.kltb.accounting.api.request.BatchPostingRequest;
import com.kltb.accounting.api.request.PostingRetryRequest;
import com.kltb.accounting.api.request.PostingSkipRequest;
import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.application.assembler.PostingEngineAssembler;
import com.kltb.accounting.core.domain.service.PostingEngineDomainService;
import com.kltb.accounting.core.domain.service.PostingEngineDomainService.BatchPostingResult;
import com.kltb.accounting.core.domain.service.PostingMonitorDomainService;
import com.kltb.accounting.core.domain.service.PostingMonitorDomainService.AbnormalVoucherData;
import com.kltb.accounting.core.domain.service.PostingMonitorDomainService.PostingStatsData;
import com.kltb.accounting.core.domain.service.PostingMonitorDomainService.RetryExecuteResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 过账引擎应用服务（编排层）
 * <p>
 * 职责：参数校验、DTO 转换、委托领域服务，不含业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingEngineApplicationService {

    private final PostingEngineDomainService postingEngineDomainService;
    private final PostingMonitorDomainService postingMonitorDomainService;
    private final PostingEngineAssembler assembler;

    /**
     * 批量过账
     */
    public BatchPostingResponse executeBatchPosting(BatchPostingRequest request) {
        LocalDate startDate = request.getStartDate();
        LocalDate endDate = request.getEndDate() != null ? request.getEndDate() : startDate;
        String businessCode = request.getBusinessCode();
        int maxBatchSize = request.getMaxBatchSize() != null ? request.getMaxBatchSize() : 50;

        BatchPostingResult result = postingEngineDomainService.executeBatchPosting(
            startDate, endDate, businessCode, maxBatchSize);

        return assembler.toBatchResponse(result);
    }

    /**
     * 查询凭证过账进度
     */
    public PostingMonitorResponse getVoucherProgress(String voucherNo) {
        var progressData = postingMonitorDomainService.getVoucherProgress(voucherNo);
        return assembler.toVoucherMonitorResponse(progressData);
    }

    /**
     * 查询事务过账进度
     */
    public PostingMonitorResponse getTransactionProgress(String txnNo) {
        var progressData = postingMonitorDomainService.getTransactionProgress(txnNo);
        return assembler.toTransactionMonitorResponse(progressData);
    }

    /**
     * 查询过账统计报表
     */
    public PostingStatsResponse getPostingStats(
        LocalDate startDate, LocalDate endDate, String dimension) {

        List<PostingStatsData> statsData = postingMonitorDomainService.getPostingStats(
            startDate, endDate, dimension);

        return assembler.toStatsResponse(startDate, endDate, dimension, statsData);
    }

    /**
     * 异常凭证重试
     */
    public PostingRetryResponse retryAbnormalVoucher(PostingRetryRequest request) {
        RetryExecuteResult result = postingMonitorDomainService.retryAbnormalVoucher(
            request.getVoucherNo(), request.getOperatorName(), request.getRetryReason());

        return assembler.toRetryResponse(
            result.getVoucherNo(), result.isSuccess(),
            result.getVoucherStatus(), result.getVoucherStatusDesc());
    }

    /**
     * 异常凭证跳过
     */
    public void skipAbnormalVoucher(PostingSkipRequest request) {
        postingMonitorDomainService.skipAbnormalVoucher(
            request.getVoucherNo(), request.getOperatorName(), request.getSkipReason());
    }

    /**
     * 查询异常凭证列表
     */
    public List<AbnormalVoucherResponse> getAbnormalVouchers(
        Integer status, LocalDate startDate, LocalDate endDate) {

        List<AbnormalVoucherData> dataList = postingMonitorDomainService.getAbnormalVouchers(
            status, startDate, endDate, 200);

        return assembler.toAbnormalResponses(dataList);
    }
}
