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

/**
 * 外部客户按模板批量开立账户请求 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "按模板批量开立客户账户请求（单客户多科目账户批量生成）")
public class AccountTemplateBatchOpenRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "业务线编码不能为空")
    @Size(max = 32, message = "业务线编码长度不能超过32")
    @Schema(description = "业务线编码", example = "LOAN")
    private String businessCode;

    @NotBlank(message = "客户ID不能为空")
    @Size(max = 64, message = "客户ID长度不能超过64")
    @Schema(description = "客户ID", example = "CUST001")
    private String customerId;

    @Size(max = 64, message = "客户名称长度不能超过64")
    @Schema(description = "客户名称（用于户名生成规则）", example = "张三")
    private String customerName;

    @NotNull(message = "客户类型不能为空")
    @Schema(description = "客户类型：1-个人，2-企业，99-其他", example = "1")
    private Integer customerType;

    @Size(max = 64, message = "模板名称长度不能超过64")
    @Schema(description = "指定开户模板名称（可选，不填则默认匹配该业务线+客户类型的启用模板）", example = "芒好贷自营24贷款账户")
    private String templateName;

    @Schema(description = "指定开户模板ID（可选）", example = "1001")
    private Long templateId;

    @NotBlank(message = "开户批次请求号不能为空")
    @Size(max = 64, message = "开户批次请求号长度不能超过64")
    @Schema(description = "开户批次请求号（唯一幂等标识）", example = "REQ20260301000001")
    private String requestNo;
}
