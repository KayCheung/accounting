package com.kltb.accounting.core.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;

/**
 * 开户模板响应 DTO
 */
@Getter
@Setter
@Schema(description = "开户模板响应")
public class TemplateResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "业务线编码")
    private String businessCode;

    @Schema(description = "客户类型：1-个人，2-企业，99-其他")
    private Integer customerType;

    @Schema(description = "是否支持自动开户")
    private Boolean autoOpen;

    @Schema(description = "状态：1-待启用，2-启用，3-停用")
    private Integer status;

    @Schema(description = "会计科目编码")
    private String subjectCode;

    @Schema(description = "账户类型")
    private String accountType;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "余额方向：1-借，2-贷")
    private Integer balanceDirection;

    @Schema(description = "账户编号生成规则")
    private String acctNoRule;

    @Schema(description = "账户名称生成规则")
    private String acctNameRule;
}
