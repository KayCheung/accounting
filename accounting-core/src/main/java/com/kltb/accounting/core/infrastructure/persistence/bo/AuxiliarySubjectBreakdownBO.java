package com.kltb.accounting.core.infrastructure.persistence.bo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 辅助核算科目分布明细 BO
 */
@Data
public class AuxiliarySubjectBreakdownBO {
    private String subjectCode;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private BigDecimal totalAmount;
    private Integer entryCount;
}
