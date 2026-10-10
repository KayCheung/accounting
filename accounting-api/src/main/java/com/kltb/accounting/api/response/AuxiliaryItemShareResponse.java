package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 辅助核算项金额与占比响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算项金额与占比响应 DTO")
public class AuxiliaryItemShareResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "辅助核算项目编码", example = "DEPT001")
    private String auxCode;

    @Schema(description = "辅助核算项目名称", example = "技术中心")
    private String auxName;

    @Schema(description = "发生金额", example = "186000.00")
    private BigDecimal amount;

    @Schema(description = "占比百分比(%)", example = "21.12")
    private BigDecimal percentage;
}
