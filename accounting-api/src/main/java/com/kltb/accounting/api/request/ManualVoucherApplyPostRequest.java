// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyPostRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 手工记账确认记账请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账确认记账请求")
public class ManualVoucherApplyPostRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "申请单号不能为空")
    @Schema(description = "申请单号", example = "MVA202609290001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String applyNo;

    @NotBlank(message = "记账人姓名不能为空")
    @Schema(description = "记账人姓名", example = "王出纳", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bookkeeperName;

    @Schema(description = "记账备注说明", example = "确认入账并完成账户余额扣增")
    private String remark;
}
