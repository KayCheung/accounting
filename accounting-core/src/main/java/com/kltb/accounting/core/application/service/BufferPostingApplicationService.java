package com.kltb.accounting.core.application.service;

import cn.hutool.core.util.StrUtil;
import com.kltb.accounting.api.request.BufferExecuteRequest;
import com.kltb.accounting.api.response.BufferExecuteResponse;
import com.kltb.accounting.api.response.BufferMonitorResponse;
import com.kltb.accounting.api.response.BufferPendingStatsResponse;
import com.kltb.accounting.core.application.assembler.BufferPostingAssembler;
import com.kltb.accounting.core.domain.enums.BufferModeEnum;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.kltb.accounting.core.domain.service.RunningBalanceValidator;
import com.kltb.accounting.core.domain.service.RunningBalanceValidator.ValidationResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import com.kltb.accounting.core.infrastructure.persistence.repository.BufferPostingDetailRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 缓冲记账应用服务（编排层）
 * <p>
 * 职责：参数校验、DTO 转换、委托领域服务，不含业务逻辑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BufferPostingApplicationService {

    private static final int MAX_QUERY_LIMIT = 10000;

    private final BufferPostingEngineDomainService bufferPostingEngineDomainService;
    private final BufferPostingDetailRepository bufferPostingDetailRepository;
    private final RunningBalanceValidator runningBalanceValidator;
    private final BufferPostingAssembler assembler;

    /**
     * 手动触发缓冲记账
     */
    public BufferExecuteResponse executePosting(BufferExecuteRequest request) {
        LocalDate accountingDate = request.getAccountingDate();
        Integer bufferMode = request.getBufferMode();
        int maxBatchSize = request.getMaxBatchSize() != null ? request.getMaxBatchSize() : 50;

        BatchPostingResult result;

        // 指定了账户 → 仅处理该账户
        if (StrUtil.isNotBlank(request.getAccountNo())) {
            result = executeForAccount(accountingDate, bufferMode, maxBatchSize, request.getAccountNo());
        } else {
            // 不指定账户 → 按模式执行
            result = executeByMode(accountingDate, bufferMode, maxBatchSize);
        }

        return assembler.toExecuteResponse(result);
    }

    /**
     * 按模式执行
     */
    private BatchPostingResult executeByMode(LocalDate accountingDate, Integer bufferMode, int maxBatchSize) {
        if (bufferMode == null) {
            // 处理全部模式
            BatchPostingResult total = new BatchPostingResult();
            for (BufferModeEnum mode : BufferModeEnum.values()) {
                BatchPostingResult r = executeSingleMode(accountingDate, mode, maxBatchSize);
                total.setTotalCount(total.getTotalCount() + r.getTotalCount());
                total.setSuccessCount(total.getSuccessCount() + r.getSuccessCount());
                total.setFailedCount(total.getFailedCount() + r.getFailedCount());
                total.getFailedList().addAll(r.getFailedList());
            }
            return total;
        }

        BufferModeEnum mode = BufferModeEnum.fromCode(bufferMode);
        if (mode == null) {
            throw new IllegalArgumentException("无效的缓冲模式: " + bufferMode);
        }

        return executeSingleMode(accountingDate, mode, maxBatchSize);
    }

    /**
     * 执行单个模式
     */
    private BatchPostingResult executeSingleMode(LocalDate accountingDate, BufferModeEnum mode, int maxBatchSize) {
        switch (mode) {
            case ASYNC_SINGLE:
                return bufferPostingEngineDomainService.executeSinglePosting(accountingDate, maxBatchSize);
            case DAILY_BATCH:
                return bufferPostingEngineDomainService.executeBatchPosting(accountingDate, maxBatchSize);
            case EOD_BATCH:
                return bufferPostingEngineDomainService.executeSinglePosting(accountingDate, maxBatchSize);
            default:
                throw new IllegalArgumentException("不支持的缓冲模式: " + mode);
        }
    }

    /**
     * 按账户执行
     */
    private BatchPostingResult executeForAccount(
            LocalDate accountingDate, Integer bufferMode, int maxBatchSize, String accountNo) {

        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, bufferMode, BufferStatusEnum.PENDING.getCode(), maxBatchSize);

        // 过滤指定账户
        details = details.stream()
                .filter(d -> d.getAccountNo().equals(accountNo))
                .collect(Collectors.toList());

        BatchPostingResult result = new BatchPostingResult();
        result.setTotalCount(details.size());

        for (BufferPostingDetailPO detail : details) {
            try {
                bufferPostingEngineDomainService.processSingleDetail(detail);
                result.setSuccessCount(result.getSuccessCount() + 1);
            } catch (Exception e) {
                log.error("[BUFFER-POSTING] 缓冲入账异常", e);
                result.setFailedCount(result.getFailedCount() + 1);
                result.getFailedList().add(new BufferPostingEngineDomainService.FailedItemInfo(
                        detail.getId(), detail.getAccountNo(), detail.getAmount(), e.getMessage()));
            }
        }

        return result;
    }

    /**
     * 查询待入账统计信息
     */
    public BufferPendingStatsResponse getPendingStats(LocalDate accountingDate) {
        BufferPendingStatsResponse response = new BufferPendingStatsResponse();
        response.setAccountingDate(accountingDate.toString());

        // 按模式查询统计
        for (BufferModeEnum mode : BufferModeEnum.values()) {
            List<BufferPostingDetailPO> pending = bufferPostingDetailRepository.selectPendingByCondition(
                    accountingDate, mode.getCode(), BufferStatusEnum.PENDING.getCode(), MAX_QUERY_LIMIT);

            int count = pending.size();
            BigDecimal amount = pending.stream()
                    .map(BufferPostingDetailPO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            switch (mode) {
                case ASYNC_SINGLE:
                    response.setMode1Count(count);
                    response.setMode1Amount(amount);
                    break;
                case DAILY_BATCH:
                    response.setMode2Count(count);
                    response.setMode2Amount(amount);
                    break;
                case EOD_BATCH:
                    response.setMode3Count(count);
                    response.setMode3Amount(amount);
                    break;
            }
        }

        // 查询最早待入账时间
        List<BufferPostingDetailPO> allPending = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, null, BufferStatusEnum.PENDING.getCode(), 1);
        if (!allPending.isEmpty()) {
            response.setOldestPendingTime(allPending.get(0).getCreateTime());
        }

        return response;
    }

    /**
     * 查询缓冲监控数据
     */
    public BufferMonitorResponse getMonitorData(LocalDate accountingDate, Integer status) {
        List<BufferPostingDetailPO> details = bufferPostingDetailRepository.selectPendingByCondition(
                accountingDate, null, status, MAX_QUERY_LIMIT);

        BufferMonitorResponse response = assembler.toMonitorResponse(details);

        // P2-4: 填充 Running Balance 告警
        if (status == null || BufferStatusEnum.SUCCESS.getCode().equals(status)) {
            Set<String> accountNos = details.stream()
                    .map(BufferPostingDetailPO::getAccountNo)
                    .collect(Collectors.toSet());
            List<BufferMonitorResponse.BalanceAlertInfo> alerts = new ArrayList<>();
            for (String accountNo : accountNos) {
                ValidationResult vr = runningBalanceValidator.validateRunningBalance(accountNo, accountingDate);
                if (vr.isAlert()) {
                    BufferMonitorResponse.BalanceAlertInfo info = new BufferMonitorResponse.BalanceAlertInfo();
                    info.setAccountNo(accountNo);
                    info.setActualBalance(vr.getActualBalance());
                    info.setCalculatedBalance(vr.getCalculatedBalance());
                    info.setDiff(vr.getDiff());
                    alerts.add(info);
                }
            }
            response.setRunningBalanceAlerts(alerts);
        }

        return response;
    }
}
