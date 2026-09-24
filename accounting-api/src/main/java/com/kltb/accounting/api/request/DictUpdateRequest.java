package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更新字典项请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DictUpdateRequest {

    @Schema(description = "字典项名称", maxLength = 64)
    private String dictName;

    @Schema(description = "字典项英文名称", maxLength = 64)
    private String dictNameEn;

    @Schema(description = "排序序号")
    private Integer sortOrder;

    @Schema(description = "分组键")
    private String groupKey;

    @Schema(description = "状态：1=启用，2=停用")
    private Integer status;

    @Schema(description = "扩展属性 JSON")
    private String extJson;
}
