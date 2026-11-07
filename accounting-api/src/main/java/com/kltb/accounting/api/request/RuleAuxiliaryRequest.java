package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 辅助核算项请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "辅助核算项")
public class RuleAuxiliaryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "辅助核算类型不能为空")
    @Size(max = 32, message = "辅助核算类型长度不能超过32")
    @Schema(description = "辅助核算类型", example = "DEPT")
    private String auxType;

    @NotBlank(message = "辅助核算编码不能为空")
    @Size(max = 32, message = "辅助核算编码长度不能超过32")
    @Schema(description = "辅助核算项目编码", example = "DEPT_001")
    private String auxCode;

    @NotNull(message = "分摊方式不能为空")
    @Schema(description = "分摊方式：1-不分摊，2-固定金额，3-按比例", example = "1")
    private Integer allocationMethod;

    @Schema(description = "分摊值", example = "0")
    private BigDecimal allocationValue;

    @Size(max = 2000, message = "扩展脚本长度不能超过2000")
    @Schema(description = "SpEL扩展脚本")
    private String extendScript;
}
