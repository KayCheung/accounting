package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭证生成请求 DTO
 */
@Data
public class VoucherGenerateRequest {

    @NotBlank(message = "traceNo 不能为空")
    @Size(max = 64, message = "traceNo 长度不能超过 64")
    private String traceNo;

    @Size(max = 32, message = "bookkeeperName 长度不能超过 32")
    private String bookkeeperName = "SYSTEM";
}
