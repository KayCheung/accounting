package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 聚合余额响应
 */
@Data
@Schema(description = "聚合余额响应")
public class AggregateBalanceResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "账户名称")
    private String accountName;

    @Schema(description = "科目编码")
    private String subjectCode;

    @Schema(description = "主账户余额")
    private BigDecimal mainBalance;

    @Schema(description = "可用子账户余额")
    private BigDecimal availableBalance;

    @Schema(description = "冻结子账户余额")
    private BigDecimal frozenBalance;

    @Schema(description = "缓冲预估金额")
    private BigDecimal bufferEstimate;

    @Schema(description = "账户总余额 = 可用 + 冻结")
    private BigDecimal totalBalance;

    @Schema(description = "账户状态：1-正常,2-冻结,3-注销")
    private Integer status;

    @Schema(description = "账户状态描述")
    private String statusDesc;

    @Schema(description = "风控状态：1-正常,2-止入,3-止出,4-止入止出")
    private Integer riskStatus;

    @Schema(description = "风控状态描述")
    private String riskStatusDesc;

    @Schema(description = "余额方向：1-借方,2-贷方")
    private Integer balanceDirection;

    @Schema(description = "余额方向描述")
    private String balanceDirectionDesc;

    @Schema(description = "币种")
    private String currency;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "查询时间")
    private LocalDateTime queryTime;
}
