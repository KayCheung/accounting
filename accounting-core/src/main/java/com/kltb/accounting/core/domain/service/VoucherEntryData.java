package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证分录领域数据对象
 */
@Data
@AllArgsConstructor
public class VoucherEntryData {
    private String entryId;
    private String voucherNo;
    private Integer rowNum;
    private String subjectCode;
    private String accountNo;
    /** 1=借, 2=贷 */
    private Integer debitCredit;
    private BigDecimal amount;
    private String currency;
    private String summary;
    private LocalDate accountingDate;
    /** 来自规则明细 is_unilateral */
    private Boolean isUnilateral;
    /** 缓冲匹配后标记（Java-B 设置） */
    private Boolean isBuffered;
}
