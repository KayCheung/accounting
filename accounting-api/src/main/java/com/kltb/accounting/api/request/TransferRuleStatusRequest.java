package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "期末结转规则状态切换请求")
public class TransferRuleStatusRequest {

    @NotNull(message = "状态不能为空")
    @Schema(description = "状态：1-启用, 2-停用", example = "1")
    private Integer status;
}
