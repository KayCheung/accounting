package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 业务流水记录分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "业务流水记录分页查询请求")
public class JournalPageQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "系统跟踪号(支持模糊匹配)", example = "TRC202610100001")
    private String traceNo;

    @Schema(description = "业务线编码", example = "LOAN")
    private String businessCode;

    @Schema(description = "交易类型: 1-正常, 2-调账, 3-红冲, 4-蓝补, 5-预冻结, 6-预解冻", example = "1")
    private Integer tradeType;

    @Schema(description = "状态: 1-处理中, 2-成功, 3-失败, 4-已冲正", example = "2")
    private Integer status;

    @Schema(description = "关联预冻结单号", example = "FRZ202610100001")
    private String origFreezeNo;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期起始", example = "2026-10-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期截止", example = "2026-10-31")
    private LocalDate endDate;
}
