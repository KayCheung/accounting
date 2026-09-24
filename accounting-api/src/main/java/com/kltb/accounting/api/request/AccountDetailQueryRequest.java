package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 账户明细查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "账户明细查询请求")
public class AccountDetailQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "起始日期", example = "2026-06-01")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @Schema(description = "结束日期", example = "2026-06-12")
    private LocalDate endDate;

    @Schema(description = "交易类别：1-正常,2-调账,3-红冲,4-蓝冲", example = "1")
    private Integer tradeType;

    @Schema(description = "借贷方向：1-借方,2-贷方", example = "1")
    private Integer debitCredit;
}
