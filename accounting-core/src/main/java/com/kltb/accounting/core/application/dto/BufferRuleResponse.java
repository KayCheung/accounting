package com.kltb.accounting.core.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 缓冲入账规则响应 DTO
 */
@Getter
@Setter
@Schema(description = "缓冲入账规则响应")
public class BufferRuleResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "缓冲模式：1-逐条，2-日间批量，3-日终批量")
    private Integer bufferMode;

    @Schema(description = "业务线编码")
    private String businessCode;

    @Schema(description = "交易编码")
    private String tradingCode;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "会计科目编码")
    private String subjectCode;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "借贷方向：1-借，2-贷")
    private Integer debitCredit;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "生效时间")
    private LocalDateTime effectiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "失效时间")
    private LocalDateTime expirationTime;

    @Schema(description = "状态：1-待启用，2-启用，3-停用")
    private Integer status;
}
