package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 过账执行请求 DTO
 */
@Data
public class PostingExecuteRequest {

    @NotBlank(message = "凭证号不能为空")
    @Size(max = 32, message = "凭证号长度不能超过32")
    private String voucherNo;

    @Size(max = 32, message = "操作人姓名长度不能超过32")
    private String operatorName = "SYSTEM";
}
