package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "结转试算分录项")
public class TransferPreviewEntryItemResponse {

    @Schema(description = "行号")
    private Integer rowNum;

    @Schema(description = "科目代码")
    private String subjectCode;

    @Schema(description = "科目名称")
    private String subjectName;

    @Schema(description = "分户账户号（若涉及）")
    private String accountNo;

    @Schema(description = "借贷方向：1-借, 2-贷")
    private Integer debitCredit;

    @Schema(description = "借贷方向描述")
    private String debitCreditDesc;

    @Schema(description = "借方金额")
    private BigDecimal debitAmount;

    @Schema(description = "贷方金额")
    private BigDecimal creditAmount;

    @Schema(description = "币种")
    private String currency;

    @Schema(description = "摘要")
    private String summary;
}
