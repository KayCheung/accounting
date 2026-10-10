package com.kltb.accounting.api.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 辅助核算账簿统计 KPI 概览响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "辅助核算统计 KPI 概览响应")
public class AuxiliarySummaryResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "本期发生总金额", example = "880600.00")
    private BigDecimal totalAmount;

    @Schema(description = "借方发生额合计", example = "880600.00")
    private BigDecimal debitAmount;

    @Schema(description = "贷方发生额合计", example = "0.00")
    private BigDecimal creditAmount;

    @Schema(description = "涉及核算项目数", example = "6")
    private Integer itemCount;

    @Schema(description = "涉及会计科目数", example = "12")
    private Integer subjectCount;

    @Schema(description = "涉及凭证笔数", example = "64")
    private Integer voucherCount;

    @Schema(description = "占比排名前列的核算项分布")
    private List<AuxiliaryItemShareResponse> topItems;
}
