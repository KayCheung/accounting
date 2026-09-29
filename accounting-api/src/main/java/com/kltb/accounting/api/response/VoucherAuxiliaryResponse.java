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
 * 凭证辅助核算项响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "凭证辅助核算项响应 DTO")
public class VoucherAuxiliaryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分录流水号", example = "ENT202609200000010001")
    private String entryId;

    @Schema(description = "会计科目编码", example = "1001")
    private String subjectCode;

    @Schema(description = "辅助核算类型（如 DEPT/CUSTOMER/PROJECT/SUPPLIER）", example = "CUSTOMER")
    private String auxType;

    @Schema(description = "辅助核算项目编码", example = "CUST001")
    private String auxCode;

    @Schema(description = "辅助核算项目名称", example = "张三")
    private String auxName;

    @Schema(description = "增减方向：1-增, 2-减", example = "1")
    private Integer changeDirection;

    @Schema(description = "金额", example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "会计日期", example = "2026-09-20")
    private LocalDate accountingDate;
}
