package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.time.LocalDate;

/**
 * 分页查询缓冲记账明细请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页查询缓冲记账明细请求")
public class BufferDetailQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "会计日期 yyyy-MM-dd", example = "2026-10-13")
    private LocalDate accountingDate;

    @Schema(description = "账户编号", example = "10010001")
    private String accountNo;

    @Schema(description = "凭证编号", example = "V202610130001")
    private String voucherNo;

    @Schema(description = "缓冲模式：1-异步逐条，2-日间批量，3-日终批量汇总", example = "1")
    private Integer bufferMode;

    @Schema(description = "缓冲状态：1-待入账，2-处理中，3-成功，4-失败", example = "1")
    private Integer status;

    @Schema(description = "交易开始日期", example = "2026-10-01")
    private LocalDate startDate;

    @Schema(description = "交易结束日期", example = "2026-10-13")
    private LocalDate endDate;
}
