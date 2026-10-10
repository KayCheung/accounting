package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 账务事务记录分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "账务事务记录分页查询请求")
public class TransactionPageQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "事务编号(支持模糊匹配)", example = "TXN202610100001")
    private String txnNo;

    @Schema(description = "系统跟踪号(支持模糊匹配)", example = "TRC202610100001")
    private String traceNo;

    @Schema(description = "事务状态: 1-处理中, 2-成功, 3-失败, 4-已冲销", example = "2")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期起始", example = "2026-10-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期截止", example = "2026-10-31")
    private LocalDate endDate;
}
