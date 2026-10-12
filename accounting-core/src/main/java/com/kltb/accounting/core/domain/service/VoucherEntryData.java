package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 凭证分录领域数据对象
 */
@Data
@NoArgsConstructor
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
    /** 增减方向：1=增, 2=减 */
    private Integer changeDirection;

    public VoucherEntryData(String entryId, String voucherNo, Integer rowNum, String subjectCode,
                            String accountNo, Integer debitCredit, BigDecimal amount, String currency,
                            String summary, LocalDate accountingDate, Boolean isUnilateral, Boolean isBuffered) {
        this.entryId = entryId;
        this.voucherNo = voucherNo;
        this.rowNum = rowNum;
        this.subjectCode = subjectCode;
        this.accountNo = accountNo;
        this.debitCredit = debitCredit;
        this.amount = amount;
        this.currency = currency;
        this.summary = summary;
        this.accountingDate = accountingDate;
        this.isUnilateral = isUnilateral;
        this.isBuffered = isBuffered;
        this.changeDirection = null;
    }
}

