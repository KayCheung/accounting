package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

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
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private LocalDate accountingDate;
    private String failReason;
    private Integer retryCount;
    private Integer skipFlag;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}
