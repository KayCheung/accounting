package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 业务记账流水记录列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "业务记账流水记录列表项")
public class JournalRecordItemResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "系统跟踪号", example = "TRC202610100001")
    private String traceNo;

    @Schema(description = "流水序号", example = "0")
    private Integer traceSeq;

    @Schema(description = "业务线编码", example = "LOAN")
    private String businessCode;

    @Schema(description = "交易编码", example = "DISBURSE")
    private String tradingCode;

    @Schema(description = "支付渠道", example = "BANK")
    private String payChannel;

    @Schema(description = "交易类别代码", example = "1")
    private Integer tradeType;

    @Schema(description = "交易类别描述", example = "正常")
    private String tradeTypeDesc;

    @Schema(description = "交易金额", example = "1000.00")
    private BigDecimal amount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "交易时间")
    private LocalDateTime tradeTime;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "摘要", example = "放款出金")
    private String summary;

    @Schema(description = "关联预冻结单号", example = "FRZ202610100001")
    private String origFreezeNo;

    @Schema(description = "处理状态代码", example = "2")
    private Integer status;

    @Schema(description = "处理状态描述", example = "成功")
    private String statusDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
