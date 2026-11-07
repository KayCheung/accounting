package com.kltb.accounting.api.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金冻结请求 DTO
 */
@Data
public class FundFreezeRequest {

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    private String accountNo;

    @NotNull(message = "冻结金额不能为空")
    @DecimalMin(value = "0.000001", message = "冻结金额必须大于0")
    private BigDecimal freezeAmount;

    @Size(max = 32, message = "业务线编码长度不能超过32")
    private String businessCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    @Size(max = 128, message = "冻结原因长度不能超过128")
    private String reason;
}
