// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyAuditRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 手工记账申请审核请求（初审/复核通用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请审核请求")
public class ManualVoucherApplyAuditRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "申请单号不能为空")
    @Schema(description = "申请单号", example = "MVA202609290001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String applyNo;

    @NotBlank(message = "审核操作不能为空")
    @Schema(description = "审核操作：PASS-通过, REJECT-驳回", example = "PASS", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @NotBlank(message = "操作人姓名不能为空")
    @Schema(description = "操作人姓名", example = "李主管", requiredMode = Schema.RequiredMode.REQUIRED)
    private String operatorName;

    @Schema(description = "操作角色：AUDITOR-初审人, REVIEWER-复核人", example = "AUDITOR")
    private String operatorRole;

    @Schema(description = "审批意见/驳回原因(驳回时必填)", example = "核对无误，同意调账")
    private String opinion;
}
