package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "科目明细账查询请求")
public class SubsidiaryLedgerQueryRequest implements Serializable {

    @NotBlank(message = "科目编码不能为空")
    @Schema(description = "会计科目编码（如 1002 银行存款）", example = "1002")
    private String subjectCode;

    @Schema(description = "账户编号（可选，若不指定则归集该科目下所有分户）")
    private String accountNo;

    @NotNull(message = "开始日期不能为空")
    @Schema(description = "会计期间起始日期", example = "2026-03-01")
    private LocalDate startDate;

    @NotNull(message = "结束日期不能为空")
    @Schema(description = "会计期间截止日期", example = "2026-03-31")
    private LocalDate endDate;

    @Schema(description = "摘要关键字模糊检索")
    private String summaryKeyword;

    @Schema(description = "最小发生金额")
    private BigDecimal minAmount;

    @Schema(description = "最大发生金额")
    private BigDecimal maxAmount;
}
