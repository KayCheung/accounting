package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 科目分页查询请求
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SubjectQueryRequest extends PageRequest {

    @Schema(description = "账类：0=表外，1=资产，2=负债，3=权益，4=共同，5=成本，6=损益")
    private Integer subjectCategory;

    @Schema(description = "状态：1=启用，2=停用")
    private Integer status;

    @Schema(description = "是否末级科目")
    private Boolean leaf;
}
