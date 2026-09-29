// accounting-api/src/main/java/com/kltb/accounting/api/response/ManualVoucherApplyAuxiliaryResponse.java
package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 手工记账申请辅助核算分摊响应 DTO
 * 对应《凭证详情.html》行 456-585 辅助核算项表格
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工凭证辅助核算项响应")
public class ManualVoucherApplyAuxiliaryResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "申请单号", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "关联分录行号", example = "1")
    private Integer entryRowNum;

    @Schema(description = "会计科目编码", example = "660201")
    private String subjectCode;

    @Schema(description = "会计科目名称", example = "管理费用-办公费")
    private String subjectName;

    @Schema(description = "辅助核算类别代码", example = "DEPT")
    private String auxType;

    @Schema(description = "辅助核算类别名称", example = "部门")
    private String auxTypeName;

    @Schema(description = "辅助核算项目编码", example = "DEPT001")
    private String auxCode;

    @Schema(description = "辅助核算项目名称", example = "产品运营部")
    private String auxName;

    @Schema(description = "增减方向：1-增, 2-减", example = "1")
    private Integer changeDirection;

    @Schema(description = "增减方向描述", example = "增加")
    private String changeDirectionDesc;

    @Schema(description = "核算金额", example = "15.36")
    private BigDecimal amount;
}
