package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 辅助核算凭证分录记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算凭证分录记录响应 DTO")
public class AuxiliaryEntryRecordResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1001")
    private Long id;

    @Schema(description = "会计日期", example = "2026-03-01")
    private LocalDate accountingDate;

    @Schema(description = "记账凭证号", example = "VOU20260301000001")
    private String voucherNo;

    @Schema(description = "财务凭证字号", example = "记-2026003-00001")
    private String voucherWord;

    @Schema(description = "分录流水号", example = "ENT202603010000010001")
    private String entryId;

    @Schema(description = "分录摘要", example = "发放3月销售团队薪酬")
    private String summary;

    @Schema(description = "辅助核算类型编码", example = "DEPARTMENT")
    private String auxType;

    @Schema(description = "辅助核算项目编码", example = "DEPT001")
    private String auxCode;

    @Schema(description = "辅助核算项目名称", example = "销售中心")
    private String auxName;

    @Schema(description = "科目编码", example = "660101")
    private String subjectCode;

    @Schema(description = "科目名称", example = "职工薪酬")
    private String subjectName;

    @Schema(description = "增减方向：1-增, 2-减", example = "1")
    private Integer changeDirection;

    @Schema(description = "增减方向描述", example = "增加")
    private String changeDirectionDesc;

    @Schema(description = "借贷方向：1-借, 2-贷", example = "1")
    private Integer debitCredit;

    @Schema(description = "借贷方向描述", example = "借")
    private String debitCreditDesc;

    @Schema(description = "借方发生额", example = "186000.00")
    private BigDecimal debitAmount;

    @Schema(description = "贷方发生额", example = "0.00")
    private BigDecimal creditAmount;

    @Schema(description = "核算发生金额", example = "186000.00")
    private BigDecimal amount;
}
