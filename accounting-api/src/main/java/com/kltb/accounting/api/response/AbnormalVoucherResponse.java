package com.kltb.accounting.api.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 异常凭证响应 DTO
 */
@Data
public class AbnormalVoucherResponse {

    private String voucherNo;
    private Integer status;
    private String statusDesc;
    private LocalDate accountingDate;
    private String failReason;
    private Integer retryCount;
    private Integer skipFlag;
    private LocalDateTime updateTime;
}
