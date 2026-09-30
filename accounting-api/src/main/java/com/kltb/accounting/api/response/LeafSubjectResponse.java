package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 可记账末级科目选项响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "可记账末级科目选项响应")
public class LeafSubjectResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "科目编码", example = "1002.01")
    private String subjectCode;

    @Schema(description = "科目名称", example = "银行存款-工行基本户")
    private String subjectName;

    @Schema(description = "科目级次", example = "2")
    private Integer subjectLevel;

    @Schema(description = "余额方向：1-借方(Debit), 2-贷方(Credit)", example = "1")
    private Integer balanceDirection;
}
