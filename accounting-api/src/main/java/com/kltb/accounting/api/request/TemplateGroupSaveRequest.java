package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 批量保存开户模板请求（主信息 + 多个科目账户明细）
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "批量保存开户模板请求")
public class TemplateGroupSaveRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 32, message = "模板名称长度不能超过32")
    @Schema(description = "模板名称", example = "芒好贷自营24贷款账户")
    private String templateName;

    @NotBlank(message = "业务线编码不能为空")
    @Size(max = 32, message = "业务线编码长度不能超过32")
    @Schema(description = "业务线编码", example = "LOAN_24")
    private String businessCode;

    @NotNull(message = "客户类型不能为空")
    @Schema(description = "客户类型：1-个人，2-企业，99-其他", example = "1")
    private Integer customerType;

    @NotNull(message = "是否自动开户不能为空")
    @Schema(description = "是否支持自动开户", example = "true")
    private Boolean autoOpen;

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "2")
    private Integer status;

    @NotEmpty(message = "开户模板必须至少包含一个会计科目账户")
    @Valid
    @Schema(description = "账户科目明细列表")
    private List<TemplateItemRequest> items;
}
