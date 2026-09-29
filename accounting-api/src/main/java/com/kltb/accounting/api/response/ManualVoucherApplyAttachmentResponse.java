// accounting-api/src/main/java/com/kltb/accounting/api/response/ManualVoucherApplyAttachmentResponse.java
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
 * 手工记账申请附件响应 DTO
 * 对应《凭证附件.html》行 68-165
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工凭证附件响应")
public class ManualVoucherApplyAttachmentResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "主键ID", example = "1")
    private Long id;

    @Schema(description = "申请单号", example = "MVA202609290001")
    private String applyNo;

    @Schema(description = "文件名称", example = "京东代扣证明.pdf")
    private String fileName;

    @Schema(description = "文件路径/URL", example = "/attachments/2026/09/mva_proof_001.pdf")
    private String filePath;

    @Schema(description = "文件大小(字节)", example = "1048576")
    private Long fileSize;

    @Schema(description = "文件类型", example = "PDF")
    private String fileType;

    @Schema(description = "文件大小格式化显示", example = "1MB")
    private String fileSizeFormatted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "上传时间")
    private LocalDateTime createTime;
}
