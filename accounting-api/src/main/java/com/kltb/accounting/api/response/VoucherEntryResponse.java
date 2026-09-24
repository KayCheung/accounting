package com.kltb.accounting.api.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分录行响应 DTO
 */
@Data
@AllArgsConstructor
public class VoucherEntryResponse {

    private String entryId;
    private Integer rowNum;
    private String subjectCode;
    private String accountNo;
    private Integer debitCredit;
    private BigDecimal amount;
    private String currency;
    private String summary;
    private Boolean isUnilateral;
    private Boolean isBuffered;
    private LocalDate accountingDate;
}
