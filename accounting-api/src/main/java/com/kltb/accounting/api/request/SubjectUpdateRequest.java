package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更新科目请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubjectUpdateRequest {

    @Schema(description = "科目名称", maxLength = 64)
    private String subjectName;

    @Schema(description = "账类：0=表外，1=资产，2=负债，3=权益，4=共同，5=成本，6=损益")
    private Integer subjectCategory;

    @Schema(description = "科目性质：1=非特殊，2=销账，3=贷款，4=现金")
    private Integer nature;

    @Schema(description = "是否末级科目")
    private Boolean leaf;

    @Schema(description = "是否允许记账")
    private Boolean allowPost;

    @Schema(description = "是否允许建明细账户")
    private Boolean allowOpenAccount;

    @Schema(description = "状态：1=启用，2=停用")
    private Integer status;
}
