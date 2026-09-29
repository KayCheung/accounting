// accounting-api/src/main/java/com/kltb/accounting/api/request/ManualVoucherApplyEntryRequest.java
package com.kltb.accounting.api.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 手工记账申请分录行请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手工记账申请分录明细请求")
public class ManualVoucherApplyEntryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分录行号", example = "1")
    private Integer rowNum;

    @NotNull(message = "借贷方向不能为空")
    @Schema(description = "借贷方向：1-借, 2-贷", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer debitCredit;

    @NotBlank(message = "会计科目编码不能为空")
    @Schema(description = "会计科目编码", example = "100201", requiredMode = Schema.RequiredMode.REQUIRED)
    private String subjectCode;

    @Schema(description = "分户账户编号", example = "ACC202609010001")
    private String accountNo;

    @NotNull(message = "分录金额不能为空")
    @DecimalMin(value = "0.01", message = "分录金额必须大于0")
    @Schema(description = "交易金额(绝对值法则，正数)", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "摘要", example = "期末调账")
    private String summary;

    @Schema(description = "资金单边处理(是否实时更新账户余额)：1-是(实时), 0-否", example = "1")
    private Integer unilateral;
}
