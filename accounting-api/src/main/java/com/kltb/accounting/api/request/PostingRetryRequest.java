package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 凭证重试请求 DTO
 */
@Data
public class PostingRetryRequest {

    @NotBlank(message = "凭证号不能为空")
    @Size(max = 64, message = "凭证号长度不能超过64")
    private String voucherNo;

    @NotBlank(message = "操作人不能为空")
    @Size(max = 32, message = "操作人姓名长度不能超过32")
    private String operatorName;

    @NotBlank(message = "重试原因不能为空")
    @Size(max = 200, message = "重试原因长度不能超过200")
    private String retryReason;
}
