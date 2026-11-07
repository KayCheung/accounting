package com.kltb.accounting.core.infrastructure.messaging;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 异步过账 MQ 消息体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostingMessagePayload {
    private String voucherNo;
    private String entryId;
    private String accountNo;
    private String subjectCode;
    private Integer debitCredit;        // 1=借, 2=贷
    private Integer changeDirection;    // 1=增, 2=减
    private BigDecimal amount;
    private LocalDate accountingDate;
    private String currency;
    private String summary;
}
