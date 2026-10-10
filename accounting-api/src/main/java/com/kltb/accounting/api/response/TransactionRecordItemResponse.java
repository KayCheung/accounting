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
 * 账务事务记录列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "账务事务记录列表项")
public class TransactionRecordItemResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "事务编号", example = "TXN202610100001")
    private String txnNo;

    @Schema(description = "系统跟踪号", example = "TRC202610100001")
    private String traceNo;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "会计日期")
    private LocalDate accountingDate;

    @Schema(description = "关联账户数", example = "2")
    private Integer relateAccountCount;

    @Schema(description = "事务金额", example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "事务状态代码: 1-处理中, 2-成功, 3-失败, 4-已冲销", example = "2")
    private Integer status;

    @Schema(description = "事务状态描述", example = "成功")
    private String statusDesc;

    @Schema(description = "失败原因")
    private String failReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "完成时间")
    private LocalDateTime finishTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
