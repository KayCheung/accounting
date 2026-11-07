// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyAuxiliaryRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 手工记账申请分录辅助核算分摊录入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工凭证辅助核算分摊项")
public class ManualVoucherApplyAuxiliaryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联分录行号(从1开始)", example = "1")
    private Integer entryRowNum;

    @Schema(description = "会计科目编码", example = "660201")
    private String subjectCode;

    @NotBlank(message = "辅助核算类别不能为空")
    @Schema(description = "辅助核算类别代码(DEPT/PROJECT/CUSTOMER/SUPPLIER)", example = "DEPT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String auxType;

    @Schema(description = "辅助核算类别名称", example = "部门")
    private String auxTypeName;

    @NotBlank(message = "辅助核算项目编码不能为空")
    @Schema(description = "辅助核算项目编码", example = "DEPT001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String auxCode;

    @NotBlank(message = "辅助核算项目名称不能为空")
    @Schema(description = "辅助核算项目名称", example = "产品运营部", requiredMode = Schema.RequiredMode.REQUIRED)
    private String auxName;

    @Schema(description = "增减方向：1-增, 2-减", example = "1")
    @Builder.Default
    private Integer changeDirection = 1;

    @NotNull(message = "核算金额不能为空")
    @DecimalMin(value = "0.000001", message = "核算金额必须大于0")
    @Schema(description = "核算金额", example = "15.36", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
