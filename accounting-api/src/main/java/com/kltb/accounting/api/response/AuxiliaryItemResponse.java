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
 * 辅助核算项目项响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算项目项响应 DTO")
public class AuxiliaryItemResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "辅助核算项目编码", example = "DEPT001")
    private String auxCode;

    @Schema(description = "辅助核算项目名称", example = "技术中心")
    private String auxName;

    @Schema(description = "凭证分录记录数", example = "42")
    private Long recordCount;

    @Schema(description = "累计发生总金额", example = "186000.00")
    private BigDecimal totalAmount;
}
