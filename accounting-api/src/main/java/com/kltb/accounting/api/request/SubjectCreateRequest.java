package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 创建科目请求
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SubjectCreateRequest {

    @NotBlank(message = "科目编码不能为空")
    @Schema(description = "科目编码", maxLength = 32, requiredMode = Schema.RequiredMode.REQUIRED)
    private String subjectCode;

    @NotBlank(message = "科目名称不能为空")
    @Schema(description = "科目名称", maxLength = 64, requiredMode = Schema.RequiredMode.REQUIRED)
    private String subjectName;

    @NotNull(message = "科目级别不能为空")
    @Schema(description = "科目级别", minimum = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer subjectLevel;

    @NotNull(message = "父科目ID不能为空")
    @Schema(description = "父科目ID，顶级科目传0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parentSubjectId;

    @NotNull(message = "账类不能为空")
    @Schema(description = "账类：0=表外，1=资产，2=负债，3=权益，4=共同，5=成本，6=损益", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer subjectCategory;

    @Schema(description = "余额方向：0=无(表外)，1=借，2=贷，3=双向")
    private Integer debitCredit;

    @Schema(description = "是否末级科目", defaultValue = "false")
    private Boolean leaf;

    @Schema(description = "是否允许记账", defaultValue = "false")
    private Boolean allowPost;

    @Schema(description = "是否允许建明细账户", defaultValue = "false")
    private Boolean allowOpenAccount;

    @Schema(description = "状态：1=启用，2=停用", defaultValue = "1")
    private Integer status;
}
