package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 红冲响应 DTO
 */
@Data
@Builder
public class ReversalResponse {

    /** 原凭证号 */
    private String origVoucherNo;

    /** 红冲凭证号 */
    private String reversalVoucherNo;

    /** 交易类别编码（3=红） */
    private Integer tradeType;

    /** 红冲金额 */
    private BigDecimal amount;

    /** 红冲会计日期 */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;

    /** 红冲分录数 */
    private int entryCount;

    /** 红冲时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime reversaledAt;
}
