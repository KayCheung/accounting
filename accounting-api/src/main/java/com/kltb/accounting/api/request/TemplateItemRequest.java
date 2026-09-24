package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

/**
 * 开户模板明细项请求（单科目账户配置）
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "开户模板明细项请求")
public class TemplateItemRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "模板记录ID（编辑已有明细时传入）")
    private Long id;

    @NotBlank(message = "会计科目编码不能为空")
    @Size(max = 32, message = "会计科目编码长度不能超过32")
    @Schema(description = "会计科目编码", example = "101001")
    private String subjectCode;

    @NotBlank(message = "账户类型不能为空")
    @Size(max = 32, message = "账户类型长度不能超过32")
    @Schema(description = "账户类型", example = "CASH")
    private String accountType;

    @Size(max = 32, message = "币种长度不能超过32")
    @Schema(description = "币种", example = "CNY")
    private String currency;

    @NotNull(message = "余额方向不能为空")
    @Schema(description = "余额方向：1-借，2-贷", example = "1")
    private Integer balanceDirection;

    @NotBlank(message = "账户编号生成规则不能为空")
    @Size(max = 32, message = "账户编号生成规则长度不能超过32")
    @Schema(description = "账户编号生成规则", example = "CASH-{yyyyMMdd}-{seq}")
    private String acctNoRule;

    @NotBlank(message = "账户名称生成规则不能为空")
    @Size(max = 32, message = "账户名称生成规则长度不能超过32")
    @Schema(description = "账户名称生成规则", example = "{subjectName}-{ownerName}")
    private String acctNameRule;
}
