// accounting-core/src/main/java/com/kltb/accounting/core/domain/service/JournalSubmitResult.java
package com.kltb.accounting.core.domain.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;

/**
 * 流水入库领域层结果对象（S2 修复）
 * Application Service 通过 Assembler 将此转换为 JournalSubmitResponse DTO
 */
@Data
@AllArgsConstructor
public class JournalSubmitResult {

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 事务编号 */
    private String txnNo;
}
