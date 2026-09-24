package com.kltb.accounting.api.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 开户结果响应 DTO
 */
@Getter
@Setter
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountOpenResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户编号")
    private String accountNo;

    @Schema(description = "账户名称")
    private String accountName;

    @Schema(description = "科目编码")
    private String subjectCode;

    @Schema(description = "所有者ID")
    private String ownerId;

    @Schema(description = "账户状态")
    private Integer status;

    @Schema(description = "开户日期")
    private LocalDate openDate;
}
