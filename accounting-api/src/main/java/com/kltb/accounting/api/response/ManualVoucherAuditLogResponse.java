// accounting-api/src/main/java/com/kltb/accounting/api/response/ManualVoucherAuditLogResponse.java
package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 手工记账流转可追溯审计日志响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账审批流转审计日志响应")
public class ManualVoucherAuditLogResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    private Long id;

    @Schema(description = "申请单号", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "审批动作：SUBMIT-提交初审, AUDIT_PASS-初审通过, AUDIT_REJECT-初审驳回, REVIEW_PASS-复核通过, REVIEW_REJECT-复核驳回, BOOKKEEPING-确认记账, DRAFT-保存草稿, CANCEL-作废", example = "AUDIT_PASS")
    private String action;

    @Schema(description = "操作动作中文描述", example = "初审通过")
    private String actionDesc;

    @Schema(description = "操作人姓名", example = "李主管")
    private String operatorName;

    @Schema(description = "操作角色：MAKER-制单人, AUDITOR-初审人, REVIEWER-复核人, BOOKKEEPER-记账人", example = "AUDITOR")
    private String operatorRole;

    @Schema(description = "流转前状态码")
    private Integer preStatus;

    @Schema(description = "流转前状态描述", example = "待初审")
    private String preStatusDesc;

    @Schema(description = "流转后状态码")
    private Integer postStatus;

    @Schema(description = "流转后状态描述", example = "待复核")
    private String postStatusDesc;

    @Schema(description = "审批处理意见或驳回原因", example = "分录科目与凭据核实无误，初审通过")
    private String opinion;

    @Schema(description = "操作时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operateTime;
}
