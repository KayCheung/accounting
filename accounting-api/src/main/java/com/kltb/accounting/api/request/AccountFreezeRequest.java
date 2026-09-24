package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 冻结账户请求 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "冻结账户请求")
public class AccountFreezeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @Size(max = 128, message = "冻结原因长度不能超过128")
    @Schema(description = "冻结原因", example = "客户异常交易风控")
    private String reason;
}
