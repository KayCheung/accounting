package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 规则明细行请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "规则明细行")
public class RuleEntryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull(message = "行号不能为空")
    @Schema(description = "凭证分录行号", example = "1")
    private Integer rowNum;

    @NotBlank(message = "交易款项类型不能为空")
    @Size(max = 32, message = "交易款项类型长度不能超过32")
    @Schema(description = "交易款项类型", example = "PRINCIPAL")
    private String fundsType;

    @NotBlank(message = "科目编码不能为空")
    @Size(max = 32, message = "科目编码长度不能超过32")
    @Schema(description = "会计科目编码", example = "101001")
    private String subjectCode;

    @NotNull(message = "账户作用域不能为空")
    @Schema(description = "账户作用域：1-内部，2-外部", example = "1")
    private Integer accountScope;

    @NotNull(message = "借贷方向不能为空")
    @Schema(description = "借贷方向：1-借，2-贷", example = "1")
    private Integer debitCredit;

    @Size(max = 32, message = "币种长度不能超过32")
    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "是否单边更新", example = "false")
    private Boolean isUnilateral;

    @Size(max = 2000, message = "扩展脚本长度不能超过2000")
    @Schema(description = "SpEL扩展脚本")
    private String extendScript;

    @Size(max = 64, message = "摘要长度不能超过64")
    @Schema(description = "摘要", example = "现金付款")
    private String summary;

    @Valid
    @Schema(description = "辅助核算项列表")
    private List<RuleAuxiliaryRequest> auxiliaries;
}
