package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缓冲记账明细领域数据对象
 */
@Data
@AllArgsConstructor
public class BufferPostingDetailData {
    private Long ruleId;
    private Integer bufferMode;
    private String voucherNo;
    private String entryId;
    private String txnNo;
    private String traceNo;
    private Integer traceSeq;
    private String businessCode;
    private String tradingCode;
    private String payChannel;
    private Integer tradeType;
    private LocalDateTime tradeTime;
    private String accountNo;
    private Integer debitCredit;
    private String currency;
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String summary;
    private Long sharding;
}
