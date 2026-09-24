package com.kltb.accounting.core.domain.service;

import com.kltb.accounting.api.constant.ResultCode;
import com.kltb.accounting.api.request.PostingExecuteRequest;
import com.kltb.accounting.api.response.PostingExecuteResponse;
import com.kltb.accounting.core.application.service.PostingApplicationService;
import com.kltb.accounting.core.domain.enums.VoucherEntryStatusEnum;
import com.kltb.accounting.core.domain.enums.VoucherStatusEnum;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherEntryPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.AccountingVoucherPO;
import com.kltb.accounting.core.infrastructure.persistence.entity.TransactionPO;
import com.kltb.accounting.core.infrastructure.persistence.mapper.TransactionMapper;
import com.kltb.accounting.core.infrastructure.persistence.repository.AccountingVoucherRepository;
import com.kltb.accounting.core.infrastructure.persistence.repository.TransactionRepository;
import com.kltb.accounting.core.shared.exception.ServiceException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 过账监控领域服务
 * <p>
 * 职责：进度查询、统计报表、异常凭证治理（重试/跳过/僵尸检测）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingMonitorDomainService {

    private final AccountingVoucherRepository accountingVoucherRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final PostingApplicationService postingApplicationService;
    private final TransactionTemplate transactionTemplate;

    private static final int MAX_RETRY_COUNT = 5;
    private static final long ZOMBIE_THRESHOLD_HOURS = 1;

    /**
     * 查询凭证过账进度
     */
    public VoucherProgressData getVoucherProgress(String voucherNo) {
        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (voucher == null) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "凭证不存在: " + voucherNo);
        }

        List<AccountingVoucherEntryPO> entries = accountingVoucherRepository
            .selectEntriesByVoucherNo(voucherNo);

        int totalEntries = entries.size();
        int realTimeTotal = 0, realTimePosted = 0;
        int asyncTotal = 0, asyncPosted = 0;
        int bufferTotal = 0, bufferPosted = 0;

        for (AccountingVoucherEntryPO entry : entries) {
            if (entry.getUnilateral() != null && entry.getUnilateral() == 1) {
                realTimeTotal++;
                if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
                    realTimePosted++;
                }
            } else if (entry.getBuffered() != null && entry.getBuffered() == 1) {
                bufferTotal++;
                if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
                    bufferPosted++;
                }
            } else {
                asyncTotal++;
                if (entry.getStatus() == VoucherEntryStatusEnum.POSTED) {
                    asyncPosted++;
                }
            }
        }

        BigDecimal progressPercent;
        int nonBufferTotal = realTimeTotal + asyncTotal;
        if (nonBufferTotal == 0) {
            progressPercent = BigDecimal.ZERO;
        } else {
            progressPercent = BigDecimal.valueOf(realTimePosted + asyncPosted)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(nonBufferTotal), 2, RoundingMode.HALF_UP);
        }

        return new VoucherProgressData(
            voucherNo,
            voucher.getStatus() != null ? voucher.getStatus().getCode() : null,
            voucher.getStatus() != null ? voucher.getStatus().getDesc() : "未知",
            voucher.getAccountingDate(),
            totalEntries,
            realTimePosted, realTimeTotal,
            asyncPosted, asyncTotal,
            bufferPosted, bufferTotal,
            progressPercent
        );
    }

    /**
     * 查询事务过账进度
     */
    public TransactionProgressData getTransactionProgress(String txnNo) {
        TransactionPO txn = transactionRepository.selectByTxnNo(txnNo);
        if (txn == null) {
            throw new ServiceException(ResultCode.DATA_NOT_FOUND, "事务不存在: " + txnNo);
        }

        // 查询关联凭证（通过 Repository 层，不直接调用 Mapper）
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository.selectByTraceNo(txn.getTraceNo())
            .stream()
            .filter(v -> txnNo.equals(v.getTxnNo()))
            .collect(Collectors.toList());

        int totalVouchers = vouchers.size();
        int postedVouchers = 0;
        int processingVouchers = 0;
        int failedVouchers = 0;
        List<VoucherProgressData> voucherProgressList = new ArrayList<>();

        for (AccountingVoucherPO voucher : vouchers) {
            VoucherStatusEnum status = voucher.getStatus();
            if (status == VoucherStatusEnum.POSTED) {
                postedVouchers++;
            } else if (status == VoucherStatusEnum.POSTING) {
                processingVouchers++;
            } else if (status == VoucherStatusEnum.FAILED) {
                failedVouchers++;
            }

            try {
                VoucherProgressData progress = getVoucherProgress(voucher.getVoucherNo());
                voucherProgressList.add(progress);
            } catch (Exception e) {
                log.warn("[MONITOR] 查询凭证进度失败: voucherNo={}", voucher.getVoucherNo(), e);
            }
        }

        BigDecimal progressPercent;
        if (totalVouchers == 0) {
            progressPercent = BigDecimal.ZERO;
        } else {
            progressPercent = BigDecimal.valueOf(postedVouchers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalVouchers), 2, RoundingMode.HALF_UP);
        }

        return new TransactionProgressData(
            txnNo,
            txn.getStatus() != null ? txn.getStatus().getCode() : null,
            txn.getStatus() != null ? txn.getStatus().getDesc() : "未知",
            txn.getAccountingDate(),
            totalVouchers,
            postedVouchers,
            processingVouchers,
            failedVouchers,
            progressPercent,
            voucherProgressList
        );
    }

    /**
     * 查询过账统计报表（按维度分组）
     *
     * @param dimension 维度：date / businessCode / tradingCode / payChannel
     */
    public List<PostingStatsData> getPostingStats(
        LocalDate startDate, LocalDate endDate, String dimension) {

        List<Map<String, Object>> statusGroups = accountingVoucherRepository
            .countByStatusGroup(startDate, endDate, null);

        // 按指定维度分组
        String dimensionKey = switch (dimension != null ? dimension : "date") {
            case "businessCode" -> "business_code";
            case "tradingCode" -> "trading_code";
            case "payChannel" -> "pay_channel";
            default -> "accounting_date";
        };

        Map<String, Map<Integer, Long>> grouped = statusGroups.stream()
            .collect(Collectors.groupingBy(
                row -> String.valueOf(row.get(dimensionKey) != null ? row.get(dimensionKey) : "N/A"),
                Collectors.toMap(
                    row -> ((Number) row.get("status")).intValue(),
                    row -> ((Number) row.get("count")).longValue()
                )
            ));

        Map<String, Object> durationStats = transactionMapper.selectDurationStats(startDate, endDate, null);
        Long avgDuration = extractDuration(durationStats, "avg_duration_ms");
        Long maxDuration = extractDuration(durationStats, "max_duration_ms");
        Long minDuration = extractDuration(durationStats, "min_duration_ms");

        List<PostingStatsData> result = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, Long>> entry : grouped.entrySet()) {
            String dimValue = entry.getKey();
            Map<Integer, Long> counts = entry.getValue();

            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            long success = counts.getOrDefault(3, 0L);
            long failed = counts.getOrDefault(4, 0L);
            long processing = counts.getOrDefault(2, 0L);
            long pending = counts.getOrDefault(1, 0L);

            BigDecimal successRate = total == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(success)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

            result.add(new PostingStatsData(
                dimValue,
                (int) total,
                (int) success,
                (int) failed,
                (int) processing,
                (int) pending,
                successRate,
                avgDuration, maxDuration, minDuration
            ));
        }

        return result;
    }

    /**
     * 查询异常凭证列表
     */
    public List<AbnormalVoucherData> getAbnormalVouchers(
        Integer status, LocalDate startDate, LocalDate endDate, int limit) {

        List<AccountingVoucherPO> vouchers = accountingVoucherRepository
            .selectByStatusAndDateRange(status, startDate, endDate, null, limit);

        return vouchers.stream()
            .filter(v -> v.getStatus() == VoucherStatusEnum.FAILED
                || v.getStatus() == VoucherStatusEnum.POSTING)
            .map(this::toAbnormalVoucherData)
            .collect(Collectors.toList());
    }

    /**
     * 僵尸凭证检测：status=2(过账中) 且 update_time < NOW() - 1h
     */
    public List<AbnormalVoucherData> detectZombieVouchers() {
        List<AccountingVoucherPO> vouchers = accountingVoucherRepository
            .selectByStatusAndDateRange(
                VoucherStatusEnum.POSTING.getCode(), null, null, null, 1000);

        LocalDateTime threshold = LocalDateTime.now().minusHours(ZOMBIE_THRESHOLD_HOURS);
        List<AbnormalVoucherData> zombies = new ArrayList<>();

        for (AccountingVoucherPO voucher : vouchers) {
            if (voucher.getUpdateTime() != null && voucher.getUpdateTime().isBefore(threshold)) {
                // 独立事务更新凭证 + 联动更新关联事务状态
                transactionTemplate.execute(status -> {
                    voucher.setStatus(VoucherStatusEnum.FAILED);
                    voucher.setFailReason("过账超时，自动标记失败");
                    accountingVoucherRepository.updateById(voucher);

                    // 联动更新事务状态
                    if (voucher.getTxnNo() != null) {
                        TransactionPO txn = transactionRepository.selectByTxnNo(voucher.getTxnNo());
                        if (txn != null && txn.getStatus() != com.kltb.accounting.core.domain.enums.TransactionStatusEnum.FAILED) {
                            transactionRepository.updateStatusByTxnNo(
                                txn.getTxnNo(),
                                com.kltb.accounting.core.domain.enums.TransactionStatusEnum.FAILED,
                                "关联凭证过账超时：" + voucher.getVoucherNo(),
                                java.time.LocalDateTime.now()
                            );
                        }
                    }
                    return true;
                });

                log.warn("[ZOMBIE-DETECT] 检测到僵尸凭证: voucherNo={}, updateTime={}",
                    voucher.getVoucherNo(), voucher.getUpdateTime());

                zombies.add(toAbnormalVoucherData(voucher));
            }
        }

        return zombies;
    }

    /**
     * 凭证重试（限次校验 + 委托 Step 11）
     */
    public RetryExecuteResult retryAbnormalVoucher(
        String voucherNo, String operatorName, String retryReason) {

        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (voucher == null) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "凭证不存在: " + voucherNo);
        }

        VoucherStatusEnum currentStatus = voucher.getStatus();
        if (currentStatus != VoucherStatusEnum.FAILED && currentStatus != VoucherStatusEnum.POSTING) {
            throw new ServiceException(ResultCode.VOUCHER_STATUS_ILLEGAL,
                "凭证状态非法，仅允许失败/过账中的凭证重试: " + voucherNo
                    + ", 当前状态=" + (currentStatus != null ? currentStatus.getDesc() : "null"));
        }

        int retryCount = voucher.getRetryCount() != null ? voucher.getRetryCount() : 0;
        if (retryCount >= MAX_RETRY_COUNT) {
            throw new ServiceException(ResultCode.POSTING_RETRY_EXHAUSTED,
                "凭证重试次数已达上限: " + voucherNo + ", 当前重试次数=" + retryCount);
        }

        log.info("[RETRY] 手动重试凭证: voucherNo={}, operator={}, reason={}",
            voucherNo, operatorName, retryReason);

        try {
            PostingExecuteRequest request = new PostingExecuteRequest();
            request.setVoucherNo(voucherNo);
            request.setOperatorName(operatorName);
            PostingExecuteResponse response = postingApplicationService.executePosting(request);

            return new RetryExecuteResult(
                voucherNo, true,
                response.getVoucherStatus(),
                response.getVoucherStatusDesc());
        } catch (Exception e) {
            // 重试失败：retryCount +1
            transactionTemplate.execute(status -> {
                AccountingVoucherPO v = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
                if (v != null) {
                    v.setRetryCount((v.getRetryCount() != null ? v.getRetryCount() : 0) + 1);
                    v.setFailReason(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                    accountingVoucherRepository.updateById(v);
                }
                return true;
            });
            throw e;
        }
    }

    /**
     * 凭证跳过
     */
    public void skipAbnormalVoucher(
        String voucherNo, String operatorName, String skipReason) {

        AccountingVoucherPO voucher = accountingVoucherRepository.selectByVoucherNoSimple(voucherNo);
        if (voucher == null) {
            throw new ServiceException(ResultCode.VOUCHER_NOT_FOUND, "凭证不存在: " + voucherNo);
        }

        VoucherStatusEnum currentStatus = voucher.getStatus();
        if (currentStatus != VoucherStatusEnum.FAILED && currentStatus != VoucherStatusEnum.POSTING) {
            throw new ServiceException(ResultCode.VOUCHER_STATUS_ILLEGAL,
                "凭证状态非法，仅允许失败/过账中的凭证跳过: " + voucherNo
                    + ", 当前状态=" + (currentStatus != null ? currentStatus.getDesc() : "null"));
        }

        // 独立事务更新
        transactionTemplate.execute(status -> {
            voucher.setSkipFlag(1);
            voucher.setFailReason("[人工跳过] operator=" + operatorName + ", reason=" + skipReason);
            // status 保持 4(过账失败)
            accountingVoucherRepository.updateById(voucher);
            return true;
        });

        log.warn("[SKIP-VOUCHER] 凭证被跳过: voucherNo={}, operator={}, reason={}",
            voucherNo, operatorName, skipReason);
    }

    // ==================== Private Helpers ====================

    private AbnormalVoucherData toAbnormalVoucherData(AccountingVoucherPO voucher) {
        return new AbnormalVoucherData(
            voucher.getVoucherNo(),
            voucher.getStatus() != null ? voucher.getStatus().getCode() : null,
            voucher.getStatus() != null ? voucher.getStatus().getDesc() : "未知",
            voucher.getAccountingDate(),
            voucher.getFailReason(),
            voucher.getRetryCount(),
            voucher.getSkipFlag(),
            voucher.getUpdateTime()
        );
    }

    private Long extractDuration(Map<String, Object> stats, String key) {
        if (stats == null || stats.get(key) == null) {
            return null;
        }
        Object val = stats.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        return null;
    }

    // ==================== Domain Data Objects ====================

    @Data
    @AllArgsConstructor
    public static class VoucherProgressData {
        private String voucherNo;
        private Integer status;
        private String statusDesc;
        private LocalDate accountingDate;
        private int totalEntries;
        private int realTimePosted;
        private int realTimeTotal;
        private int asyncPosted;
        private int asyncTotal;
        private int bufferPosted;
        private int bufferTotal;
        private BigDecimal progressPercent;
    }

    @Data
    @AllArgsConstructor
    public static class TransactionProgressData {
        private String txnNo;
        private Integer status;
        private String statusDesc;
        private LocalDate accountingDate;
        private int totalVouchers;
        private int postedVouchers;
        private int processingVouchers;
        private int failedVouchers;
        private BigDecimal progressPercent;
        private List<VoucherProgressData> voucherProgressList;
    }

    @Data
    @AllArgsConstructor
    public static class PostingStatsData {
        private String dimensionValue;
        private int totalCount;
        private int successCount;
        private int failedCount;
        private int processingCount;
        private int pendingCount;
        private BigDecimal successRate;
        private Long avgDurationMs;
        private Long maxDurationMs;
        private Long minDurationMs;
    }

    @Data
    @AllArgsConstructor
    public static class AbnormalVoucherData {
        private String voucherNo;
        private Integer status;
        private String statusDesc;
        private LocalDate accountingDate;
        private String failReason;
        private Integer retryCount;
        private Integer skipFlag;
        private LocalDateTime updateTime;
    }

    @Data
    @AllArgsConstructor
    public static class RetryExecuteResult {
        private String voucherNo;
        private boolean success;
        private Integer voucherStatus;
        private String voucherStatusDesc;
    }
}
