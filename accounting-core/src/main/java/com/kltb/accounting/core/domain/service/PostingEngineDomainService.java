package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.request.PostingExecuteRequest;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.core.application.service.PostingApplicationService;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 批量过账领域服务
 * <p>
 * 职责：批量过账核心逻辑，不含事务，逐笔委托 Step 11 的 PostingApplicationService。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingEngineDomainService {

    private final PostingApplicationService postingApplicationService;
    private final AccountingVoucherRepository accountingVoucherRepository;

    /**
     * 批量过账：按条件查询待过账凭证 → 逐笔执行过账
     * <p>
     * 单笔失败不抛异常，记录到 failedList 后继续处理。
     *
     * @param startDate    起始会计日期
     * @param endDate      结束会计日期
     * @param businessCode 业务线编码（null 表示全部）
     * @param maxBatchSize 最大批次大小
     * @return 批量过账结果
     */
    public BatchPostingResult executeBatchPosting(
        LocalDate startDate,
        LocalDate endDate,
        String businessCode,
        int maxBatchSize) {

        List<AccountingVoucherPO> vouchers = selectPendingVouchers(
            startDate, endDate, businessCode, maxBatchSize);

        int totalCount = vouchers.size();
        int successCount = 0;
        int failedCount = 0;
        long startTime = System.currentTimeMillis();
        List<FailedVoucherInfo> failedList = new ArrayList<>();

        for (AccountingVoucherPO voucher : vouchers) {
            try {
                PostingExecuteRequest request = new PostingExecuteRequest();
                request.setVoucherNo(voucher.getVoucherNo());
                request.setOperatorName("SYSTEM");
                postingApplicationService.executePosting(request);
                successCount++;
                log.info("[BATCH-POSTING] 过账成功: voucherNo={}", voucher.getVoucherNo());
            } catch (Exception e) {
                failedCount++;
                String failReason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                failedList.add(new FailedVoucherInfo(voucher.getVoucherNo(), failReason));
                log.warn("[BATCH-POSTING] 过账失败: voucherNo={}, reason={}",
                    voucher.getVoucherNo(), failReason);
            }
        }

        long totalDurationMs = System.currentTimeMillis() - startTime;
        return new BatchPostingResult(totalCount, successCount, failedCount, totalDurationMs, failedList);
    }

    /**
     * 查询待过账凭证列表
     * <p>
     * 仅查询 status=1(未过账) 且 posting_type IN ('REALTIME','ASYNC') 的凭证。
     */
    public List<AccountingVoucherPO> selectPendingVouchers(
        LocalDate startDate,
        LocalDate endDate,
        String businessCode,
        int limit) {
        // status=1 表示未过账
        return accountingVoucherRepository.selectByStatusAndDateRange(
            1, startDate, endDate, businessCode, limit);
    }

    /**
     * 单笔凭证过账（委托给 Step 11）
     */
    public PostingExecuteResult postSingleVoucher(String voucherNo) {
        PostingExecuteRequest request = new PostingExecuteRequest();
        request.setVoucherNo(voucherNo);
        request.setOperatorName("SYSTEM");
        PostingExecuteResponse response = postingApplicationService.executePosting(request);
        return new PostingExecuteResult(
            response.getVoucherNo(),
            response.getVoucherStatus(),
            response.getVoucherStatusDesc());
    }

    /**
     * 批量过账结果
     */
    @Data
    @AllArgsConstructor
    public static class BatchPostingResult {
        private int totalCount;
        private int successCount;
        private int failedCount;
        private long totalDurationMs;
        private List<FailedVoucherInfo> failedList;
    }

    /**
     * 失败凭证信息
     */
    @Data
    @AllArgsConstructor
    public static class FailedVoucherInfo {
        private String voucherNo;
        private String failReason;
    }

    /**
     * 单笔过账结果（透传 Step 11 响应）
     */
    @Data
    @AllArgsConstructor
    public static class PostingExecuteResult {
        private String voucherNo;
        private Integer voucherStatus;
        private String voucherStatusDesc;
    }
}
