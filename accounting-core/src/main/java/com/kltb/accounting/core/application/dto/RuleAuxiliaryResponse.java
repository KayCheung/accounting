package com.kltb.accounting.core.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 辅助核算项响应 DTO
 */
@Getter
@Setter
@Schema(description = "辅助核算项响应")
public class RuleAuxiliaryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "辅助核算类型")
    private String auxType;

    @Schema(description = "辅助核算项目编码")
    private String auxCode;

    @Schema(description = "分摊方式：1-不分摊，2-固定金额，3-按比例")
    private Integer allocationMethod;

    @Schema(description = "分摊值")
    private BigDecimal allocationValue;

    @Schema(description = "SpEL扩展脚本")
    private String extendScript;
}
