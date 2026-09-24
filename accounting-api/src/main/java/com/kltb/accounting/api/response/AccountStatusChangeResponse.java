package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 账户状态变更结果响应 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "账户状态变更结果响应")
public class AccountStatusChangeResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "变更前状态")
    private Integer previousStatus;

    @Schema(description = "变更后状态")
    private Integer currentStatus;

    @Schema(description = "变更后状态描述")
    private String statusDesc;

    @Schema(description = "变更后风控状态")
    private Integer riskStatus;

    @Schema(description = "变更后风控状态描述")
    private String riskStatusDesc;
}
