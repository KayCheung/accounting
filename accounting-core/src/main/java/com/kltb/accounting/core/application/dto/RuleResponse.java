package com.kltb.accounting.core.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 记账规则响应 DTO
 */
@Getter
@Setter
@Schema(description = "记账规则响应")
public class RuleResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "规则名称")
    private String ruleName;

    @Schema(description = "凭证类型")
    private String voucherType;

    @Schema(description = "业务线编码")
    private String businessCode;

    @Schema(description = "交易编码")
    private String tradingCode;

    @Schema(description = "支付渠道")
    private String payChannel;

    @Schema(description = "是否允许自动开户")
    private Boolean isOpenAccount;

    @Schema(description = "冻结时长（秒）")
    private Integer freezeDuration;

    @Schema(description = "前置入账规则ID")
    private Long preRuleId;

    @Schema(description = "状态：1-待启用，2-启用，3-停用")
    private Integer status;

    @Schema(description = "规则明细列表")
    private List<RuleEntryResponse> entries;
}
