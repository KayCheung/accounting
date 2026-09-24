package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证生成结果响应 DTO
 */
@Data
@AllArgsConstructor
public class VoucherGenerateResponse {

    private String voucherNo;
    private String traceNo;
    private String txnNo;
    private String voucherType;
    private BigDecimal amount;
    private LocalDate accountingDate;
    private Integer status;
    private LocalDateTime tradeTime;
    private String summary;
    private List<VoucherEntryResponse> entries;
    /** 缓冲入账分录数量 */
    private Integer bufferedCount;
    /** 正常过账分录数量 */
    private Integer normalCount;
}
