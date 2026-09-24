package com.kltb.accounting.api.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 红冲请求 DTO
 */
@Data
public class ReversalRequest {

    /**
     * 原凭证号（必填）
     */
    @NotBlank(message = "原凭证号不能为空")
    private String origVoucherNo;

    /**
     * 红冲摘要（选填，默认"红冲凭证:{origVoucherNo}"）
     */
    private String summary;

    /**
     * 记账人姓名（选填，从登录上下文获取）
     */
    private String bookkeeperName;
}
