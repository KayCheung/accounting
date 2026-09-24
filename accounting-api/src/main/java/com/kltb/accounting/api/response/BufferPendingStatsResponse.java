package com.kltb.accounting.api.response;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 待入账统计响应 DTO
 */
@Data
public class BufferPendingStatsResponse {

    private String accountingDate;
    private Integer mode1Count;
    private BigDecimal mode1Amount;
    private Integer mode2Count;
    private BigDecimal mode2Amount;
    private Integer mode3Count;
    private BigDecimal mode3Amount;
    private Integer totalAccounts;
    private LocalDateTime oldestPendingTime;
}
