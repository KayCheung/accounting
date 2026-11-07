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
 * 注销账户请求 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "注销账户请求")
public class AccountCancelRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @Size(max = 128, message = "注销原因长度不能超过128")
    @Schema(description = "注销原因", example = "客户申请销户")
    private String reason;
}
