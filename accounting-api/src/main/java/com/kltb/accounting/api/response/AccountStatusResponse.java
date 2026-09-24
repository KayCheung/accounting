package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 账户状态查询响应 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "账户状态查询响应")
public class AccountStatusResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "账户名称")
    private String accountName;

    @Schema(description = "科目编码")
    private String subjectCode;

    @Schema(description = "账户状态：1=正常,2=冻结,3=注销")
    private Integer status;

    @Schema(description = "账户状态描述")
    private String statusDesc;

    @Schema(description = "风控状态：1=正常,2=止入,3=止出,4=止入止出")
    private Integer riskStatus;

    @Schema(description = "风控状态描述")
    private String riskStatusDesc;

    @Schema(description = "余额")
    private BigDecimal balance;

    @Schema(description = "开户日期")
    private LocalDate openDate;

    @Schema(description = "注销生效日（未注销时为null）")
    private LocalDate inactiveDate;
}
