package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 更新记账规则请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "更新记账规则请求")
public class RuleUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 32, message = "规则名称长度不能超过32")
    @Schema(description = "规则名称", example = "付款-现金规则（新）")
    private String ruleName;

    @Size(max = 32, message = "凭证类型长度不能超过32")
    @Schema(description = "凭证类型", example = "PAYMENT")
    private String voucherType;

    @Schema(description = "是否允许自动开户", example = "false")
    private Boolean isOpenAccount;

    @Schema(description = "冻结时长（秒）", example = "0")
    private Integer freezeDuration;

    @Schema(description = "前置入账规则ID", example = "0")
    private Long preRuleId;

    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "1")
    private Integer status;

    @Valid
    @Schema(description = "规则明细列表（全量替换）")
    private List<RuleEntryRequest> entries;
}
