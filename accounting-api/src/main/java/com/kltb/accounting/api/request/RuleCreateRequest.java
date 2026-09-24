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
 * 创建记账规则请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "创建记账规则请求")
public class RuleCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 32, message = "规则名称长度不能超过32")
    @Schema(description = "规则名称", example = "付款-现金规则")
    private String ruleName;

    @NotBlank(message = "凭证类型不能为空")
    @Size(max = 32, message = "凭证类型长度不能超过32")
    @Schema(description = "凭证类型", example = "PAYMENT")
    private String voucherType;

    @NotBlank(message = "业务线编码不能为空")
    @Size(max = 32, message = "业务线编码长度不能超过32")
    @Schema(description = "业务线编码", example = "PAYMENT")
    private String businessCode;

    @NotBlank(message = "交易编码不能为空")
    @Size(max = 32, message = "交易编码长度不能超过32")
    @Schema(description = "交易编码", example = "CASH_PAY")
    private String tradingCode;

    @NotBlank(message = "支付渠道不能为空")
    @Size(max = 32, message = "支付渠道长度不能超过32")
    @Schema(description = "支付渠道", example = "CASH")
    private String payChannel;

    @Schema(description = "是否允许自动开户", example = "false")
    private Boolean isOpenAccount;

    @Schema(description = "冻结时长（秒）", example = "0")
    private Integer freezeDuration;

    @Schema(description = "前置入账规则ID", example = "0")
    private Long preRuleId;

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "1")
    private Integer status;

    @NotEmpty(message = "规则明細不能为空")
    @Valid
    @Schema(description = "规则明细列表")
    private List<RuleEntryRequest> entries;
}
