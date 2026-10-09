package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Schema(description = "期末结转历史记录响应")
public class TransferRecordResponse {

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "结转流水号")
    private String transferNo;

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "结转类型：1-损益结转, 2-成本结转, 3-自定义结转")
    private Integer transferType;

    @Schema(description = "结转类型描述")
    private String transferTypeDesc;

    @Schema(description = "规则编码")
    private String ruleCode;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "生成的凭证号")
    private String voucherNo;

    @Schema(description = "结转总金额")
    private BigDecimal totalAmount;

    @Schema(description = "执行状态：1-处理中, 2-成功, 3-失败")
    private Integer status;

    @Schema(description = "执行状态描述")
    private String statusDesc;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "执行时间")
    private LocalDateTime executeTime;

    @Schema(description = "完成时间")
    private LocalDateTime finishTime;
}
