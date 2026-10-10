package com.kltb.accounting.core.infrastructure.persistence.bo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 辅助核算凭证分录记录 BO
 */
@Data
public class AuxiliaryEntryRecordBO {
    private Long id;
    private LocalDate accountingDate;
    private String voucherNo;
    private String entryId;
    private String summary;
    private String auxType;
    private String auxCode;
    private String auxName;
    private String subjectCode;
    private String subjectName;
    private Integer changeDirection;
    private Integer debitCredit;
    private BigDecimal amount;
}
