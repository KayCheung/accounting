package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 聚合余额查询请求
 */
@Data
@Schema(description = "聚合余额查询请求")
public class BalanceAggregateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;
}
