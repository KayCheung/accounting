package com.kltb.accounting.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 冻结扣款请求 DTO
 */
@Data
public class FreezeDeductRequest {

    @NotBlank(message = "冻结编号不能为空")
    @Size(max = 32, message = "冻结编号长度不能超过32")
    private String freezeId;

    @NotNull(message = "扣款金额不能为空")
    @DecimalMin(value = "0.000001", message = "扣款金额必须大于0")
    private BigDecimal deductAmount;

    @Size(max = 128, message = "扣款原因长度不能超过128")
    private String reason;
}
