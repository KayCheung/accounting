package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 更新开户模板请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "更新开户模板请求")
public class TemplateUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 32, message = "模板名称长度不能超过32")
    @Schema(description = "模板名称", example = "现金账户模板")
    private String templateName;

    @Size(max = 32, message = "账户类型长度不能超过32")
    @Schema(description = "账户类型", example = "CASH_NEW")
    private String accountType;

    @Size(max = 32, message = "币种长度不能超过32")
    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "余额方向：1-借，2-贷", example = "1")
    private Integer balanceDirection;

    @Size(max = 32, message = "账户编号生成规则长度不能超过32")
    @Schema(description = "账户编号生成规则", example = "CASH-{yyyyMMdd}-{seq}")
    private String acctNoRule;

    @Size(max = 32, message = "账户名称生成规则长度不能超过32")
    @Schema(description = "账户名称生成规则", example = "{subjectName}-{ownerName}")
    private String acctNameRule;

    @Schema(description = "是否支持自动开户", example = "true")
    private Boolean autoOpen;

    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "1")
    private Integer status;
}
