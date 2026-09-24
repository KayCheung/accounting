package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建辅助核算项请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuxiliaryCreateRequest {

    @NotBlank(message = "辅助核算项类型不能为空")
    @Schema(description = "辅助核算项类型（字典CODE）", maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
    private String auxiliaryType;

    @NotNull(message = "是否必填不能为空")
    @Schema(description = "是否必填：true=必填，false=可选", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean required;

    @Schema(description = "默认辅助核算项目（字典CODE）")
    private String defaultAuxCode;
}
