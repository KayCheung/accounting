package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 资金冻结记录综合分页查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "资金冻结记录综合分页查询请求")
public class FreezePageQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @Schema(description = "冻结编号", example = "FRZ20260611000001")
    private String freezeId;

    @Schema(description = "冻结状态：1-冻结, 2-已解冻", example = "1")
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "起始日期", example = "2026-06-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "结束日期", example = "2026-06-30")
    private LocalDate endDate;
}
