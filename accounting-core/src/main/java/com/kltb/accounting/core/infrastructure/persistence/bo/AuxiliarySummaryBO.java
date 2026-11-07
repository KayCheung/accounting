package com.kltb.accounting.core.infrastructure.persistence.bo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 辅助核算账簿统计 KPI BO
 */
@Data
public class AuxiliarySummaryBO {
    private BigDecimal totalAmount;
    private BigDecimal debitAmount;
    private BigDecimal creditAmount;
    private Integer itemCount;
    private Integer subjectCount;
    private Integer voucherCount;
}
