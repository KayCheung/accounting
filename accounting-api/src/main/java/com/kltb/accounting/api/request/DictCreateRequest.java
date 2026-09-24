package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建字典项请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DictCreateRequest {

    @NotBlank(message = "字典类型不能为空")
    @Schema(description = "字典类型编码", maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictType;

    @NotBlank(message = "字典编码不能为空")
    @Schema(description = "字典项编码", maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictCode;

    @NotBlank(message = "字典名称不能为空")
    @Schema(description = "字典项名称", maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
    private String dictName;

    @Schema(description = "字典项英文名称", maxLength = 64)
    private String dictNameEn;

    @Schema(description = "排序序号", defaultValue = "0")
    private Integer sortOrder;

    @Schema(description = "分组键")
    private String groupKey;

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：1=启用，2=停用", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer status;

    @Schema(description = "扩展属性 JSON")
    private String extJson;
}
