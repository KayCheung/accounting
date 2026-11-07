package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更新辅助核算项请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuxiliaryUpdateRequest {

    @Schema(description = "是否必填：true=必填，false=可选")
    private Boolean required;

    @Schema(description = "默认辅助核算项目（字典CODE）")
    private String defaultAuxCode;
}
