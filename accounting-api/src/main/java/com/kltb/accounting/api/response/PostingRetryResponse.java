package com.kltb.accounting.api.response;

import lombok.Data;

/**
 * 凭证重试结果响应 DTO
 */
@Data
public class PostingRetryResponse {

    private String voucherNo;
    private Integer voucherStatus;
    private String voucherStatusDesc;
    private boolean success;
}
