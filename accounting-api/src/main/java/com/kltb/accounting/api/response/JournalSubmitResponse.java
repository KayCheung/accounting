// accounting-api/src/main/java/com/kltb/accounting/api/response/JournalSubmitResponse.java
package com.kltb.accounting.api.response;

import lombok.Data;

import java.time.LocalDate;

/**
 * 记账流水提交响应 DTO
 */
@Data
public class JournalSubmitResponse {

    /** 系统跟踪号 */
    private String traceNo;

    /** 会计日期 */
    private LocalDate accountingDate;

    /** 事务编号（Step 9 生成） */
    private String txnNo;

    /** 是否需要凭证生成（true 表示需要 Step 10 继续处理） */
    private Boolean needVouchering;
}
