package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 凭证附件响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "凭证附件响应 DTO")
public class VoucherAttachmentResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "附件ID", example = "101")
    private Long id;

    @Schema(description = "凭证号", example = "VOU20260920000001")
    private String voucherNo;

    @Schema(description = "附件文件地址", example = "/upload/voucher/20260920/receipt_01.pdf")
    private String filePath;

    @Schema(description = "上传时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
