package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.*;
import com.kltb.accounting.core.domain.enums.DebitCreditEnum;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.service.PostingEngineDomainService;
import com.kltb.accounting.core.domain.service.PostingMonitorDomainService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 过账引擎 PO/Result → DTO 转换器
 */
@Component
@RequiredArgsConstructor
public class PostingEngineAssembler {

    private final AccountingVoucherRepository accountingVoucherRepository;

    /**
     * 批量过账结果 → Response DTO
     */
    public BatchPostingResponse toBatchResponse(PostingEngineDomainService.BatchPostingResult result) {
        BatchPostingResponse response = new BatchPostingResponse();
        response.setTotalCount(result.getTotalCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailedCount(result.getFailedCount());
        response.setTotalDurationMs(result.getTotalDurationMs());
        response.setFailedList(result.getFailedList().stream()
            .map(f -> {
                BatchPostingResponse.FailedVoucherInfo info = new BatchPostingResponse.FailedVoucherInfo();
                info.setVoucherNo(f.getVoucherNo());
                info.setFailReason(f.getFailReason());
                return info;
            })
            .collect(Collectors.toList()));
        return response;
    }

    /**
     * 凭证过账进度 → Response DTO
     */
    public PostingMonitorResponse toVoucherMonitorResponse(PostingMonitorDomainService.VoucherProgressData data) {
        PostingMonitorResponse response = new PostingMonitorResponse();
        response.setNo(data.getVoucherNo());
        response.setStatus(data.getStatus());
        response.setStatusDesc(data.getStatusDesc());
        response.setAccountingDate(data.getAccountingDate());
        response.setTotalCount(data.getTotalEntries());
        response.setPostedCount(data.getRealTimePosted() + data.getAsyncPosted());
        response.setProcessingCount(data.getTotalEntries() - data.getRealTimePosted() - data.getAsyncPosted() - data.getBufferPosted());
        response.setFailedCount(0);
        response.setProgressPercent(data.getProgressPercent());

        // 填充分录详情
        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository.selectEntriesByVoucherNo(data.getVoucherNo());
        response.setDetailList(entries.stream().map(this::toDetailInfo).collect(Collectors.toList()));

        return response;
    }

    private PostingMonitorResponse.PostingDetailInfo toDetailInfo(AccountingVoucherEntryPO entry) {
        PostingMonitorResponse.PostingDetailInfo info = new PostingMonitorResponse.PostingDetailInfo();
        info.setEntryId(entry.getEntryId());
        info.setSubjectCode(entry.getSubjectCode());
        info.setAccountNo(entry.getAccountNo());
        info.setDebitCredit(Optional.ofNullable(entry.getDebitCredit()).map(DebitCreditEnum::getCode).orElse(null));
        info.setAmount(entry.getAmount());
        info.setEntryStatus(Optional.ofNullable(entry.getStatus()).map(VoucherEntryStatusEnum::getCode).orElse(null));
        info.setEntryStatusDesc(Optional.ofNullable(entry.getStatus()).map(VoucherEntryStatusEnum::getDesc).orElse(null));
        info.setUnilateral(entry.getUnilateral());
        info.setBuffered(entry.getBuffered());
        return info;
    }

    /**
     * 事务过账进度 → Response DTO
     */
    public PostingMonitorResponse toTransactionMonitorResponse(PostingMonitorDomainService.TransactionProgressData data) {
        PostingMonitorResponse response = new PostingMonitorResponse();
        response.setNo(data.getTxnNo());
        response.setStatus(data.getStatus());
        response.setStatusDesc(data.getStatusDesc());
        response.setAccountingDate(data.getAccountingDate());
        response.setTotalCount(data.getTotalVouchers());
        response.setPostedCount(data.getPostedVouchers());
        response.setProcessingCount(data.getProcessingVouchers());
        response.setFailedCount(data.getFailedVouchers());
        response.setProgressPercent(data.getProgressPercent());
        return response;
    }

    /**
     * 统计数据列表 → Response DTO
     */
    public PostingStatsResponse toStatsResponse(
        LocalDate startDate,
        LocalDate endDate,
        String dimension,
        List<PostingMonitorDomainService.PostingStatsData> statsDataList) {

        PostingStatsResponse response = new PostingStatsResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setDimension(dimension);
        response.setStatsList(statsDataList.stream()
            .map(d -> {
                PostingStatsResponse.StatsGroupData g = new PostingStatsResponse.StatsGroupData();
                g.setDimensionValue(d.getDimensionValue());
                g.setTotalCount(d.getTotalCount());
                g.setSuccessCount(d.getSuccessCount());
                g.setFailedCount(d.getFailedCount());
                g.setProcessingCount(d.getProcessingCount());
                g.setPendingCount(d.getPendingCount());
                g.setSuccessRate(d.getSuccessRate());
                g.setAvgDurationMs(d.getAvgDurationMs());
                g.setMaxDurationMs(d.getMaxDurationMs());
                g.setMinDurationMs(d.getMinDurationMs());
                return g;
            })
            .collect(Collectors.toList()));
        return response;
    }

    /**
     * 重试结果 → Response DTO
     */
    public PostingRetryResponse toRetryResponse(
        String voucherNo, boolean success, Integer status, String statusDesc) {
        PostingRetryResponse response = new PostingRetryResponse();
        response.setVoucherNo(voucherNo);
        response.setSuccess(success);
        response.setVoucherStatus(status);
        response.setVoucherStatusDesc(statusDesc);
        return response;
    }

    /**
     * 异常凭证列表 → Response DTO 列表
     */
    public List<AbnormalVoucherResponse> toAbnormalResponses(List<PostingMonitorDomainService.AbnormalVoucherData> dataList) {
        return dataList.stream()
            .map(d -> {
                AbnormalVoucherResponse r = new AbnormalVoucherResponse();
                r.setVoucherNo(d.getVoucherNo());
                r.setStatus(d.getStatus());
                r.setStatusDesc(d.getStatusDesc());
                r.setAccountingDate(d.getAccountingDate());
                r.setFailReason(d.getFailReason());
                r.setRetryCount(d.getRetryCount());
                r.setSkipFlag(d.getSkipFlag());
                r.setUpdateTime(d.getUpdateTime());
                return r;
            })
            .collect(Collectors.toList());
    }
}
