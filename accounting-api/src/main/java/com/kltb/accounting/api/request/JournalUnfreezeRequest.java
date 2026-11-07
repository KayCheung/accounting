// accounting-api/src/main/java/com/kltb/accounting/api/request/JournalUnfreezeRequest.java
package com.kltb.accounting.api.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务预冻结全额解冻（撤销）请求 DTO
 * <p>
 * 严格全额解冻，严禁部分解冻。根据原预冻结流水跟踪号 origTraceNo 执行全额撤销与资金释放。
 */
@Data
public class JournalUnfreezeRequest {

    /** 本次解冻撤销请求的系统跟踪号（入口幂等防重） */
    @NotBlank(message = "traceNo不能为空")
    @Size(max = 64, message = "traceNo长度不能超过64")
    private String traceNo;

    /** 预留序列号 */
    @Min(value = 0, message = "traceSeq必须>=0")
    private Integer traceSeq = 0;

    /** 原预冻结业务流水跟踪号 */
    @NotBlank(message = "origTraceNo不能为空")
    @Size(max = 64, message = "origTraceNo长度不能超过64")
    private String origTraceNo;

    /** 解冻/撤销原因 */
    @Size(max = 64, message = "reason长度不能超过64")
    private String reason;
}
