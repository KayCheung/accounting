package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "期末结转执行记录分页查询请求")
public class TransferRecordQueryRequest extends PageRequest {

    @Schema(description = "开始会计日期")
    private LocalDate startDate;

    @Schema(description = "结束会计日期")
    private LocalDate endDate;

    @Schema(description = "结转流水号")
    private String transferNo;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "凭证号")
    private String voucherNo;

    @Schema(description = "状态：1-处理中, 2-成功, 3-失败")
    private Integer status;
}
