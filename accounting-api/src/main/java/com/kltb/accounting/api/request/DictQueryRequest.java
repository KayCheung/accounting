package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 字典分页查询请求
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class DictQueryRequest extends PageRequest {

    @Schema(description = "字典类型编码")
    private String dictType;

    @Schema(description = "状态：1=启用，2=停用")
    private Integer status;

    @Schema(description = "分组键")
    private String groupKey;
}
