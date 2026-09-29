// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplySaveRequest.java
package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 手工记账申请制单/编辑保存请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请制单/编辑保存请求")
public class ManualVoucherApplySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "申请单号(编辑修改重新提交时必填，首次创建为空)", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "凭证类型", example = "记账凭证")
    @Builder.Default
    private String voucherType = "记账凭证";

    @Schema(description = "交易类别：1-正常, 2-调账", example = "2")
    @Builder.Default
    private Integer tradeType = 2;

    @NotNull(message = "会计日期不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "会计日期", example = "2026-09-29", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate accountingDate;

    @NotBlank(message = "凭证摘要不能为空")
    @Schema(description = "凭证摘要", example = "9月末手工调账分录", requiredMode = Schema.RequiredMode.REQUIRED)
    private String summary;

    @NotBlank(message = "制单人姓名不能为空")
    @Schema(description = "制单人姓名", example = "张会计", requiredMode = Schema.RequiredMode.REQUIRED)
    private String makerName;

    @Schema(description = "操作类型：DRAFT-保存草稿, SUBMIT-提交初审", example = "SUBMIT")
    @Builder.Default
    private String action = "SUBMIT";

    @NotEmpty(message = "分录明细不能为空")
    @Size(min = 2, message = "记账凭证至少需要2条借贷分录")
    @Valid
    @Schema(description = "分录明细列表(至少包含一借一贷)", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ManualVoucherApplyEntryRequest> entries;

    @Valid
    @Schema(description = "辅助核算分摊明细列表")
    private List<ManualVoucherApplyAuxiliaryRequest> auxiliaries;

    @Valid
    @Schema(description = "凭证原始单据附件列表")
    private List<ManualVoucherApplyAttachmentRequest> attachments;
}
