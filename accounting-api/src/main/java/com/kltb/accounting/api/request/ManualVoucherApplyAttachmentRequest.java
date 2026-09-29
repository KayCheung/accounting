// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyAttachmentRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 手工记账申请附件录入请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请附件项")
public class ManualVoucherApplyAttachmentRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "附件文件名不能为空")
    @Schema(description = "附件文件名", example = "京东代扣证明.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
    private String fileName;

    @NotBlank(message = "附件存储路径或URL不能为空")
    @Schema(description = "附件路径或URL", example = "/attachments/2026/09/mva_proof_001.pdf", requiredMode = Schema.RequiredMode.REQUIRED)
    private String filePath;

    @Schema(description = "附件文件大小(字节)", example = "1048576")
    @Builder.Default
    private Long fileSize = 0L;
}
