package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 缓冲记账明细响应DTO
 */
@Data
@Accessors(chain = true)
@Schema(description = "缓冲记账明细响应")
public class BufferDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "明细ID")
    private Long id;

    @Schema(description = "缓冲入账规则ID")
    private Long ruleId;

    @Schema(description = "缓冲入账模式：1-异步逐条，2-日间批量，3-日终批量汇总")
    private Integer bufferMode;

    @Schema(description = "缓冲入账模式描述")
    private String bufferModeDesc;

    @Schema(description = "凭证号")
    private String voucherNo;

    @Schema(description = "分录流水号")
    private String entryId;

    @Schema(description = "事务编号")
    private String txnNo;

    @Schema(description = "系统跟踪号")
    private String traceNo;

    @Schema(description = "跟踪序号")
    private Integer traceSeq;

    @Schema(description = "业务线编码")
    private String businessCode;

    @Schema(description = "交易编码")
    private String tradingCode;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "交易类别：1-正常，2-调账，3-红，4-蓝")
    private Integer tradeType;

    @Schema(description = "交易类别描述")
    private String tradeTypeDesc;

    @Schema(description = "交易时间")
    private LocalDateTime tradeTime;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "借贷方向：1-借，2-贷")
    private Integer debitCredit;

    @Schema(description = "借贷方向描述")
    private String debitCreditDesc;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "金额")
    private BigDecimal amount;

    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "缓冲状态：1-待入账，2-处理中，3-成功，4-失败")
    private Integer status;

    @Schema(description = "缓冲状态描述")
    private String statusDesc;

    @Schema(description = "重试次数")
    private Integer retryCount;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "执行开始时间")
    private LocalDateTime startTime;

    @Schema(description = "完成时间")
    private LocalDateTime completeTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
