package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 分录行响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "凭证分录行响应 DTO")
public class VoucherEntryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "分录流水号", example = "ENT202609200000010001")
    private String entryId;

    @Schema(description = "行号", example = "1")
    private Integer rowNum;

    @Schema(description = "会计科目编码", example = "1001")
    private String subjectCode;

    @Schema(description = "会计科目名称", example = "库存现金")
    private String subjectName;

    @Schema(description = "账户编号", example = "0012026030100001")
    private String accountNo;

    @Schema(description = "借贷方向：1-借(Debit), 2-贷(Credit)", example = "1")
    private Integer debitCredit;

    @Schema(description = "分录金额", example = "1000.00")
    private BigDecimal amount;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "摘要", example = "放款本金入账")
    private String summary;

    @Schema(description = "是否单边入账", example = "false")
    private Boolean isUnilateral;

    @Schema(description = "是否缓冲入账", example = "false")
    private Boolean isBuffered;

    @Schema(description = "会计日期", example = "2026-09-20")
    private LocalDate accountingDate;

    /**
     * 保持 11 参数历史构造方法兼容
     */
    public VoucherEntryResponse(String entryId, Integer rowNum, String subjectCode, String accountNo,
                                Integer debitCredit, BigDecimal amount, String currency, String summary,
                                Boolean isUnilateral, Boolean isBuffered, LocalDate accountingDate) {
        this.entryId = entryId;
        this.rowNum = rowNum;
        this.subjectCode = subjectCode;
        this.subjectName = null;
        this.accountNo = accountNo;
        this.debitCredit = debitCredit;
        this.amount = amount;
        this.currency = currency;
        this.summary = summary;
        this.isUnilateral = isUnilateral;
        this.isBuffered = isBuffered;
        this.accountingDate = accountingDate;
    }
}
