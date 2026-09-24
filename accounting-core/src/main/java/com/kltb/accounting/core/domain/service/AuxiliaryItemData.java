package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 辅助核算项领域数据对象
 */
@Data
@AllArgsConstructor
public class AuxiliaryItemData {
    private String entryId;
    private String voucherNo;
    private String subjectCode;
    private String auxType;
    private String auxCode;
    private String auxName;
    /** 1=增, 2=减 */
    private Integer changeDirection;
    private BigDecimal amount;
    private LocalDate accountingDate;
}
