package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 凭证生成结果响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherGenerateResponse {

    private String voucherNo;
    private String traceNo;
    private String txnNo;
    private String voucherType;
    private BigDecimal amount;
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;
    private Integer status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime tradeTime;
    private String summary;
    private List<VoucherEntryResponse> entries;
    /** 缓冲入账分录数量 */
    private Integer bufferedCount;
    /** 正常过账分录数量 */
    private Integer normalCount;
}
