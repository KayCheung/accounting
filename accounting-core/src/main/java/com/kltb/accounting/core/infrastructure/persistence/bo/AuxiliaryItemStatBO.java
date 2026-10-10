package com.kltb.accounting.core.infrastructure.persistence.bo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 辅助核算项汇总统计 BO
 */
@Data
public class AuxiliaryItemStatBO {
    private String auxCode;
    private String auxName;
    private Long recordCount;
    private BigDecimal totalAmount;
}
