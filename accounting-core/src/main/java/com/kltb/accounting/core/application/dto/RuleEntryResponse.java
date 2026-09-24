package com.kltb.accounting.core.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 规则明细响应 DTO
 */
@Getter
@Setter
@Schema(description = "规则明细响应")
public class RuleEntryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "凭证分录行号")
    private Integer rowNum;

    @Schema(description = "交易款项类型")
    private String fundsType;

    @Schema(description = "会计科目编码")
    private String subjectCode;

    @Schema(description = "账户作用域：1-内部，2-外部")
    private Integer accountScope;

    @Schema(description = "借贷方向：1-借，2-贷")
    private Integer debitCredit;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "是否单边更新")
    private Boolean isUnilateral;

    @Schema(description = "SpEL扩展脚本")
    private String extendScript;

    @Schema(description = "摘要")
    private String summary;

    @Schema(description = "辅助核算项列表")
    private List<RuleAuxiliaryResponse> auxiliaries;
}
