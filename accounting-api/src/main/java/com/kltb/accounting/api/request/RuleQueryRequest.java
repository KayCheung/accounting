package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * 分页查询记账规则请求
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "分页查询记账规则请求")
public class RuleQueryRequest extends PageRequest {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "业务线编码", example = "PAYMENT")
    private String businessCode;

    @Schema(description = "交易编码", example = "CASH_PAY")
    private String tradingCode;

    @Schema(description = "状态：1-待启用，2-启用，3-停用", example = "1")
    private Integer status;
}
