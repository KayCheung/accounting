package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 风控状态变更请求 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "风控状态变更请求")
public class AccountRiskStatusRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "账户编号不能为空")
    @Size(max = 32, message = "账户编号长度不能超过32")
    @Schema(description = "账户编号", example = "00120260301000001")
    private String accountNo;

    @NotNull(message = "风控状态不能为空")
    @Min(value = 1, message = "风控状态最小值为1")
    @Max(value = 4, message = "风控状态最大值为4")
    @Schema(description = "风控状态：1=正常,2=止入,3=止出,4=止入止出", example = "2")
    private Integer riskStatus;
}
