package com.kltb.accounting.core.infrastructure.persistence.bo;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 辅助核算交叉矩阵单元格金额 BO
 */
@Data
public class AuxiliaryMatrixCellBO {
    private String subjectCode;
    private String auxCode;
    private BigDecimal amount;
}
