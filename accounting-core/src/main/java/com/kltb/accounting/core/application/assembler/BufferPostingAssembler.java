package com.kltb.accounting.core.application.assembler;

import com.kltb.accounting.api.response.BufferExecuteResponse;
import com.kltb.accounting.api.response.BufferMonitorResponse;
import com.kltb.accounting.api.response.BufferPendingStatsResponse;
import com.kltb.accounting.core.domain.enums.BufferStatusEnum;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService;
import com.kltb.accounting.core.domain.service.BufferPostingEngineDomainService.BatchPostingResult;
import com.kltb.accounting.core.infrastructure.persistence.entity.BufferPostingDetailPO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 缓冲记账 PO/Result → DTO 转换器
 */
@Component
public class BufferPostingAssembler {

    /**
     * 批量过账结果 → Execute Response
     */
    public BufferExecuteResponse toExecuteResponse(BatchPostingResult result) {
        BufferExecuteResponse response = new BufferExecuteResponse();
        response.setTotalCount(result.getTotalCount());
        response.setSuccessCount(result.getSuccessCount());
        response.setFailedCount(result.getFailedCount());
        response.setDurationMs(result.getTotalDurationMs());

        List<BufferExecuteResponse.BufferFailedItem> failedItems = result.getFailedList().stream()
                .map(f -> new BufferExecuteResponse.BufferFailedItem(
                        f.getDetailId(), f.getAccountNo(), f.getAmount(), f.getFailReason()))
                .collect(Collectors.toList());
        response.setFailedList(failedItems);

        return response;
    }

    /**
     * 明细列表 → Monitor Response
     */
    public BufferMonitorResponse toMonitorResponse(List<BufferPostingDetailPO> details) {
        BufferMonitorResponse response = new BufferMonitorResponse();
        response.setTotalRecords(details.size());

        // 状态分布统计
        Map<Integer, List<BufferPostingDetailPO>> byStatus = details.stream()
                .collect(Collectors.groupingBy(d -> Optional.ofNullable(d.getStatus()).map(BufferStatusEnum::getCode).orElse(0)));

        List<BufferMonitorResponse.BufferStatusCount> statusBreakdown = new ArrayList<>();
        for (Map.Entry<Integer, List<BufferPostingDetailPO>> entry : byStatus.entrySet()) {
            Integer statusCode = entry.getKey();
            List<BufferPostingDetailPO> group = entry.getValue();
            BigDecimal amount = group.stream()
                    .map(BufferPostingDetailPO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BufferMonitorResponse.BufferStatusCount sc = new BufferMonitorResponse.BufferStatusCount();
            sc.setStatus(statusCode);
            sc.setStatusDesc(getStatusDesc(statusCode));
            sc.setCount(group.size());
            sc.setAmount(amount);
            statusBreakdown.add(sc);
        }
        response.setStatusBreakdown(statusBreakdown);

        // 失败账户 Top
        Map<String, List<BufferPostingDetailPO>> failedByAccount = details.stream()
                .filter(d -> d.getStatus() == BufferStatusEnum.FAILED)
                .collect(Collectors.groupingBy(BufferPostingDetailPO::getAccountNo));

        List<BufferMonitorResponse.FailedAccountInfo> failedTopAccounts = failedByAccount.entrySet().stream()
                .map(entry -> {
                    BufferMonitorResponse.FailedAccountInfo info = new BufferMonitorResponse.FailedAccountInfo();
                    info.setAccountNo(entry.getKey());
                    info.setFailedCount(entry.getValue().size());
                    info.setTotalAmount(entry.getValue().stream()
                            .map(BufferPostingDetailPO::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
                    return info;
                })
                .sorted((a, b) -> b.getFailedCount().compareTo(a.getFailedCount()))
                .limit(10)
                .collect(Collectors.toList());
        response.setFailedTopAccounts(failedTopAccounts);

        // Running Balance 告警（预留，由 RunningBalanceValidator 填充）
        response.setRunningBalanceAlerts(new ArrayList<>());

        return response;
    }

    private String getStatusDesc(Integer code) {
        if (code == null) return "未知";
        BufferStatusEnum status = BufferStatusEnum.fromCode(code);
        return Optional.ofNullable(status).map(BufferStatusEnum::getDesc).orElse("未知");
    }
}
