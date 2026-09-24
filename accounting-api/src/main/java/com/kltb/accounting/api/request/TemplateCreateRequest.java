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
 * 创建开户模板请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "创建开户模板请求")
public class TemplateCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "模板名称不能为空")
    @Size(max = 32, message = "模板名称长度不能超过32")
    @Schema(description = "模板名称", example = "现金账户模板")
    private String templateName;

    @NotBlank(message = "业务线编码不能为空")
    @Size(max = 32, message = "业务线编码长度不能超过32")
    @Schema(description = "业务线编码", example = "PAYMENT")
    private String businessCode;

    @NotNull(message = "客户类型不能为空")
    @Schema(description = "客户类型：1-个人，2-企业，99-其他", example = "1")
    private Integer customerType;

    @NotNull(message = "是否自动开户不能为空")
    @Schema(description = "是否支持自动开户", example = "true")
    private Boolean autoOpen;

    @NotBlank(message = "科目编码不能为空")
    @Size(max = 32, message = "科目编码长度不能超过32")
    @Schema(description = "会计科目编码", example = "101001")
    private String subjectCode;

    @NotBlank(message = "账户类型不能为空")
    @Size(max = 32, message = "账户类型长度不能超过32")
    @Schema(description = "账户类型", example = "CASH")
    private String accountType;

    @Size(max = 32, message = "币种长度不能超过32")
    @Schema(description = "币种", example = "CNY")
    private String currency;

    @NotNull(message = "余额方向不能为空")
    @Schema(description = "余额方向：1-借，2-贷", example = "1")
    private Integer balanceDirection;

    @NotBlank(message = "账户编号生成规则不能为空")
    @Size(max = 32, message = "账户编号生成规则长度不能超过32")
    @Schema(description = "账户编号生成规则", example = "CASH-{yyyyMMdd}-{seq}")
    private String acctNoRule;

    @NotBlank(message = "账户名称生成规则不能为空")
    @Size(max = 32, message = "账户名称生成规则长度不能超过32")
    @Schema(description = "账户名称生成规则", example = "{subjectName}-{ownerName}")
    private String acctNameRule;

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "1")
    private Integer status;
}
