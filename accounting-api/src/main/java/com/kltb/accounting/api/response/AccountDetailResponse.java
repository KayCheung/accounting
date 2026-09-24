package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 账户明细响应
 */
@Data
@Schema(description = "账户明细响应")
public class AccountDetailResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证号")
    private String voucherNo;

    @Schema(description = "分录流水号")
    private String entryId;

    @Schema(description = "事务编号")
    private String txnNo;

    @Schema(description = "系统跟踪号")
    private String traceNo;

    @Schema(description = "科目编码")
    private String subjectCode;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "业务线编码")
    private String businessCode;

    @Schema(description = "交易编码")
    private String tradingCode;

    @Schema(description = "交易类别：1-正常,2-调账,3-红冲,4-蓝冲")
    private Integer tradeType;

    @Schema(description = "交易类别描述")
    private String tradeTypeDesc;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "交易时间")
    private LocalDateTime tradeTime;

    @Schema(description = "借贷方向：1-借方,2-贷方")
    private Integer debitCredit;

    @Schema(description = "借贷方向描述")
    private String debitCreditDesc;

    @Schema(description = "增减方向：1-增加,2-减少")
    private Integer changeDirection;

    @Schema(description = "增减方向描述")
    private String changeDirectionDesc;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "交易前余额")
    private BigDecimal preBalance;

    @Schema(description = "交易金额")
    private BigDecimal amount;

    @Schema(description = "交易后余额")
    private BigDecimal postBalance;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "摘要")
    private String summary;
}
